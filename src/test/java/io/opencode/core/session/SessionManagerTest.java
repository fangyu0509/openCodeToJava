package io.opencode.core.session;

import io.opencode.core.agent.AgentConfig;
import io.opencode.core.config.OpenCodeConfig;
import io.opencode.core.model.SessionId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class SessionManagerTest {

    @Test
    void createAndGet(@TempDir Path tempDir) {
        var config = OpenCodeConfig.defaults(tempDir);
        var mgr = new SessionManager(config);
        var session = new InMemorySession(AgentConfig.builder("build").build());
        mgr.create(session);
        assertTrue(mgr.get(session.id()).isPresent());
        assertEquals(session.id(), mgr.get(session.id()).get().id());
    }

    @Test
    void getByStringId(@TempDir Path tempDir) {
        var config = OpenCodeConfig.defaults(tempDir);
        var mgr = new SessionManager(config);
        var session = new InMemorySession(AgentConfig.builder("build").build());
        mgr.create(session);
        assertTrue(mgr.get(session.id().value()).isPresent());
    }

    @Test
    void removeSession(@TempDir Path tempDir) {
        var config = OpenCodeConfig.defaults(tempDir);
        var mgr = new SessionManager(config);
        var session = new InMemorySession(AgentConfig.builder("build").build());
        mgr.create(session);
        mgr.remove(session.id());
        assertTrue(mgr.get(session.id()).isEmpty());
    }

    @Test
    void unknownIdReturnsEmpty(@TempDir Path tempDir) {
        var config = OpenCodeConfig.defaults(tempDir);
        var mgr = new SessionManager(config);
        assertTrue(mgr.get(SessionId.random()).isEmpty());
    }

    @Test
    void countReturnsNumberOfSessions(@TempDir Path tempDir) {
        var config = OpenCodeConfig.defaults(tempDir);
        var mgr = new SessionManager(config);
        mgr.create(new InMemorySession(AgentConfig.builder("a").build()));
        mgr.create(new InMemorySession(AgentConfig.builder("b").build()));
        assertEquals(2, mgr.count());
    }

    @Test
    void allReturnsAllSessions(@TempDir Path tempDir) {
        var config = OpenCodeConfig.defaults(tempDir);
        var mgr = new SessionManager(config);
        mgr.create(new InMemorySession(AgentConfig.builder("a").build()));
        mgr.create(new InMemorySession(AgentConfig.builder("b").build()));
        assertEquals(2, mgr.all().size());
    }

    @Test
    void loadSessionsDoesNotThrowWithNoDataDir(@TempDir Path tempDir) {
        var config = OpenCodeConfig.defaults(tempDir);
        var mgr = new SessionManager(config);
        assertDoesNotThrow(mgr::loadSessions);
    }

    @Test
    void shutdownPersistsFileSessions(@TempDir Path tempDir) throws Exception {
        var dataDir = tempDir.resolve("data");
        var config = new OpenCodeConfig("0.1.0", tempDir.resolve("workspace"),
            java.util.Optional.of(dataDir), java.util.Optional.empty(),
            java.util.List.of(), java.util.List.of(),
            OpenCodeConfig.ServerConfig.defaultConfig(), java.util.List.of(), false, 100_000, 5, java.util.List.of());
        var mgr = new SessionManager(config);
        var session = new FileSession(AgentConfig.builder("build").build(), dataDir);
        session.append(Message.userText("hello"));
        mgr.create(session);
        mgr.shutdown();

        var sessionDir = dataDir.resolve("sessions").resolve(session.id().value());
        assertTrue(java.nio.file.Files.exists(sessionDir.resolve("messages.jsonl")));
        assertTrue(java.nio.file.Files.exists(sessionDir.resolve("config.json")));
    }

    @Test
    void shutdownDoesNotThrowOnInMemorySessions(@TempDir Path tempDir) {
        var config = OpenCodeConfig.defaults(tempDir);
        var mgr = new SessionManager(config);
        mgr.create(new InMemorySession(AgentConfig.builder("build").build()));
        assertDoesNotThrow(mgr::shutdown);
    }
}
