package io.opencode.core.tool.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.opencode.core.model.AbortSignal;
import io.opencode.core.model.MessageId;
import io.opencode.core.model.SessionId;
import io.opencode.core.session.Message;
import io.opencode.core.tool.ToolContext;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ReadToolTest {
    private final ReadTool tool = new ReadTool();
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void testId() {
        assertEquals("read", tool.id());
    }

    @Test
    void testDescription() {
        assertTrue(tool.description().contains("file"));
    }

    @Test
    void testParameters() {
        assertNotNull(tool.parameters());
    }

    @Test
    void fileNotFound() {
        var args = mapper.createObjectNode().put("filePath", "/nonexistent/path.txt");
        var result = tool.execute(args, dummyContext());
        assertTrue(result.output().contains("not found"));
    }

    @Test
    void missingArgsThrows() {
        var args = mapper.createObjectNode();
        assertThrows(NullPointerException.class, () -> tool.execute(args, dummyContext()));
    }

    @Test
    void readsExistingFile() throws Exception {
        var tempFile = java.nio.file.Files.createTempFile("opencode-test-read", ".txt");
        java.nio.file.Files.writeString(tempFile, "hello world");
        var args = mapper.createObjectNode().put("filePath", tempFile.toString());
        var result = tool.execute(args, dummyContext());
        assertTrue(result.output().contains("hello world"));
        java.nio.file.Files.deleteIfExists(tempFile);
    }

    private ToolContext dummyContext() {
        return new ToolContext(
            new SessionId("test-session"),
            MessageId.random(),
            "test",
            new AbortSignal(),
            "call-1",
            List.of(),
            r -> {},
            q -> {}
        );
    }
}
