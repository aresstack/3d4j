package com.aresstack.threed4j.generator;

import com.aresstack.threed4j.assets.AssetCatalog;
import com.aresstack.threed4j.assets.AssetStore;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Runs the generator against a local stand-in for the OpenAI-compatible service. */
class AssetGeneratorTest {

    private static final String GOOD_SVG = """
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 256 512">
              <rect x="108" y="300" width="40" height="212" fill="#8A5A3C" stroke="#3B2A4A" stroke-width="8"/>
              <circle cx="128" cy="200" r="110" fill="#F7A8CF" stroke="#3B2A4A" stroke-width="8"/>
            </svg>""";

    @TempDir
    Path temp;
    private HttpServer server;
    private String previousHome;
    private final List<String> authorizations = new CopyOnWriteArrayList<>();
    private final List<String> userPrompts = new CopyOnWriteArrayList<>();
    private final AtomicInteger lampRequests = new AtomicInteger();

    @BeforeEach
    void startService() throws Exception {
        previousHome = System.getProperty("user.home");
        System.setProperty("user.home", temp.resolve("home").toString());

        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            authorizations.add(String.valueOf(exchange.getRequestHeaders().getFirst("Authorization")));
            JsonObject request = JsonParser.parseString(
                    new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
            String prompt = request.getAsJsonArray("messages").get(1).getAsJsonObject().get("content").getAsString();
            userPrompts.add(prompt);

            String content;
            if (prompt.contains("street lamp") && lampRequests.incrementAndGet() == 1) {
                content = "I am only a language model and cannot draw."; // first answer for the lamp is useless
            } else if (prompt.contains("yellow coat")) {
                // never transparent: a filled background
                content = "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 256 512\">"
                        + "<rect width=\"256\" height=\"512\" fill=\"#FFFFFF\"/></svg>";
            } else {
                content = "Here you go:\n```svg\n" + GOOD_SVG + "\n```";
            }
            JsonObject message = new JsonObject();
            message.addProperty("role", "assistant");
            message.addProperty("content", content);
            JsonObject choice = new JsonObject();
            choice.add("message", message);
            com.google.gson.JsonArray choices = new com.google.gson.JsonArray();
            choices.add(choice);
            JsonObject response = new JsonObject();
            response.add("choices", choices);
            byte[] body = response.toString().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stopService() {
        server.stop(0);
        System.setProperty("user.home", previousHome);
    }

    private String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/v1";
    }

    @Test
    void generatesAssetsRetriesAndReportsWhatStaysUnusable() throws Exception {
        AssetStore store = new AssetStore(temp.resolve("assets"));

        boolean allOk = AssetGenerator.run(new String[]{"--base-url=" + baseUrl(), "--model=test-model"},
                store, "secret-key");

        assertFalse(allOk, "the visitor with the filled background never becomes usable");
        assertTrue(Files.isRegularFile(store.pngFile(AssetCatalog.BLOSSOM_TREE)));
        assertTrue(Files.isRegularFile(store.svgFile(AssetCatalog.BLOSSOM_TREE)));
        assertTrue(Files.isRegularFile(store.pngFile(AssetCatalog.LAMP)), "second attempt for the lamp succeeds");
        assertEquals(2, lampRequests.get());
        assertFalse(store.has(AssetCatalog.VISITOR_3));
        assertTrue(Files.isRegularFile(store.directory().resolve("visitor-3.rejected.txt")));

        assertTrue(authorizations.stream().allMatch("Bearer secret-key"::equals));
        assertTrue(userPrompts.stream().anyMatch(p -> p.contains("contained no <svg> element")),
                "the model is told what was wrong");
        assertTrue(userPrompts.stream().anyMatch(p -> p.contains("background is not transparent")));
    }

    @Test
    void keepsExistingAssetsAndRemembersTheAddress() throws Exception {
        AssetStore store = new AssetStore(temp.resolve("assets"));
        AssetGenerator.run(new String[]{"--base-url=" + baseUrl(), "--only=blossom-tree"}, store, "k");
        int requestsAfterFirstRun = userPrompts.size();
        assertEquals(1, requestsAfterFirstRun);

        // No --base-url this time: it was remembered. The tree exists and is not asked for again.
        boolean allOk = AssetGenerator.run(new String[]{"--only=blossom-tree"}, store, "k");
        assertTrue(allOk);
        assertEquals(requestsAfterFirstRun, userPrompts.size());
    }

    @Test
    void unreachableServiceIsReportedPerAsset() throws Exception {
        AssetStore store = new AssetStore(temp.resolve("assets"));
        server.stop(0);
        boolean allOk = AssetGenerator.run(new String[]{"--base-url=" + baseUrl(), "--only=lamp"}, store, "k");
        assertFalse(allOk);
        assertFalse(store.has(AssetCatalog.LAMP));
    }
}
