package io.opencode.core.tool.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.opencode.core.tool.ToolContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ScoutToolTest {
    private final ScoutTool tool = new ScoutTool();
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void idAndDescription() {
        assertEquals("scout", tool.id());
        assertNotNull(tool.description());
        assertFalse(tool.description().isBlank());
        assertNotNull(tool.parameters());
    }

    @Test
    void requiresQueryArgument() throws Exception {
        var args = mapper.createObjectNode();
        var result = tool.execute(args, ToolContext.EMPTY);
        assertTrue(result.output().contains("'query' is required"));
    }

    @Test
    void emptyQueryReturnsError() throws Exception {
        var args = mapper.createObjectNode();
        args.put("query", "");
        var result = tool.execute(args, ToolContext.EMPTY);
        assertTrue(result.output().contains("'query' is required"));
    }

    @Test
    void researchPerformsSearch() throws Exception {
        var args = mapper.createObjectNode();
        args.put("query", "Java 24 features");
        args.put("maxResults", 3);
        var result = tool.execute(args, ToolContext.EMPTY);
        assertNotNull(result.output());
        // May or may not find results depending on network, but should not error
        assertFalse(result.output().startsWith("Error:"));
    }
}
