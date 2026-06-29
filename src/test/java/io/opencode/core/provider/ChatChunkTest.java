package io.opencode.core.provider;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ChatChunkTest {

    @Test
    void textFactory() {
        var chunk = ChatChunk.text("hello");
        assertEquals("hello", chunk.textDelta().get());
        assertTrue(chunk.toolCallDelta().isEmpty());
        assertTrue(chunk.usage().isEmpty());
    }

    @Test
    void toolCallFactory() {
        var chunk = ChatChunk.toolCall("call-1", "read", "{}");
        assertTrue(chunk.textDelta().isEmpty());
        assertEquals("call-1", chunk.toolCallDelta().get().id());
        assertEquals("read", chunk.toolCallDelta().get().toolId());
        assertEquals("{}", chunk.toolCallDelta().get().argsJsonDelta());
    }

    @Test
    void usageFactory() {
        var usage = new ChatResponse.Usage(10, 20, 30);
        var chunk = ChatChunk.usage(usage);
        assertTrue(chunk.textDelta().isEmpty());
        assertTrue(chunk.toolCallDelta().isEmpty());
        assertEquals(10, chunk.usage().get().promptTokens());
    }

    @Test
    void doneFactory() {
        var chunk = ChatChunk.done();
        assertTrue(chunk.textDelta().isEmpty());
        assertTrue(chunk.toolCallDelta().isEmpty());
        assertTrue(chunk.usage().isEmpty());
    }

    @Test
    void toolCallDeltaRecord() {
        var delta = new ChatChunk.ToolCallDelta("id1", "read", "{\"key\"");
        assertEquals("id1", delta.id());
        assertEquals("read", delta.toolId());
        assertEquals("{\"key\"", delta.argsJsonDelta());
    }
}
