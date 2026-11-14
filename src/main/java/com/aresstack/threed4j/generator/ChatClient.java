package com.aresstack.threed4j.generator;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Minimal client for an OpenAI-compatible chat API: one question, one answer
 * ({@code POST <baseUrl>/chat/completions}). Safe to use from several threads.
 *
 * <p>Connections are made the way the Enterprise AI Client makes them: with
 * {@link HttpURLConnection} through the route of {@link ProxyRoutes} (directly or
 * through the proxy of the company network), so that Windows logs in at the proxy
 * by itself where the proxy asks for it.</p>
 */
final class ChatClient {

    private static final SSLSocketFactory TRUST = windowsTrust();

    private final ProxyRoutes routes = ProxyRoutes.shared();
    private final URI endpoint;
    private final URI modelsEndpoint;
    private final String model;
    private final String apiKey;
    private final int maxTokens = AiSetup.maxTokens();
    private final String reasoningEffort = AiSetup.reasoningEffort();
    /** Whether the server is Ollama, spoken to in its own API, instead of an OpenAI-compatible service. */
    private final boolean ollama;

    ChatClient(URI baseUrl, String model, String apiKey) {
        this.endpoint = URI.create(baseUrl + "/chat/completions");
        this.modelsEndpoint = URI.create(baseUrl + "/models");
        this.model = model;
        this.apiKey = apiKey;
        this.ollama = false;
    }

    /** The address Ollama listens at on the machine it runs on. */
    static final String OLLAMA_DEFAULT = "http://localhost:11434";

    private ChatClient(String ollamaUrl, String model) {
        // As the Ollama client of AskAI normalizes it: empty means the local default, no trailing slash.
        String url = ollamaUrl == null ? "" : ollamaUrl.trim();
        if (url.isEmpty()) {
            url = OLLAMA_DEFAULT;
        }
        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        this.endpoint = URI.create(url + "/api/chat");
        this.modelsEndpoint = URI.create(url + "/api/tags");
        this.model = model;
        this.apiKey = null;
        this.ollama = true;
    }

    /**
     * A client for an Ollama server, spoken to in Ollama's own API, the one AskAI
     * uses through ollama4j: {@code GET /api/tags} lists the installed models,
     * {@code POST /api/chat} asks one. No API key.
     *
     * @param baseUrl the server's address, e.g. {@code http://localhost:11434}; empty for that default
     */
    static ChatClient ollama(String baseUrl, String model) {
        return new ChatClient(baseUrl, model);
    }

    /** The model this client asks. */
    String model() {
        return model;
    }

