package io.opencode.core.tool.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.opencode.core.model.AbortSignal;
import io.opencode.core.model.MessageId;
import io.opencode.core.model.SessionId;
import io.opencode.core.session.SnapshotManager;
import io.opencode.core.tool.ToolContext;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class WriteToolTest {
    private final WriteTool tool = new WriteTool(new SnapshotManager());
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void testId() {
        assertEquals("write", tool.id());
    }

    @Test
    void missingArgsThrows() {
        var args = mapper.createObjectNode();
        assertThrows(NullPointerException.class, () -> tool.execute(args, dummyContext()));
    }

    @Test
    void writesFileSuccessfully() throws Exception {
        var tempFile = java.nio.file.Files.createTempFile("opencode-test-write", ".txt");
        var args = mapper.createObjectNode()
            .put("filePath", tempFile.toString())
            .put("content", "test content");
        var result = tool.execute(args, dummyContext());
        assertTrue(result.output().contains("Successfully wrote"));
        assertEquals("test content", java.nio.file.Files.readString(tempFile));
        java.nio.file.Files.deleteIfExists(tempFile);
    }

    private ToolContext dummyContext() {
        return new ToolContext(
            new SessionId("test-session"), MessageId.random(), "test",
            new AbortSignal(), "call-1", List.of(), r -> {}, q -> {});
    }
}
