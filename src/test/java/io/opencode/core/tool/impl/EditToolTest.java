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

class EditToolTest {
    private final EditTool tool = new EditTool(new SnapshotManager());
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void testId() {
        assertEquals("edit", tool.id());
    }

    @Test
    void fileNotFound() {
        var args = mapper.createObjectNode()
            .put("filePath", "/nonexistent/path.txt")
            .put("oldString", "foo")
            .put("newString", "bar");
        var result = tool.execute(args, dummyContext());
        assertTrue(result.output().contains("not found"));
    }

    @Test
    void editsFileSuccessfully() throws Exception {
        var tempFile = java.nio.file.Files.createTempFile("opencode-test-edit", ".txt");
        java.nio.file.Files.writeString(tempFile, "hello foo world");
        var args = mapper.createObjectNode()
            .put("filePath", tempFile.toString())
            .put("oldString", "foo")
            .put("newString", "bar");
        var result = tool.execute(args, dummyContext());
        assertTrue(result.output().contains("Successfully edited"));
        assertEquals("hello bar world", java.nio.file.Files.readString(tempFile));
        java.nio.file.Files.deleteIfExists(tempFile);
    }

    @Test
    void noMatch() throws Exception {
        var tempFile = java.nio.file.Files.createTempFile("opencode-test-edit-nomatch", ".txt");
        java.nio.file.Files.writeString(tempFile, "hello world");
        var args = mapper.createObjectNode()
            .put("filePath", tempFile.toString())
            .put("oldString", "nonexistent")
            .put("newString", "bar");
        var result = tool.execute(args, dummyContext());
        assertTrue(result.output().contains("not find"));
        java.nio.file.Files.deleteIfExists(tempFile);
    }

    @Test
    void multipleMatches() throws Exception {
        var tempFile = java.nio.file.Files.createTempFile("opencode-test-edit-multi", ".txt");
        java.nio.file.Files.writeString(tempFile, "foo foo foo");
        var args = mapper.createObjectNode()
            .put("filePath", tempFile.toString())
            .put("oldString", "foo")
            .put("newString", "bar");
        var result = tool.execute(args, dummyContext());
        assertTrue(result.output().contains("matches"));
        java.nio.file.Files.deleteIfExists(tempFile);
    }

    private ToolContext dummyContext() {
        return new ToolContext(
            new SessionId("test-session"), MessageId.random(), "test",
            new AbortSignal(), "call-1", List.of(), r -> {}, q -> {});
    }
}
