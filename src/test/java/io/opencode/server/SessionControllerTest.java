package io.opencode.server;

import io.opencode.core.agent.AgentConfig;
import io.opencode.core.agent.AgentLoop;
import io.opencode.core.agent.AgentResponse;
import io.opencode.core.config.OpenCodeConfig;
import io.opencode.core.plugin.PluginManager;
import io.opencode.core.event.EventBus;
import io.opencode.core.model.AbortSignal;
import io.opencode.core.model.MessageId;
import io.opencode.core.model.SessionId;
import io.opencode.core.provider.ModelRef;
import io.opencode.core.provider.Provider;
import io.opencode.core.provider.ProviderRegistry;
import io.opencode.core.provider.UsageTracker;
import io.opencode.core.session.InMemorySession;
import io.opencode.core.session.Message;
import io.opencode.core.session.Session;
import io.opencode.core.session.SessionManager;
import io.opencode.core.session.SharedSessionService;
import io.opencode.core.skill.SkillService;
import io.opencode.core.tool.util.FileSearchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

class SessionControllerTest {
    private SessionManager sessionManager;
    private AgentLoop agentLoop;
    private ProviderRegistry providerRegistry;
    private EventBus eventBus;
    private UsageTracker usageTracker;
    private SessionController controller;

    @BeforeEach
    void setUp() {
        sessionManager = new SessionManager(OpenCodeConfig.defaults(java.nio.file.Path.of("/tmp")));
        agentLoop = new StubAgentLoop();
        providerRegistry = new StubProviderRegistry();
        eventBus = new StubEventBus();
        usageTracker = new UsageTracker();
        var config = OpenCodeConfig.defaults(java.nio.file.Path.of("/tmp"));
        var toolReg = new io.opencode.core.tool.DefaultToolRegistry(List.of());
        var pluginManager = new PluginManager(toolReg, List.of());
        var skillService = new SkillService(config);
        skillService.loadAllSkills();
        controller = new SessionController(sessionManager, agentLoop, providerRegistry, eventBus, usageTracker, config, pluginManager, new FileSearchService(), new SharedSessionService(), skillService);
    }

    @Test
    void listProviders() {
        var providers = controller.listProviders();
        assertTrue(providers.stream().anyMatch(p -> p.name().equals("stub")));
    }

    @Test
    void createSession() {
        var request = new SessionController.CreateSessionRequest("build", "stub:test");
        var response = controller.createSession(request).block();

        assertNotNull(response);
        assertNotNull(response.id());
        assertEquals("build", response.agent());
        assertEquals(0, response.messageCount());
    }

    @Test
    void createSessionDefault() {
        var response = controller.createSession(null).block();
        assertNotNull(response);
        assertEquals("build", response.agent());
    }

    @Test
    void createPlanSession() {
        var request = new SessionController.CreateSessionRequest("plan", null);
        var response = controller.createSession(request).block();
        assertNotNull(response);
        assertEquals("plan", response.agent());
    }

    @Test
    void getSession() {
        var created = controller.createSession(null).block();
        var session = controller.getSession(created.id()).block();
        assertNotNull(session);
        assertEquals(created.id(), session.id());
    }

    @Test
    void getSessionNotFound() {
        assertThrows(Exception.class, () -> controller.getSession("nonexistent").block());
    }

    @Test
    void getSessionMessages() {
        var created = controller.createSession(null).block();
        var session = sessionManager.get(created.id()).orElseThrow();
        session.append(Message.userText("hello"));
        session.append(Message.assistantText("world"));

        var messages = controller.getSessionMessages(created.id()).block();
        assertNotNull(messages);
        assertEquals(2, messages.size());
        assertEquals("text", messages.get(0).type());
        assertEquals("hello", messages.get(0).text());
        assertEquals("user", messages.get(0).role());
        assertEquals("world", messages.get(1).text());
    }

    @Test
    void sendMessage() {
        var created = controller.createSession(null).block();
        var request = new SessionController.MessageRequest("hello");
        var response = controller.sendMessage(created.id(), request).block();

        assertNotNull(response);
        assertEquals("TEXT", response.type());
        assertEquals("Response to: hello", response.text());
    }

    @Test
    void sendMessageSessionNotFound() {
        var request = new SessionController.MessageRequest("hello");
        var result = controller.sendMessage("nonexistent", request);
        assertThrows(Exception.class, result::block);
    }

    @Test
    void usage() {
        usageTracker.track("test-model", new io.opencode.core.provider.ChatResponse.Usage(100, 20, 120));
        var usage = controller.getUsage();
        assertEquals(120, usage.totalTokens());
        assertEquals(100, usage.promptTokens());
    }

