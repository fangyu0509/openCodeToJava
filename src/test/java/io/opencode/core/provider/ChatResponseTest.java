package io.opencode.core.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ChatResponseTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void textFactory() {
        var resp = ChatResponse.text("hello", ChatResponse.Usage.EMPTY);
        assertEquals(ChatResponse.Type.TEXT, resp.type());
        assertEquals("hello", resp.text().get());
        assertTrue(resp.toolCalls().isEmpty());
    }

    @Test
    void toolCallsFactory() {
        var call = new ChatResponse.ToolCall("call-1", "read", MAPPER.createObjectNode());
        var resp = ChatResponse.toolCalls(List.of(call), ChatResponse.Usage.EMPTY);
        assertEquals(ChatResponse.Type.TOOL_CALL, resp.type());
        assertTrue(resp.text().isEmpty());
        assertEquals(1, resp.toolCalls().size());
    }

    @Test
    void toolCallFactory() {
        var call = new ChatResponse.ToolCall("call-1", "read", MAPPER.createObjectNode());
        var resp = ChatResponse.toolCall(call, ChatResponse.Usage.EMPTY);
        assertEquals(1, resp.toolCalls().size());
    }

    @Test
    void usageRecord() {
        var usage = new ChatResponse.Usage(100, 50, 150);
        assertEquals(100, usage.promptTokens());
        assertEquals(50, usage.completionTokens());
        assertEquals(150, usage.totalTokens());
    }

    @Test
    void emptyUsage() {
        var usage = ChatResponse.Usage.EMPTY;
        assertEquals(0, usage.promptTokens());
        assertEquals(0, usage.completionTokens());
        assertEquals(0, usage.totalTokens());
    }

    @Test
    void toolCallRecord() {
        var args = MAPPER.createObjectNode().put("key", "value");
        var call = new ChatResponse.ToolCall("id1", "read", args);
        assertEquals("id1", call.id());
        assertEquals("read", call.toolId());
        assertEquals("value", call.args().get("key").asText());
    }
}
