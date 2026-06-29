package io.opencode.core.agent;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AgentResponseTest {

    @Test
    void textFactory() {
        var resp = AgentResponse.text("hello", 3);
        assertEquals(AgentResponse.Type.TEXT, resp.type());
        assertEquals("hello", resp.text().get());
        assertTrue(resp.toolCall().isEmpty());
        assertEquals(3, resp.stepsUsed());
    }

    @Test
    void toolCallFactory() {
        var call = new AgentResponse.ToolCall("read", "call-1", null);
        var resp = AgentResponse.toolCall(call, 5);
        assertEquals(AgentResponse.Type.TOOL_CALL, resp.type());
        assertTrue(resp.text().isEmpty());
        assertEquals("read", resp.toolCall().get().toolId());
    }

    @Test
    void errorFactory() {
        var resp = AgentResponse.error("something went wrong");
        assertEquals(AgentResponse.Type.ERROR, resp.type());
        assertEquals("something went wrong", resp.text().get());
        assertEquals(0, resp.stepsUsed());
    }

    @Test
    void maxStepsFactory() {
        var resp = AgentResponse.maxSteps(50);
        assertEquals(AgentResponse.Type.MAX_STEPS, resp.type());
        assertTrue(resp.text().get().contains("50"));
        assertEquals(50, resp.stepsUsed());
    }

    @Test
    void toolCallRecord() {
        var call = new AgentResponse.ToolCall("glob", "c1",
            new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode());
        assertEquals("glob", call.toolId());
        assertEquals("c1", call.callId());
        assertNotNull(call.args());
    }

    @Test
    void enumValues() {
        assertEquals(4, AgentResponse.Type.values().length);
    }
}
