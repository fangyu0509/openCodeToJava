package io.opencode.core.session;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MessageTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void userTextFactory() {
        var msg = Message.userText("hello");
        assertEquals("user", msg.role());
        assertEquals("hello", msg.text());
        assertNotNull(msg.id());
        assertTrue(msg.timestamp() > 0);
    }

    @Test
    void assistantTextFactory() {
        var msg = Message.assistantText("hi");
        assertEquals("assistant", msg.role());
        assertEquals("hi", msg.text());
    }

    @Test
    void toolCallFactory() {
        var args = MAPPER.createObjectNode().put("pattern", "*.java");
        var msg = Message.toolCall("glob", "call-1", args);
        assertInstanceOf(Message.ToolCallMessage.class, msg);
        assertEquals("glob", ((Message.ToolCallMessage) msg).toolId());
        assertEquals("call-1", ((Message.ToolCallMessage) msg).callId());
        assertEquals("*.java", ((Message.ToolCallMessage) msg).args().get("pattern").asText());
    }

    @Test
    void toolResultFactory() {
        var msg = Message.toolResult("glob", "call-1", "found 5 files");
        assertInstanceOf(Message.ToolResultMessage.class, msg);
        assertEquals("glob", ((Message.ToolResultMessage) msg).toolId());
        assertEquals("found 5 files", ((Message.ToolResultMessage) msg).output());
    }

    @Test
    void fileMessageRecord() {
        var msg = new Message.FileMessage(
            io.opencode.core.model.MessageId.random(), "user",
            FilePart.reference("/tmp/test.txt"),
            System.currentTimeMillis());
        assertEquals("/tmp/test.txt", msg.file().path());
        assertEquals("user", msg.role());
    }

    @Test
    void textMessageEquality() {
        var id = io.opencode.core.model.MessageId.random();
        var ts = System.currentTimeMillis();
        var a = new Message.TextMessage(id, "user", "hello", ts);
        var b = new Message.TextMessage(id, "user", "hello", ts);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void uniqueIds() {
        var a = Message.userText("a");
        var b = Message.userText("b");
        assertNotEquals(a.id(), b.id());
    }
}
