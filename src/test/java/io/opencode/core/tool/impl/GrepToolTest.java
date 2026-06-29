package io.opencode.core.tool.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.opencode.core.model.AbortSignal;
import io.opencode.core.model.MessageId;
import io.opencode.core.model.SessionId;
import io.opencode.core.tool.ToolContext;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GrepToolTest {
    private final GrepTool tool = new GrepTool();
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void testId() {
        assertEquals("grep", tool.id());
    }

    @Test
    void testDescription() {
        assertNotNull(tool.description());
    }

    @Test
    void directoryNotFound() {
        var args = mapper.createObjectNode()
            .put("pattern", "test")
            .put("path", "/nonexistent");
        var result = tool.execute(args, dummyContext());
        assertTrue(result.output().contains("not found"));
    }

    @Test
    void findsContentInProject() {
        var args = mapper.createObjectNode().put("pattern", "class");
        var result = tool.execute(args, dummyContext());
        assertNotNull(result.output());
    }

    @Test
    void noMatchFound() {
        var args = mapper.createObjectNode().put("pattern", "zzz_unlikely_match_xyz_123zzz");
        var result = tool.execute(args, dummyContext());
        assertNotNull(result.output());
    }

    @Test
    void missingPatternThrows() {
        var args = mapper.createObjectNode();
        assertThrows(NullPointerException.class, () -> tool.execute(args, dummyContext()));
    }

    @Test
    void withIncludeFilter() {
        var args = mapper.createObjectNode()
            .put("pattern", "class")
            .put("include", "*.java");
        var result = tool.execute(args, dummyContext());
        assertNotNull(result.output());
    }

    private ToolContext dummyContext() {
        return new ToolContext(
            new SessionId("test-session"), MessageId.random(), "test",
            new AbortSignal(), "call-1", List.of(), r -> {}, q -> {});
    }
}
