package io.opencode.core.tool.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.opencode.core.model.AbortSignal;
import io.opencode.core.model.MessageId;
import io.opencode.core.model.SessionId;
import io.opencode.core.tool.ToolContext;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ShellToolTest {
    private final ShellTool tool = new ShellTool();
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void testId() {
        assertEquals("shell", tool.id());
    }

    @Test
    void echoCommand() {
        var args = mapper.createObjectNode().put("command", "echo hello");
        var result = tool.execute(args, dummyContext());
        assertEquals(0, result.output().indexOf("Exit code: 0"));
        assertTrue(result.output().contains("hello"));
    }

    @Test
    void commandWithError() {
        var args = mapper.createObjectNode().put("command", "exit 42");
        var result = tool.execute(args, dummyContext());
        assertTrue(result.output().contains("Exit code: 42"));
    }

    @Test
    void missingCommandThrows() {
        var args = mapper.createObjectNode();
        assertThrows(NullPointerException.class, () -> tool.execute(args, dummyContext()));
    }

    @Test
    void workingDirectory() {
        var args = mapper.createObjectNode()
            .put("command", "pwd")
            .put("workdir", "/tmp");
        var result = tool.execute(args, dummyContext());
        assertTrue(result.output().contains("Exit code: 0"));
    }

    private ToolContext dummyContext() {
        return new ToolContext(
            new SessionId("test-session"), MessageId.random(), "test",
            new AbortSignal(), "call-1", List.of(), r -> {}, q -> {});
    }
}
