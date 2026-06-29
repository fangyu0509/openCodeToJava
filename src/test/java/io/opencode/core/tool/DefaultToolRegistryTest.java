package io.opencode.core.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.opencode.core.tool.util.JsonSchema;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DefaultToolRegistryTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void registerAndGet() {
        var reg = new DefaultToolRegistry(List.of());
        var tool = new StubTool("my_tool");
        reg.register(tool);
        assertTrue(reg.get("my_tool").isPresent());
        assertEquals(tool, reg.get("my_tool").get());
    }

    @Test
    void getUnknownReturnsEmpty() {
        var reg = new DefaultToolRegistry(List.of());
        assertTrue(reg.get("nonexistent").isEmpty());
    }

    @Test
    void unregisterTool() {
        var reg = new DefaultToolRegistry(List.of());
        reg.register(new StubTool("t"));
        reg.unregister("t");
        assertTrue(reg.get("t").isEmpty());
    }

    @Test
    void listIds() {
        var reg = new DefaultToolRegistry(List.of(new StubTool("a"), new StubTool("b")));
        assertEquals(2, reg.ids().size());
        assertTrue(reg.ids().containsAll(List.of("a", "b")));
    }

    @Test
    void allTools() {
        var reg = new DefaultToolRegistry(List.of(new StubTool("a")));
        assertEquals(1, reg.all().size());
    }

    @Test
    void toolsReturnsAll() {
        var reg = new DefaultToolRegistry(List.of(new StubTool("a")));
        var filter = new ToolRegistry.ToolFilter(null, null);
        assertEquals(1, reg.tools(filter).size());
    }

    @Test
    void initializedFromConstructorList() {
        var reg = new DefaultToolRegistry(List.of(new StubTool("a"), new StubTool("b")));
        assertEquals(2, reg.all().size());
    }

    static class StubTool implements Tool<Tool.Metadata> {
        private final String id;
        StubTool(String id) { this.id = id; }
        @Override public String id() { return id; }
        @Override public String description() { return "stub"; }
        @Override public JsonSchema parameters() { return JsonSchema.empty(); }
        @Override public ExecuteResult<Tool.Metadata> execute(JsonNode args, ToolContext ctx) {
            return ExecuteResult.of("ok", Tool.Metadata.EMPTY, "done");
        }
    }
}
