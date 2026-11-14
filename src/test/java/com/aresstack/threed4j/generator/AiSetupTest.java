package com.aresstack.threed4j.generator;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiSetupTest {

    @Test
    void preselectsTheWantedModelThenASimilarOneThenTheFirst() {
        assertEquals("openai/gpt-oss-120b",
                AiSetup.preselect(List.of("a/b", "openai/gpt-oss-120b", "openai/gpt-oss-20b"), "openai/gpt-oss-120b"));
        assertEquals("OpenAI/GPT-OSS-120B",
                AiSetup.preselect(List.of("a/b", "OpenAI/GPT-OSS-120B"), "openai/gpt-oss-120b"), "case does not matter");
        assertEquals("local/gpt-oss-120b-q4",
                AiSetup.preselect(List.of("a/b", "local/gpt-oss-120b-q4"), "openai/gpt-oss-120b"),
                "the only model that contains the name");
        assertEquals("a/b",
                AiSetup.preselect(List.of("a/b", "x/gpt-oss-120b-q4", "y/gpt-oss-120b-q8"), "openai/gpt-oss-120b"),
                "two similar ones: no guess, the first offered");
        assertNull(AiSetup.preselect(List.of(), "openai/gpt-oss-120b"));
    }

    @Test
    void normalizesAddresses() {
        assertEquals("https://ki.intern/v1", AiSetup.normalizeBaseUrl(" https://ki.intern/v1/ "));
        assertEquals("http://127.0.0.1:8080/v1", AiSetup.normalizeBaseUrl("http://127.0.0.1:8080/v1"));
        assertNull(AiSetup.normalizeBaseUrl("ki.intern/v1"));
        assertNull(AiSetup.normalizeBaseUrl("ftp://ki.intern"));
        assertNull(AiSetup.normalizeBaseUrl(""));
    }

    @Test
    void listsModelsSortedAndSendsTheKey() throws Exception {
        String[] authorization = new String[1];
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/models", exchange -> {
            authorization[0] = exchange.getRequestHeaders().getFirst("Authorization");
            byte[] body = "{\"object\":\"list\",\"data\":[{\"id\":\"openai/gpt-oss-20b\"},{\"id\":\"BAAI/bge-m3\"},{\"id\":\"openai/gpt-oss-120b\"}]}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/v1";
            assertEquals(List.of("BAAI/bge-m3", "openai/gpt-oss-120b", "openai/gpt-oss-20b"),
                    AiSetup.listModels(baseUrl, "secret"));
            assertEquals("Bearer secret", authorization[0]);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void refusedRequestNamesTheStatus() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/models", exchange -> {
            byte[] body = "{\"error\":\"invalid api key\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(401, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/v1";
            IOException e = assertThrows(IOException.class, () -> AiSetup.listModels(baseUrl, "wrong"));
            assertTrue(e.getMessage().contains("HTTP 401"), e.getMessage());
        } finally {
            server.stop(0);
        }
    }
}
