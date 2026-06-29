package io.opencode.core.session;

import io.opencode.core.agent.AgentConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SharedSessionServiceTest {
    private final SharedSessionService service = new SharedSessionService();

    @Test
    void testShareAndGet() {
        var config = AgentConfig.builder("build").build();
        var session = new InMemorySession(config);
        session.append(Message.userText("hello"));
        session.append(Message.assistantText("hi there"));

        var shareId = service.share(session);
        assertNotNull(shareId);
        assertFalse(shareId.isEmpty());

        var shared = service.get(shareId);
        assertNotNull(shared);
        assertEquals(2, shared.messages().size());
        assertEquals("build", shared.agent());
    }

    @Test
    void testShareIdempotent() {
        var config = AgentConfig.builder("build").build();
        var session = new InMemorySession(config);

        var id1 = service.share(session);
        var id2 = service.share(session);
        assertEquals(id1, id2);
    }

    @Test
    void testUniqueShareIds() {
        var session1 = new InMemorySession(AgentConfig.builder("build").build());
        var session2 = new InMemorySession(AgentConfig.builder("plan").build());

        var id1 = service.share(session1);
        var id2 = service.share(session2);
        assertNotEquals(id1, id2);
    }

    @Test
    void testGetNonexistentShare() {
        assertNull(service.get("nonexistent"));
    }

    @Test
    void testExportSession() {
        var config = AgentConfig.builder("build").build();
        var session = new InMemorySession(config);
        session.append(Message.userText("test message"));

        var data = service.exportSession(session);
        assertNotNull(data);
        assertEquals(1, data.messages().size());
        assertEquals("user", data.messages().get(0).role());
        assertEquals("text", data.messages().get(0).type());
        assertEquals("test message", data.messages().get(0).text());
    }

    @Test
    void testListShares() {
        var session1 = new InMemorySession(AgentConfig.builder("build").build());
        var session2 = new InMemorySession(AgentConfig.builder("plan").build());

        service.share(session1);
        service.share(session2);

        assertEquals(2, service.listShares().size());
    }

    @Test
    void testUnshare() {
        var session = new InMemorySession(AgentConfig.builder("build").build());
        var shareId = service.share(session);
        assertNotNull(service.get(shareId));
        assertTrue(service.unshare(session.id().value()));
        assertNull(service.get(shareId));
    }
}
