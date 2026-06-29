package io.opencode.core.agent;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AgentModeTest {

    @Test
    void enumValues() {
        var values = AgentMode.values();
        assertEquals(4, values.length);
    }

    @Test
    void valueOf() {
        assertEquals(AgentMode.SUBAGENT, AgentMode.valueOf("SUBAGENT"));
        assertEquals(AgentMode.PRIMARY, AgentMode.valueOf("PRIMARY"));
        assertEquals(AgentMode.ALL, AgentMode.valueOf("ALL"));
        assertEquals(AgentMode.PLAN, AgentMode.valueOf("PLAN"));
    }
}
