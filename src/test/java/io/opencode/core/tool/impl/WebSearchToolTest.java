package io.opencode.core.tool.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.opencode.core.model.AbortSignal;
import io.opencode.core.model.MessageId;
import io.opencode.core.model.SessionId;
import io.opencode.core.tool.ToolContext;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class WebSearchToolTest {
    private final WebSearchTool tool = new WebSearchTool();
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void testId() {
        assertEquals("websearch", tool.id());
    }

    @Test
    void testDescription() {
        assertNotNull(tool.description());
    }

    @Test
    void testParameters() {
        assertNotNull(tool.parameters());
    }

    @Test
    void missingQueryThrows() {
        var args = mapper.createObjectNode();
        assertThrows(NullPointerException.class, () -> tool.execute(args, dummyContext()));
    }

    @Test
    @Disabled("requires network and DuckDuckGo access")
    void searchDuckDuckGo() {
        var args = mapper.createObjectNode().put("query", "java programming");
        var result = tool.execute(args, dummyContext());
        assertTrue(result.output().contains("Search results"));
    }

    private ToolContext dummyContext() {
        return new ToolContext(
            new SessionId("test-session"), MessageId.random(), "test",
            new AbortSignal(), "call-1", List.of(), r -> {}, q -> {});
    }
}
