package io.opencode.core.session;

import io.opencode.core.agent.AgentConfig;
import io.opencode.core.model.SessionId;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class InMemorySessionTest {

    @Test
    void createWithAgentConfig() {
        var config = AgentConfig.builder("build").build();
        var session = new InMemorySession(config);
        assertNotNull(session.id());
        assertEquals(config, session.agentConfig());
    }

    @Test
    void createWithIdAndConfig() {
        var config = AgentConfig.builder("build").build();
        var id = SessionId.random();
        var session = new InMemorySession(id, config);
        assertEquals(id, session.id());
    }

    @Test
    void appendAndReadMessages() {
        var session = new InMemorySession(AgentConfig.builder("build").build());
        session.append(Message.userText("hello"));
        session.append(Message.assistantText("world"));
        assertEquals(2, session.messages().size());
        assertEquals("hello", ((Message.TextMessage) session.messages().get(0)).text());
    }

    @Test
    void clearMessages() {
        var session = new InMemorySession(AgentConfig.builder("build").build());
        session.append(Message.userText("hello"));
        session.clear();
        assertTrue(session.messages().isEmpty());
    }

    @Test
    void messagesReturnsImmutableCopy() {
        var session = new InMemorySession(AgentConfig.builder("build").build());
        session.append(Message.userText("hello"));
        assertThrows(UnsupportedOperationException.class, () -> session.messages().add(Message.userText("x")));
    }

    @Test
    void persistAndLoadReturnCompletedFuture() {
        var session = new InMemorySession(AgentConfig.builder("build").build());
        assertDoesNotThrow(() -> session.persist().get());
        assertDoesNotThrow(() -> session.load().get());
    }

    @Test
    void emptySessionHasNoMessages() {
        var session = new InMemorySession(AgentConfig.builder("build").build());
        assertTrue(session.messages().isEmpty());
    }
}
