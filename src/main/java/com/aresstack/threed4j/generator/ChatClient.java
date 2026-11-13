package com.aresstack.threed4j.generator;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.time.Duration;
import java.util.Locale;

/**
 * Minimal client for an OpenAI-compatible chat API: one question, one answer
 * ({@code POST <baseUrl>/chat/completions}). Safe to use from several threads.
 */
final class ChatClient {

    private final HttpClient http;
    private final URI endpoint;
    private final URI modelsEndpoint;
    private final String model;
    private final String apiKey;

    ChatClient(URI baseUrl, String model, String apiKey) {
        this.endpoint = URI.create(baseUrl + "/chat/completions");
        this.modelsEndpoint = URI.create(baseUrl + "/models");
        this.model = model;
        this.apiKey = apiKey;
        HttpClient.Builder builder = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20));
        SSLContext windowsTrust = windowsTrustStore();
        if (windowsTrust != null) {
            builder.sslContext(windowsTrust);
        }
        this.http = builder.build();
    }

    /**
     * On Windows, trust the certificates Windows trusts. An in-house service usually
     * has a certificate from an in-house authority that Java's own list does not know.
     */
    private static SSLContext windowsTrustStore() {
        if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows")) {
            return null;
        }
        try {
            KeyStore roots = KeyStore.getInstance("Windows-ROOT");
            roots.load(null, null);
            TrustManagerFactory trust = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            trust.init(roots);
            SSLContext context = SSLContext.getInstance("TLS");
            context.init(null, trust.getTrustManagers(), null);
            return context;
        } catch (GeneralSecurityException | IOException e) {
            return null; // fall back to Java's own list
        }
    }

    /**
     * @return the text of the model's answer
     * @throws IOException with a message for the user if the service cannot be reached or refuses
     */
    String ask(String systemPrompt, String userPrompt) throws IOException, InterruptedException {
        JsonObject body = new JsonObject();
        body.addProperty("model", model);
        JsonArray messages = new JsonArray();
        messages.add(message("system", systemPrompt));
        messages.add(message("user", userPrompt));
        body.add("messages", messages);
        body.addProperty("stream", false);

        HttpRequest.Builder request = HttpRequest.newBuilder(endpoint)
                .timeout(Duration.ofMinutes(5))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8));
        if (apiKey != null && !apiKey.isBlank()) {
            request.header("Authorization", "Bearer " + apiKey);
        }

        HttpResponse<String> response;
        try {
            response = http.send(request.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IOException("cannot reach " + endpoint + " (" + e.getClass().getSimpleName() + ": "
                    + e.getMessage() + ")", e);
        }
        if (response.statusCode() / 100 != 2) {
            throw new IOException(endpoint + " answered HTTP " + response.statusCode() + ": "
                    + abbreviate(response.body()));
        }
        try {
            JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
            JsonElement content = json.getAsJsonArray("choices").get(0).getAsJsonObject()
                    .getAsJsonObject("message").get("content");
            if (content == null || content.isJsonNull()) {
                throw new IOException("the answer has no text: " + abbreviate(response.body()));
            }
            return content.getAsString();
        } catch (RuntimeException e) {
            throw new IOException("unexpected answer format: " + abbreviate(response.body()), e);
        }
    }

    /**
     * @return the ids of the models the service offers ({@code GET <baseUrl>/models}), sorted
     * @throws IOException with a message for the user if the service cannot be reached or refuses
     */
    java.util.List<String> models() throws IOException, InterruptedException {
        HttpRequest.Builder request = HttpRequest.newBuilder(modelsEndpoint).timeout(Duration.ofSeconds(60)).GET();
        if (apiKey != null && !apiKey.isBlank()) {
            request.header("Authorization", "Bearer " + apiKey);
        }
        HttpResponse<String> response;
        try {
            response = http.send(request.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IOException("cannot reach " + modelsEndpoint + " (" + e.getClass().getSimpleName() + ": "
                    + e.getMessage() + ")", e);
        }
        if (response.statusCode() / 100 != 2) {
            throw new IOException(modelsEndpoint + " answered HTTP " + response.statusCode() + ": "
                    + abbreviate(response.body()));
        }
        try {
            java.util.List<String> ids = new java.util.ArrayList<>();
            for (JsonElement model : JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonArray("data")) {
                ids.add(model.getAsJsonObject().get("id").getAsString());
            }
            java.util.Collections.sort(ids, String.CASE_INSENSITIVE_ORDER);
            return ids;
        } catch (RuntimeException e) {
            throw new IOException("unexpected answer format: " + abbreviate(response.body()), e);
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