    @Test
    void listSessions() {
        assertEquals(0, controller.listSessions().size());
        controller.createSession(null).block();
        assertEquals(1, controller.listSessions().size());
    }

    @Test
    void deleteSession() {
        var created = controller.createSession(null).block();
        assertEquals(1, controller.listSessions().size());
        controller.deleteSession(created.id()).block();
        assertEquals(0, controller.listSessions().size());
    }

    @Test
    void deleteSessionNotFound() {
        assertThrows(Exception.class, () -> controller.deleteSession("nonexistent").block());
    }

    @Test
    void uploadFile() {
        var created = controller.createSession(null).block();
        var filePart = new StubFilePart("test.txt", "hello world");
        var result = controller.uploadFile(created.id(), filePart).block();
        assertNotNull(result);
        assertEquals("test.txt", result.get("name"));
        assertEquals("text", result.get("type"));

        var messages = controller.getSessionMessages(created.id()).block();
        assertEquals(1, messages.size());
        assertEquals("file", messages.get(0).type());
    }

    @Test
    void uploadFileSessionNotFound() {
        var filePart = new StubFilePart("test.txt", "content");
        assertThrows(Exception.class, () -> controller.uploadFile("nonexistent", filePart).block());
    }

    @Test
    void uploadBinaryFile() {
        var created = controller.createSession(null).block();
        var filePart = new StubFilePart("image.png", "\u0089PNG\r\n\u001a\n");
        var result = controller.uploadFile(created.id(), filePart).block();
        assertNotNull(result);
        assertEquals("image.png", result.get("name"));
        assertEquals("binary", result.get("type"));
    }

    // --- Stubs ---

    static class StubAgentLoop implements AgentLoop {
        @Override
        public CompletableFuture<AgentResponse> process(Session session, String userInput, AgentConfig config) {
            session.append(Message.assistantText("Response to: " + userInput));
            return CompletableFuture.completedFuture(AgentResponse.text("Response to: " + userInput, 1));
        }

        @Override
        public CompletableFuture<AgentResponse> process(Session session, String userInput, AgentConfig config,
                java.util.function.Consumer<String> onEvent) {
            return process(session, userInput, config);
        }
    }

    static class StubProviderRegistry implements ProviderRegistry {
        @Override
        public void register(Provider provider) {}

        @Override
        public Optional<Provider> getProvider(String id) {
            return Optional.empty();
        }

        @Override
        public List<Provider> allProviders() {
            return List.of(new StubProvider());
        }

        @Override
        public Optional<ModelRef> defaultModel() {
            return Optional.empty();
        }
    }

    static class StubProvider implements Provider {
        @Override
        public String name() { return "stub"; }

        @Override
        public CompletableFuture<io.opencode.core.provider.ChatResponse> chat(
                io.opencode.core.provider.ChatRequest request) {
            return CompletableFuture.completedFuture(
                io.opencode.core.provider.ChatResponse.text("ok", io.opencode.core.provider.ChatResponse.Usage.EMPTY));
        }

        @Override
        public CompletableFuture<Void> chatStream(io.opencode.core.provider.ChatRequest request,
                io.opencode.core.provider.StreamObserver<io.opencode.core.provider.ChatChunk> observer) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public Optional<ModelRef> defaultModel() { return Optional.of(ModelRef.of("stub", "test")); }

        @Override
        public boolean supportsModel(String modelId) { return true; }
    }

    static class StubFilePart implements FilePart {
        private final String name;
        private final String content;
        private final HttpHeaders headers;

        StubFilePart(String name, String content) {
            this.name = name;
            this.content = content;
            this.headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        }

        @Override public String name() { return "file"; }
        @Override public String filename() { return name; }
        @Override public HttpHeaders headers() { return headers; }
        @Override public Flux<DataBuffer> content() {
            var buf = new DefaultDataBufferFactory().wrap(content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return Flux.just(buf);
        }
        @Override public Mono<Void> transferTo(java.nio.file.Path dest) { return Mono.empty(); }
        @Override public String toString() { return name; }
    }

    static class StubEventBus implements EventBus {
        @Override
        public <T> void publish(String topic, T event) {}

        @Override
        public <T> io.opencode.core.event.Disposable subscribe(String topic, Class<T> type,
                io.opencode.core.event.EventHandler<T> handler) { return () -> {}; }

        @Override
        public void unsubscribe(String topic, io.opencode.core.event.Disposable subscription) {}
    }
}
