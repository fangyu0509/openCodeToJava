package io.opencode.core.tool;

import io.opencode.core.model.AbortSignal;
import io.opencode.core.model.MessageId;
import io.opencode.core.model.SessionId;
import io.opencode.core.session.Message;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ToolContextTest {

    @Test
    void recordComponents() {
        var sessionId = SessionId.random();
        var messageId = MessageId.random();
        var abort = new AbortSignal();
        var ctx = new ToolContext(
            sessionId, messageId, "test", abort, "call-1",
            List.of(Message.userText("hi")), r -> {}, q -> {}
        );
        assertEquals(sessionId, ctx.sessionId());
        assertEquals(messageId, ctx.messageId());
        assertEquals("test", ctx.agent());
        assertEquals(abort, ctx.abort());
        assertEquals("call-1", ctx.callId());
        assertEquals(1, ctx.messages().size());
    }

    @Test
    void nullAbortDefaultsToNew() {
        var ctx = new ToolContext(
            SessionId.random(), MessageId.random(), "test", null, "call-1",
            List.of(), r -> {}, q -> {}
        );
        assertNotNull(ctx.abort());
        assertFalse(ctx.abort().isAborted());
    }

    @Test
    void reportMetadataCallsConsumer() {
        var result = new ExecuteResult<?>[1];
        var ctx = new ToolContext(
            SessionId.random(), MessageId.random(), "test", null, "call-1",
            List.of(), r -> result[0] = r, q -> {}
        );
        var metadata = ExecuteResult.of("title", Tool.Metadata.EMPTY, "ok");
        ctx.reportMetadata(metadata);
        assertSame(metadata, result[0]);
    }

    @Test
    void askPermissionCallsConsumer() {
        var question = new String[1];
        var ctx = new ToolContext(
            SessionId.random(), MessageId.random(), "test", null, "call-1",
            List.of(), r -> {}, q -> question[0] = q
        );
        ctx.askPermission("allow?");
        assertEquals("allow?", question[0]);
    }
}
