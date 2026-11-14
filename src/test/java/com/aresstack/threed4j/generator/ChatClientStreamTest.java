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
}
