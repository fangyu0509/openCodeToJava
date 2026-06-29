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

class WebFetchToolTest {
    private final WebFetchTool tool = new WebFetchTool();
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void testId() {
        assertEquals("webfetch", tool.id());
    }

    @Test
    void missingUrlThrows() {
        var args = mapper.createObjectNode();
        assertThrows(NullPointerException.class, () -> tool.execute(args, dummyContext()));
    }

    @Test
    void invalidUrl() {
        var args = mapper.createObjectNode().put("url", "not-a-valid-url");
        var result = tool.execute(args, dummyContext());
        assertTrue(result.output().contains("Error") || result.output().contains("Failed"));
    }

    @Test
    @Disabled("requires network")
    void fetchesUrl() {
        var args = mapper.createObjectNode().put("url", "https://example.com");
        var result = tool.execute(args, dummyContext());
        assertTrue(result.output().contains("Status: 200"));
    }

    private ToolContext dummyContext() {
        return new ToolContext(
            new SessionId("test-session"), MessageId.random(), "test",
            new AbortSignal(), "call-1", List.of(), r -> {}, q -> {});
    }
}
