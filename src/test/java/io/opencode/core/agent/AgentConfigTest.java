package io.opencode.core.agent;

import io.opencode.core.permission.PermissionRules;
import io.opencode.core.provider.ModelRef;
import io.opencode.core.tool.util.JsonSchema;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AgentConfigTest {

    @Test
    void builderCreatesConfigWithName() {
        var config = AgentConfig.builder("build").build();
        assertEquals("build", config.name());
    }

    @Test
    void builderSetsAllFields() {
        var config = AgentConfig.builder("test")
            .description("test agent")
            .mode(AgentMode.PLAN)
            .permission(PermissionRules.strict())
            .model(ModelRef.of("openai", "gpt-4"))
            .temperature(0.7)
            .maxSteps(10)
            .prompt("custom prompt")
            .options(Map.of("key", "value"))
            .jsonSchema(JsonSchema.empty())
            .build();
        assertEquals("test agent", config.description());
        assertEquals(AgentMode.PLAN, config.mode());
        assertEquals("gpt-4", config.model().get().modelId());
        assertEquals(0.7, config.temperature().get());
        assertEquals(10, config.maxSteps().get());
        assertEquals("custom prompt", config.prompt().get());
        assertEquals("value", config.options().get("key"));
    }

    @Test
    void blankNameThrows() {
        assertThrows(IllegalArgumentException.class, () -> AgentConfig.builder("").build());
        assertThrows(IllegalArgumentException.class, () -> AgentConfig.builder("   ").build());
    }

    @Test
    void defaultsAreApplied() {
        var config = AgentConfig.builder("test").build();
        assertNotNull(config.permission());
        assertNotNull(config.options());
        assertTrue(config.model().isEmpty());
        assertTrue(config.temperature().isEmpty());
        assertTrue(config.maxSteps().isEmpty());
        assertTrue(config.prompt().isEmpty());
        assertNotNull(config.jsonSchema());
    }

    @Test
    void defaultMode() {
        var config = AgentConfig.builder("test").build();
        assertEquals(AgentMode.SUBAGENT, config.mode());
    }

    @Test
    void nullModelSetsEmpty() {
        var config = AgentConfig.builder("test").model(null).build();
        assertTrue(config.model().isEmpty());
    }
}
