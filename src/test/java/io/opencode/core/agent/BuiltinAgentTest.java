package io.opencode.core.agent;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BuiltinAgentTest {

    @Test
    void enumValues() {
        var values = BuiltinAgent.values();
        assertEquals(9, values.length);
    }

    @Test
    void buildAgent() {
        var agent = BuiltinAgent.BUILD;
        assertEquals("build", agent.id());
        assertEquals(AgentMode.PRIMARY, agent.mode());
        assertNotNull(agent.description());
    }

    @Test
    void fromId() {
        assertEquals(BuiltinAgent.BUILD, BuiltinAgent.fromId("build"));
        assertEquals(BuiltinAgent.PLAN, BuiltinAgent.fromId("plan"));
        assertEquals(BuiltinAgent.EXPLORE, BuiltinAgent.fromId("explore"));
    }

    @Test
    void fromIdThrowsForUnknown() {
        assertThrows(IllegalArgumentException.class, () -> BuiltinAgent.fromId("nonexistent"));
    }

    @Test
    void allAgentsHaveIds() {
        for (var agent : BuiltinAgent.values()) {
            assertNotNull(agent.id());
            assertFalse(agent.id().isBlank());
        }
    }

    @Test
    void planAgentReadOnly() {
        assertEquals(AgentMode.PRIMARY, BuiltinAgent.PLAN.mode());
    }

    @Test
    void exploreAgentIsSubagent() {
        assertEquals(AgentMode.SUBAGENT, BuiltinAgent.EXPLORE.mode());
    }
}
