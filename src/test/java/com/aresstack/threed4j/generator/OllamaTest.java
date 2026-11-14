package com.aresstack.threed4j.generator;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Ollama is spoken to in its own API: /api/tags for the installed models, /api/chat for a question. */
class OllamaTest {

    private static HttpServer server(List<String> requests, String chatAnswer) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/tags", exchange -> {
            requests.add("GET /api/tags auth=" + exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] body = "{\"models\":[{\"name\":\"qwen3:8b\",\"size\":1},{\"name\":\"gemma3:27b\",\"size\":2}]}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.createContext("/api/chat", exchange -> {
            requests.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] body = chatAnswer.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        return server;
    }

    @Test
    void listsTheInstalledModelsAndAsksOneWithoutAKey() throws Exception {
        List<String> requests = new ArrayList<>();
        HttpServer server = server(requests, "{\"model\":\"gemma3:27b\",\"message\":{\"role\":\"assistant\","
                + "\"content\":\"<svg/>\"},\"done\":true,\"done_reason\":\"stop\"}");
        try {
            String address = "http://127.0.0.1:" + server.getAddress().getPort() + "/";
            assertEquals(List.of("gemma3:27b", "qwen3:8b"), AiSetup.listOllamaModels(address), "sorted names");
            assertEquals("GET /api/tags auth=null", requests.get(0));

            assertEquals("<svg/>", ChatClient.ollama(address, "gemma3:27b").ask("be an artist", "draw a tree"));
            JsonObject sent = JsonParser.parseString(requests.get(1)).getAsJsonObject();
            assertEquals("gemma3:27b", sent.get("model").getAsString());
            assertFalse(sent.get("stream").getAsBoolean());
            assertEquals("system", sent.getAsJsonArray("messages").get(0).getAsJsonObject().get("role").getAsString());
            assertEquals("draw a tree", sent.getAsJsonArray("messages").get(1).getAsJsonObject().get("content").getAsString());
            assertTrue(sent.getAsJsonObject("options").get("num_predict").getAsInt() >= 256);
            assertFalse(sent.has("max_tokens"), "that is the other API's word");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void anAnswerCutOffAtTheLengthLimitIsAnError() throws Exception {
        HttpServer server = server(new ArrayList<>(), "{\"message\":{\"role\":\"assistant\",\"content\":\"<svg\"},"
                + "\"done\":true,\"done_reason\":\"length\"}");
        try {
            String address = "http://127.0.0.1:" + server.getAddress().getPort();
            IOException problem = assertThrows(IOException.class,
                    () -> ChatClient.ollama(address, "gemma3:27b").ask("s", "u"));
            assertTrue(problem.getMessage().contains("cut off"), problem.getMessage());
        } finally {
            server.stop(0);
        }
    }
}
