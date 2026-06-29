package io.opencode.core.session;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.opencode.core.agent.AgentConfig;
import io.opencode.core.model.MessageId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class FileSessionTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @TempDir
    Path tempDir;

    @Test
    void persistAndLoadRoundTrip() throws Exception {
        var config = AgentConfig.builder("build").build();
        var session = new FileSession(config, tempDir);
        session.append(Message.userText("hello"));
        session.append(Message.assistantText("hi there"));
        session.persist().get();

        var loaded = new FileSession(session.id(), config, tempDir, java.util.List.of());
        loaded.load().get();

        assertEquals(2, loaded.messages().size());
        assertEquals("user", loaded.messages().get(0).role());
        assertEquals("hello", ((Message.TextMessage) loaded.messages().get(0)).text());
        assertEquals("assistant", loaded.messages().get(1).role());
        assertEquals("hi there", ((Message.TextMessage) loaded.messages().get(1)).text());
    }

    @Test
    void persistAndLoadPreservesIdsAndTimestamps() throws Exception {
        var config = AgentConfig.builder("build").build();
        var session = new FileSession(config, tempDir);
        session.append(Message.userText("test"));
        session.persist().get();

        var originalId = session.messages().get(0).id();
        var originalTs = session.messages().get(0).timestamp();

        var loaded = new FileSession(session.id(), config, tempDir, java.util.List.of());
        loaded.load().get();

        assertEquals(1, loaded.messages().size());
        assertEquals(originalId.value(), loaded.messages().get(0).id().value());
        assertEquals(originalTs, loaded.messages().get(0).timestamp());
    }

    @Test
    void persistAndLoadToolCallAndResult() throws Exception {
        var config = AgentConfig.builder("build").build();
        var session = new FileSession(config, tempDir);
        var args = MAPPER.createObjectNode().put("pattern", "*.java");
        session.append(Message.toolCall("glob", "call-1", args));
        session.append(Message.toolResult("glob", "call-1", "found 5 files"));
        session.persist().get();

        var loaded = new FileSession(session.id(), config, tempDir, java.util.List.of());
        loaded.load().get();

        assertEquals(2, loaded.messages().size());
        assertInstanceOf(Message.ToolCallMessage.class, loaded.messages().get(0));
        assertInstanceOf(Message.ToolResultMessage.class, loaded.messages().get(1));

        var tc = (Message.ToolCallMessage) loaded.messages().get(0);
        assertEquals("glob", tc.toolId());
        assertEquals("call-1", tc.callId());
        assertEquals("*.java", tc.args().get("pattern").asText());

        var tr = (Message.ToolResultMessage) loaded.messages().get(1);
        assertTrue(tr.output().contains("found 5 files"));
    }

    @Test
    void emptyFileDoesNotThrow() throws Exception {
        var config = AgentConfig.builder("build").build();
        var session = new FileSession(config, tempDir);
        session.persist().get();

        var loaded = new FileSession(session.id(), config, tempDir, java.util.List.of());
        loaded.load().get();
        assertTrue(loaded.messages().isEmpty());
    }

    @Test
    void fileMessageRoundTrip() throws Exception {
        var config = AgentConfig.builder("build").build();
        var session = new FileSession(config, tempDir);
        session.append(new Message.FileMessage(
            MessageId.random(), "user",
            FilePart.reference("/tmp/test.txt"),
            System.currentTimeMillis()));
        session.persist().get();

        var loaded = new FileSession(session.id(), config, tempDir, java.util.List.of());
        loaded.load().get();

        assertEquals(1, loaded.messages().size());
        assertInstanceOf(Message.FileMessage.class, loaded.messages().get(0));
        var fm = (Message.FileMessage) loaded.messages().get(0);
        assertEquals("/tmp/test.txt", fm.file().path());
    }

    @Test
    void backwardCompatWithoutIds() throws Exception {
        var config = AgentConfig.builder("build").build();
        var session = new FileSession(config, tempDir);
        // Manually write old format (no id/ts)
        var sessionDir = tempDir.resolve("sessions").resolve(session.id().value());
        java.nio.file.Files.createDirectories(sessionDir);
        var msgFile = sessionDir.resolve("messages.jsonl");
        java.nio.file.Files.writeString(msgFile,
            "{\"type\":\"text\",\"role\":\"user\",\"text\":\"old msg\"}\n");

        var loaded = new FileSession(session.id(), config, tempDir, java.util.List.of());
        loaded.load().get();

        assertEquals(1, loaded.messages().size());
        assertEquals("old msg", ((Message.TextMessage) loaded.messages().get(0)).text());
    }
}
