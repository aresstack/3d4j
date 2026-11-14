package com.aresstack.threed4j.generator;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatClientStreamTest {

    @Test
    void thePiecesOfAStreamedAnswerArePutTogether() throws IOException {
        ChatClient.Streamed answer = ChatClient.streamed("""
                : keep-alive

                data: {"choices":[{"delta":{"role":"assistant","content":""},"finish_reason":null}]}

                data: {"choices":[{"delta":{"reasoning_content":"let me think"},"finish_reason":null}]}

                data: {"choices":[{"delta":{"content":"<svg "},"finish_reason":null}]}

                data: {"choices":[{"delta":{"content":"viewBox=\\"0 0 1 1\\"/>"},"finish_reason":null}]}

                data: {"choices":[{"delta":{},"finish_reason":"stop"}]}

                data: {"choices":[],"usage":{"total_tokens":12}}

                data: [DONE]
                """);
        assertEquals("<svg viewBox=\"0 0 1 1\"/>", answer.text(), "thinking is left out");
        assertFalse(answer.cutOff());
    }

    @Test
    void aStreamThatEndsAtTheLengthLimitIsCutOff() throws IOException {
        ChatClient.Streamed answer = ChatClient.streamed(
                "data: {\"choices\":[{\"delta\":{\"content\":\"<svg\"},\"finish_reason\":\"length\"}]}\r\n\r\ndata: [DONE]\r\n");
        assertEquals("<svg", answer.text());
        assertTrue(answer.cutOff());
    }

    @Test
    void anErrorInsideTheStreamIsReported() {
        IOException problem = assertThrows(IOException.class,
                () -> ChatClient.streamed("data: {\"error\":{\"message\":\"model overloaded\"}}\n\n"));
        assertTrue(problem.getMessage().contains("model overloaded"), problem.getMessage());
    }

    /** A stream that hands out its bytes and then fails, as a connection cut by a proxy does. */
    private static java.io.InputStream cutAfter(String text) {
        byte[] bytes = text.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        return new java.io.InputStream() {
            private int at;

            @Override
            public int read() throws IOException {
                if (at >= bytes.length) {
                    throw new IOException("Premature EOF");
                }
                return bytes[at++] & 0xFF;
            }
        };
    }

    @Test
    void readingStopsAtTheEndMarkWithoutWaitingForTheConnectionToClose() throws IOException {
        String whole = "data: {\"choices\":[{\"delta\":{\"content\":\"<svg/>\"},\"finish_reason\":\"stop\"}]}\n\ndata: [DONE]\n\n";
        assertEquals("<svg/>", ChatClient.streamed(ChatClient.readStream(cutAfter(whole))).text());
    }

    @Test
    void aConnectionCutAfterTheModelFinishedStillGivesTheAnswer() throws IOException {
        String finished = "data: {\"choices\":[{\"delta\":{\"content\":\"<svg/>\"},\"finish_reason\":\"stop\"}]}\n";
        assertEquals("<svg/>", ChatClient.streamed(ChatClient.readStream(cutAfter(finished))).text());
    }

    @Test
    void aConnectionCutInTheMiddleOfTheAnswerIsAnError() {
        String half = "data: {\"choices\":[{\"delta\":{\"content\":\"<svg\"},\"finish_reason\":null}]}\n";
        IOException problem = assertThrows(IOException.class, () -> ChatClient.readStream(cutAfter(half)));
        assertTrue(problem.getMessage().contains("broke off"), problem.getMessage());
    }

    @Test
    void aConnectionClosedQuietlyInTheMiddleOfTheAnswerIsAnErrorToo() {
        String half = "data: {\"choices\":[{\"delta\":{\"content\":\"<svg\"},\"finish_reason\":null}]}\n";
        IOException problem = assertThrows(IOException.class, () -> ChatClient.readStream(
                new java.io.ByteArrayInputStream(half.getBytes(java.nio.charset.StandardCharsets.UTF_8))));
        assertTrue(problem.getMessage().contains("broke off"), problem.getMessage());
    }
}
