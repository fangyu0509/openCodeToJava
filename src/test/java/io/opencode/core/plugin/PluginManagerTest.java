package io.opencode.core.plugin;

import io.opencode.core.tool.ExecuteResult;
import io.opencode.core.tool.Tool;
import io.opencode.core.tool.ToolContext;
import io.opencode.core.tool.ToolRegistry;
import io.opencode.core.tool.util.JsonSchema;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PluginManagerTest {
    private final ToolRegistry toolReg = new StubToolRegistry();
    private final PluginManager pm = new PluginManager(toolReg, List.of());

    @Test
    void registerAndListPlugin() {
        pm.register(new TestPlugin());
        var plugins = pm.listPlugins();
        assertEquals(1, plugins.size());
        assertEquals("test-plugin", plugins.get(0).name());
        assertEquals("1.0.0", plugins.get(0).version());
        assertTrue(plugins.get(0).tools().contains("test-tool"));
    }

    @Test
    void pluginToolIsRegistered() {
        pm.register(new TestPlugin());
        assertTrue(toolReg.get("test-tool").isPresent());
    }

    @Test
    void unloadRemovesPlugin() {
        pm.register(new TestPlugin());
        assertEquals(1, pm.listPlugins().size());
        pm.unload("test-plugin");
        assertEquals(0, pm.listPlugins().size());
        assertTrue(toolReg.get("test-tool").isEmpty());
    }

    @Test
    void unloadNonExistentDoesNotThrow() {
        pm.unload("nonexistent");
    }

    @Test
    void duplicateRegistration() {
        pm.register(new TestPlugin());
        pm.register(new TestPlugin());
        assertEquals(1, pm.listPlugins().size());
    }

    static class TestPlugin implements Plugin {
        @Override
        public String name() { return "test-plugin"; }

        @Override
        public String version() { return "1.0.0"; }

        @Override
        public List<Tool<?>> tools() {
            return List.of(new Tool<Tool.Metadata>() {
                @Override public String id() { return "test-tool"; }
                @Override public String description() { return "A test tool"; }
                @Override public JsonSchema parameters() { return JsonSchema.empty(); }
                @Override public ExecuteResult<Tool.Metadata> execute(
                        com.fasterxml.jackson.databind.JsonNode args, ToolContext ctx) {
                    return ExecuteResult.of("test", Tool.Metadata.EMPTY, "test output");
                }
            });
        }
    }

    static class StubToolRegistry implements ToolRegistry {
        private final java.util.Map<String, Tool<?>> tools = new java.util.concurrent.ConcurrentHashMap<>();

        @Override
        public List<String> ids() { return List.copyOf(tools.keySet()); }

        @Override
        public List<Tool<?>> all() { return List.copyOf(tools.values()); }

        @Override
        public java.util.Optional<Tool<?>> get(String id) {
            return java.util.Optional.ofNullable(tools.get(id));
        }

        @Override
        public List<Tool<?>> tools(ToolFilter filter) { return all(); }

        @Override
        public void register(Tool<?> tool) { tools.put(tool.id(), tool); }

        @Override
        public void unregister(String id) { tools.remove(id); }
    }
}