    /**
     * On Windows, trust what Java trusts and what Windows trusts. An in-house service
     * usually has a certificate from an in-house authority that Java's own list does
     * not know; a company proxy that inspects traffic signs with such an authority too.
     *
     * @return {@code null} to keep Java's own list
     */
    private static SSLSocketFactory windowsTrust() {
        if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows")) {
            return null;
        }
        try {
            List<X509TrustManager> managers = new ArrayList<>();
            managers.add(trustManager(null)); // Java's own list
            try {
                KeyStore windows = KeyStore.getInstance("Windows-ROOT");
                windows.load(null, null);
                managers.add(trustManager(windows));
            } catch (GeneralSecurityException | IOException e) {
                // the Windows list is not available; Java's own still counts
            }
            SSLContext context = SSLContext.getInstance("TLS");
            context.init(null, new TrustManager[] {new AnyOf(managers)}, null);
            return context.getSocketFactory();
        } catch (GeneralSecurityException e) {
            return null;
        }
    }

    private static X509TrustManager trustManager(KeyStore store) throws GeneralSecurityException {
        TrustManagerFactory factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        factory.init(store);
        for (TrustManager manager : factory.getTrustManagers()) {
            if (manager instanceof X509TrustManager x509) {
                return x509;
            }
        }
        throw new GeneralSecurityException("no X.509 trust manager");
    }

    /** Accepts a server that at least one of the lists accepts. */
    private record AnyOf(List<X509TrustManager> managers) implements X509TrustManager {
        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            CertificateException last = null;
            for (X509TrustManager manager : managers) {
                try {
                    manager.checkServerTrusted(chain, authType);
                    return;
                } catch (CertificateException e) {
                    last = e;
                }
            }
            throw last != null ? last : new CertificateException("no trust list");
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            throw new CertificateException("not a server");
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            List<X509Certificate> all = new ArrayList<>();
            for (X509TrustManager manager : managers) {
                all.addAll(List.of(manager.getAcceptedIssuers()));
            }
            return all.toArray(new X509Certificate[0]);
        }
    }

    /**
     * Sends one request and returns the body of a 2xx answer.
     *
     * @param body {@code null} for GET, otherwise the JSON to POST
     * @throws IOException with a message for the user if the service cannot be reached or refuses
     */
    private String exchange(URI address, String body, int readTimeoutMillis) throws IOException {
        Proxy proxy = routes.proxyFor(address);
        String way = proxy == Proxy.NO_PROXY ? "directly, proxy mode " + routes.describe() : "through proxy " + proxy.address();
        int status;
        String text;
        try {
            HttpURLConnection connection = (HttpURLConnection) address.toURL().openConnection(proxy);
            if (TRUST != null && connection instanceof HttpsURLConnection https) {
                https.setSSLSocketFactory(TRUST);
            }
            connection.setConnectTimeout(20_000);
            connection.setReadTimeout(readTimeoutMillis);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("User-Agent", "3d4j");
            if (apiKey != null && !apiKey.isBlank()) {
                connection.setRequestProperty("Authorization", "Bearer " + apiKey);
            }
            if (body != null) {
                connection.setRequestMethod("POST");
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json");
                try (OutputStream out = connection.getOutputStream()) {
                    out.write(body.getBytes(StandardCharsets.UTF_8));
                }
            }
            status = connection.getResponseCode();
            try (InputStream in = status / 100 == 2 ? connection.getInputStream() : connection.getErrorStream()) {
                text = in == null ? "" : new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            throw new IOException("cannot reach " + address + " " + way + " (" + e.getClass().getSimpleName() + ": "
                    + e.getMessage() + ")", e);
        }
        if (status / 100 != 2) {
            throw new IOException(address + " answered HTTP " + status + " (" + way + "): " + abbreviate(text));
        }
        return text;
    }

    /**
     * @return the text of the model's answer
     * @throws IOException with a message for the user if the service cannot be reached or refuses
     */
    String ask(String systemPrompt, String userPrompt) throws IOException {
        if (ollama) {
            return askOllama(systemPrompt, userPrompt);
        }
        JsonObject body = new JsonObject();
        body.addProperty("model", model);
        JsonArray messages = new JsonArray();
        messages.add(message("system", systemPrompt));
        messages.add(message("user", userPrompt));
        body.add("messages", messages);
        body.addProperty("stream", false);
        // Without a limit of its own the service may cut a long drawing short.
        body.addProperty("max_tokens", maxTokens);
        if (reasoningEffort != null) {
            // Only when chosen in the settings, as the Enterprise AI Client does; otherwise the model's default.
            body.addProperty("reasoning_effort", reasoningEffort);
        }

        String response = exchange(endpoint, body.toString(), 5 * 60_000);
        try {
            JsonObject json = JsonParser.parseString(response).getAsJsonObject();
            JsonElement content = json.getAsJsonArray("choices").get(0).getAsJsonObject()
                    .getAsJsonObject("message").get("content");
            if (content == null || content.isJsonNull()) {
                throw new IOException("the answer has no text: " + abbreviate(response));
            }
            JsonElement finish = json.getAsJsonArray("choices").get(0).getAsJsonObject().get("finish_reason");
            if (finish != null && !finish.isJsonNull() && finish.getAsString().equals("length")) {
                throw new IOException("the answer was cut off at max_tokens=" + maxTokens
                        + "; raise the answer length in the settings (chat.maxTokens)");
            }
            return content.getAsString();
        } catch (RuntimeException e) {
            throw new IOException("unexpected answer format: " + abbreviate(response), e);
        }
    }

    /**
     * @return the ids of the models the service offers ({@code GET <baseUrl>/models}), sorted
     * @throws IOException with a message for the user if the service cannot be reached or refuses
     */
    java.util.List<String> models() throws IOException {
        String response = exchange(modelsEndpoint, null, 60_000);
        try {
            java.util.List<String> ids = new java.util.ArrayList<>();
            // Ollama: {"models": [{"name": "gemma3:27b", ...}]}; OpenAI-compatible: {"data": [{"id": ...}]}.
            for (JsonElement model : JsonParser.parseString(response).getAsJsonObject()
                    .getAsJsonArray(ollama ? "models" : "data")) {
                ids.add(model.getAsJsonObject().get(ollama ? "name" : "id").getAsString());
            }
            java.util.Collections.sort(ids, String.CASE_INSENSITIVE_ORDER);
            return ids;
        } catch (RuntimeException e) {
            throw new IOException("unexpected answer format: " + abbreviate(response), e);
        }
    }

    /**
     * One question to Ollama's {@code /api/chat}, not streamed. The answer length goes
     * as {@code options.num_predict}; thinking, when chosen in the settings, as the
     * {@code think} level {@code low}, {@code medium} or {@code high}, the form AskAI
     * sends. A model may need minutes to load before its first answer, so the wait is long.
     */
    private String askOllama(String systemPrompt, String userPrompt) throws IOException {
        JsonObject body = new JsonObject();
        body.addProperty("model", model);
        JsonArray messages = new JsonArray();
        messages.add(message("system", systemPrompt));
        messages.add(message("user", userPrompt));
        body.add("messages", messages);
        body.addProperty("stream", false);
        JsonObject options = new JsonObject();
        options.addProperty("num_predict", maxTokens);
        body.add("options", options);
        if (reasoningEffort != null) {
            body.addProperty("think", reasoningEffort);
        }
        String response = exchange(endpoint, body.toString(), 30 * 60_000);
        try {
            JsonObject json = JsonParser.parseString(response).getAsJsonObject();
            if (json.has("error")) {
                throw new IOException("Ollama: " + abbreviate(json.get("error").toString()));
            }
            JsonElement content = json.getAsJsonObject("message").get("content");
            if (content == null || content.isJsonNull()) {
                throw new IOException("the answer has no text: " + abbreviate(response));
            }
            JsonElement done = json.get("done_reason");
            if (done != null && !done.isJsonNull() && done.getAsString().equals("length")) {
                throw new IOException("the answer was cut off at num_predict=" + maxTokens
                        + "; raise the answer length in the settings (chat.maxTokens)");
            }
            return content.getAsString();
        } catch (RuntimeException e) {
            throw new IOException("unexpected answer format: " + abbreviate(response), e);
        }
    }

    private static JsonObject message(String role, String content) {
        JsonObject message = new JsonObject();
        message.addProperty("role", role);
        message.addProperty("content", content);
        return message;
    }

    private static String abbreviate(String text) {
        String oneLine = text == null ? "" : text.replaceAll("\\s+", " ").trim();
        return oneLine.length() > 300 ? oneLine.substring(0, 300) + "..." : oneLine;
    }
}
