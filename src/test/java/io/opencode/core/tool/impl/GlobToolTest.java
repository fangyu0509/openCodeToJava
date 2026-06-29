package io.opencode.core.tool.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.opencode.core.model.AbortSignal;
import io.opencode.core.model.MessageId;
import io.opencode.core.model.SessionId;
import io.opencode.core.tool.ToolContext;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GlobToolTest {
    private final GlobTool tool = new GlobTool();
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void testId() {
        assertEquals("glob", tool.id());
    }

    @Test
    void directoryNotFound() {
        var args = mapper.createObjectNode()
            .put("pattern", "*.txt")
            .put("path", "/nonexistent");
        var result = tool.execute(args, dummyContext());
        assertTrue(result.output().contains("not found"));
    }

    @Test
    void findsJavaFiles() {
        var args = mapper.createObjectNode().put("pattern", "*.java");
        var result = tool.execute(args, dummyContext());
        assertTrue(result.output().contains("Found"));
    }

    @Test
    void noMatchPattern() {
        var args = mapper.createObjectNode().put("pattern", "zzz_unlikely_pattern_xyz_123.*");
        var result = tool.execute(args, dummyContext());
        assertTrue(result.output().contains("Found 0"));
    }

    @Test
    void missingPatternThrows() {
        var args = mapper.createObjectNode();
        assertThrows(NullPointerException.class, () -> tool.execute(args, dummyContext()));
    }

    private ToolContext dummyContext() {
        return new ToolContext(
            new SessionId("test-session"), MessageId.random(), "test",
            new AbortSignal(), "call-1", List.of(), r -> {}, q -> {});
    }
}
