package io.opencode.core.agent.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.opencode.core.agent.AgentConfig;
import io.opencode.core.agent.AgentMode;
import io.opencode.core.agent.AgentResponse;
import io.opencode.core.config.OpenCodeConfig;
import io.opencode.core.event.EventBus;
import io.opencode.core.formatter.FormatterService;
import io.opencode.core.lsp.LspService;
import io.opencode.core.tool.util.ReferenceService;
import io.opencode.core.model.AbortSignal;
import io.opencode.core.plugin.PluginManager;
import io.opencode.core.model.MessageId;
import io.opencode.core.permission.PermissionAction;
import io.opencode.core.session.SharedSessionService;
import io.opencode.core.session.SnapshotManager;
import io.opencode.core.permission.PermissionChecker;
import io.opencode.core.permission.PermissionRules;
import io.opencode.core.prompt.PromptLoader;
import io.opencode.core.provider.*;
import io.opencode.core.session.InMemorySession;
import io.opencode.core.session.Message;
import io.opencode.core.session.Session;
import io.opencode.core.skill.SkillService;
import io.opencode.core.tool.ExecuteResult;
import io.opencode.core.tool.Tool;
import io.opencode.core.tool.ToolContext;
import io.opencode.core.tool.ToolRegistry;
import io.opencode.core.tool.util.FileSearchService;
import io.opencode.core.tool.util.ProjectAnalyzer;
import io.opencode.core.tool.util.JsonSchema;
import io.opencode.core.tool.util.ProjectAnalyzer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

class DefaultAgentLoopTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private StubProviderRegistry providerRegistry;
    private StubToolRegistry toolRegistry;
    private StubPromptLoader promptLoader;
    private StubPermissionChecker permissionChecker;
    private StubEventBus eventBus;
    private UsageTracker usageTracker;
    private DefaultAgentLoop agentLoop;

    @BeforeEach
    void setUp() {
        providerRegistry = new StubProviderRegistry();
        toolRegistry = new StubToolRegistry();
        promptLoader = new StubPromptLoader();
        permissionChecker = new StubPermissionChecker();
        eventBus = new StubEventBus();
        usageTracker = new UsageTracker();
        agentLoop = new DefaultAgentLoop(providerRegistry, toolRegistry, promptLoader,
            permissionChecker, eventBus, usageTracker, new SnapshotManager(),
            new ProjectAnalyzer(), new FileSearchService(), new SharedSessionService(),
            new SkillService(OpenCodeConfig.defaults(java.nio.file.Path.of("/tmp"))),
            noopPluginManager(), noopLspService(), new FormatterService(), new ReferenceService());
    }

    @Test
    void textResponse() throws Exception {
        providerRegistry.provider.firstResponse = ChatResponse.text("Hello!", ChatResponse.Usage.EMPTY);
        var config = AgentConfig.builder("build").build();
        var session = new InMemorySession(config);

        var future = agentLoop.process(session, "hi", config);
        var response = future.get();

        assertEquals(AgentResponse.Type.TEXT, response.type());
        assertTrue(response.text().orElse("").contains("Hello!"));
        assertEquals(2, session.messages().size());
        assertEquals("user", session.messages().get(0).role());
        assertEquals("assistant", session.messages().get(1).role());
    }

    @Test
    void toolCallThenText() throws Exception {
        var args = mapper.createObjectNode().put("pattern", "*.java");
        providerRegistry.provider.firstResponse = ChatResponse.toolCall(
            new ChatResponse.ToolCall("call-1", "glob", args), ChatResponse.Usage.EMPTY);
        providerRegistry.provider.followUpResponse = ChatResponse.text("Found files!", ChatResponse.Usage.EMPTY);

        var config = AgentConfig.builder("build").build();
        var session = new InMemorySession(config);

        var future = agentLoop.process(session, "find files", config);
        var response = future.get();

        assertEquals(AgentResponse.Type.TEXT, response.type());
        assertTrue(response.text().orElse("").contains("Found files!"));
    }

    @Test
    void permissionDeniedSkipsTool() throws Exception {
        permissionChecker.forcedAction = PermissionAction.DENY;
        var args = mapper.createObjectNode().put("filePath", "/etc/passwd");
        providerRegistry.provider.firstResponse = ChatResponse.toolCall(
            new ChatResponse.ToolCall("call-1", "read", args), ChatResponse.Usage.EMPTY);
        providerRegistry.provider.followUpResponse = ChatResponse.text("done", ChatResponse.Usage.EMPTY);

        var config = AgentConfig.builder("build").build();
        var session = new InMemorySession(config);

        var future = agentLoop.process(session, "read file", config);
        future.get();

        var toolResults = session.messages().stream()
            .filter(m -> m instanceof Message.ToolResultMessage).toList();
        assertEquals(1, toolResults.size());
        assertTrue(((Message.ToolResultMessage) toolResults.get(0)).output().contains("Permission denied"));
    }

    @Test
    void unknownToolReturnsError() throws Exception {
        var args = mapper.createObjectNode();
        providerRegistry.provider.firstResponse = ChatResponse.toolCall(
            new ChatResponse.ToolCall("call-1", "nonexistent_tool", args), ChatResponse.Usage.EMPTY);
        providerRegistry.provider.followUpResponse = ChatResponse.text("done", ChatResponse.Usage.EMPTY);

        var config = AgentConfig.builder("build").build();
        var session = new InMemorySession(config);

        var future = agentLoop.process(session, "use bad tool", config);
        future.get();

        var toolResults = session.messages().stream()
            .filter(m -> m instanceof Message.ToolResultMessage).toList();
        assertEquals(1, toolResults.size());
        assertTrue(((Message.ToolResultMessage) toolResults.get(0)).output().contains("Unknown tool"));
    }

    @Test
    void maxStepsReached() throws Exception {
        var args = mapper.createObjectNode().put("pattern", "*.java");
        var toolCall = ChatResponse.toolCall(
            new ChatResponse.ToolCall("call-1", "glob", args), ChatResponse.Usage.EMPTY);
        providerRegistry.provider.firstResponse = toolCall;
        providerRegistry.provider.followUpResponse = toolCall;

        var config = AgentConfig.builder("build").maxSteps(2).build();
        var session = new InMemorySession(config);

        var future = agentLoop.process(session, "loop forever", config);
        var response = future.get();

        assertEquals(AgentResponse.Type.MAX_STEPS, response.type());
    }

    @Test
    void planMode() throws Exception {
        providerRegistry.provider.firstResponse = ChatResponse.text("Plan: 1. Search 2. Fix", ChatResponse.Usage.EMPTY);
        var config = AgentConfig.builder("plan").mode(AgentMode.PLAN).build();
        var session = new InMemorySession(config);

        var future = agentLoop.process(session, "plan something", config);
        var response = future.get();

        assertEquals(1, response.stepsUsed());
        var plans = session.messages().stream()
            .filter(m -> m instanceof Message.TextMessage
                && ((Message.TextMessage) m).text().contains("Plan:")).toList();
        assertEquals(1, plans.size());
    }

    @Test
    void toolExecutionError() throws Exception {
        var args = mapper.createObjectNode().put("filePath", "/nonexistent");
        providerRegistry.provider.firstResponse = ChatResponse.toolCall(
            new ChatResponse.ToolCall("call-1", "read", args), ChatResponse.Usage.EMPTY);
        providerRegistry.provider.followUpResponse = ChatResponse.text("done", ChatResponse.Usage.EMPTY);

        var readTool = new io.opencode.core.tool.impl.ReadTool();
        var existingTool = toolRegistry.tools.get("read");
        toolRegistry.tools.put("read", new Tool<Tool.Metadata>() {
            @Override public String id() { return "read"; }
            @Override public String description() { return "read tool"; }
            @Override public JsonSchema parameters() { return readTool.parameters(); }
            @Override public ExecuteResult<Tool.Metadata> execute(com.fasterxml.jackson.databind.JsonNode a, ToolContext c) {
                throw new RuntimeException("simulated failure");
            }
        });

        var config = AgentConfig.builder("build").build();
        var session = new InMemorySession(config);

        var future = agentLoop.process(session, "crash", config);
        future.get();

        var toolResults = session.messages().stream()
            .filter(m -> m instanceof Message.ToolResultMessage).toList();
        assertEquals(1, toolResults.size());
        assertTrue(((Message.ToolResultMessage) toolResults.get(0)).output().contains("simulated failure"));
    }

    @Test
    void undoCommandWithoutSnapshots() throws Exception {
        var config = AgentConfig.builder("build").build();
        var session = new InMemorySession(config);
        var future = agentLoop.process(session, "/undo", config);
        var response = future.get();

        assertEquals(AgentResponse.Type.TEXT, response.type());
        assertTrue(response.text().orElse("").contains("Nothing to undo"));
    }

    @Test
    void redoCommandWithoutSnapshots() throws Exception {
        var config = AgentConfig.builder("build").build();
        var session = new InMemorySession(config);
        var future = agentLoop.process(session, "/redo", config);
        var response = future.get();

        assertEquals(AgentResponse.Type.TEXT, response.type());
        assertTrue(response.text().orElse("").contains("Nothing to redo"));
    }

    @Test
    void undoCommandWithSnapshot() throws Exception {
        var tempFile = java.nio.file.Files.createTempFile("undo-test", ".txt");
        try {
            java.nio.file.Files.writeString(tempFile, "original");
            var snapshotManager = new SnapshotManager();

            var config = AgentConfig.builder("build").build();
            var session = new InMemorySession(config);
            var sid = session.id().value();

            snapshotManager.snapshotBefore(sid, tempFile, "write");
            java.nio.file.Files.writeString(tempFile, "modified");

            agentLoop = new DefaultAgentLoop(providerRegistry, toolRegistry, promptLoader,
                permissionChecker, eventBus, usageTracker, snapshotManager,
                new ProjectAnalyzer(), new FileSearchService(), new SharedSessionService(),
                new SkillService(OpenCodeConfig.defaults(java.nio.file.Path.of("/tmp"))),
                noopPluginManager(), noopLspService(), new FormatterService(), new ReferenceService());

            var future = agentLoop.process(session, "/undo", config);
            var response = future.get();

            assertEquals(AgentResponse.Type.TEXT, response.type());
            assertTrue(response.text().orElse("").contains("Undid"));
            assertEquals("original", java.nio.file.Files.readString(tempFile));
        } finally {
            java.nio.file.Files.deleteIfExists(tempFile);
        }
    }

    @Test
    void redoCommandAfterUndo() throws Exception {
        var tempFile = java.nio.file.Files.createTempFile("redo-test", ".txt");
        try {
            java.nio.file.Files.writeString(tempFile, "v1");
            var snapshotManager = new SnapshotManager();

            var config = AgentConfig.builder("build").build();
            var session = new InMemorySession(config);
            var sid = session.id().value();

            snapshotManager.snapshotBefore(sid, tempFile, "write");
            java.nio.file.Files.writeString(tempFile, "v2");

            agentLoop = new DefaultAgentLoop(providerRegistry, toolRegistry, promptLoader,
                permissionChecker, eventBus, usageTracker, snapshotManager,
                new ProjectAnalyzer(), new FileSearchService(), new SharedSessionService(),
                new SkillService(OpenCodeConfig.defaults(java.nio.file.Path.of("/tmp"))),
                noopPluginManager(), noopLspService(), new FormatterService(), new ReferenceService());

            agentLoop.process(session, "/undo", config).get();
            var future = agentLoop.process(session, "/redo", config).get();

            assertTrue(future.text().orElse("").contains("Redid"));
            assertEquals("v2", java.nio.file.Files.readString(tempFile));
        } finally {
            java.nio.file.Files.deleteIfExists(tempFile);
        }
    }

    private AgentConfig config(String prompt) {
        return AgentConfig.builder("build").prompt(prompt).build();
    }

    @Test
    void shareCommandSharesSession() throws Exception {
        var config = AgentConfig.builder("build").build();
        var session = new InMemorySession(config);

        agentLoop = new DefaultAgentLoop(providerRegistry, toolRegistry, promptLoader,
            permissionChecker, eventBus, usageTracker, new SnapshotManager(),
            new ProjectAnalyzer(), new FileSearchService(), new SharedSessionService(),
            new SkillService(OpenCodeConfig.defaults(java.nio.file.Path.of("/tmp"))),
            noopPluginManager(), noopLspService(), new FormatterService(), new ReferenceService());

            var future = agentLoop.process(session, "/share", config);
        var response = future.get();

        assertEquals(AgentResponse.Type.TEXT, response.type());
        assertTrue(response.text().orElse("").contains("Session shared"));
    }

    @Test
    void initCommandAnalyzesProject() throws Exception {
        var tempDir = java.nio.file.Files.createTempDirectory("init-test");
        try {
            java.nio.file.Files.writeString(tempDir.resolve("test.java"), "class Test {}");
            var projectAnalyzer = new ProjectAnalyzer();
            var origUserDir = System.getProperty("user.dir");
            System.setProperty("user.dir", tempDir.toString());
            try {
                agentLoop = new DefaultAgentLoop(providerRegistry, toolRegistry, promptLoader,
                    permissionChecker, eventBus, usageTracker, new SnapshotManager(),
                    projectAnalyzer, new FileSearchService(), new SharedSessionService(),
            new SkillService(OpenCodeConfig.defaults(java.nio.file.Path.of("/tmp"))),
            noopPluginManager(), noopLspService(), new FormatterService(), new ReferenceService());

                var config = AgentConfig.builder("build").build();
                var session = new InMemorySession(config);
                var future = agentLoop.process(session, "/init", config);
                var response = future.get();

                assertEquals(AgentResponse.Type.TEXT, response.type());
                assertTrue(response.text().orElse("").contains("Initialized AGENTS.md"));
                assertTrue(java.nio.file.Files.exists(tempDir.resolve("AGENTS.md")));
            } finally {
                System.setProperty("user.dir", origUserDir);
            }
        } finally {
            deleteDirectory(tempDir);
        }
    }

    private void deleteDirectory(java.nio.file.Path dir) {
        try (var stream = java.nio.file.Files.walk(dir)) {
            stream.sorted(java.util.Comparator.reverseOrder())
                .forEach(p -> { try { java.nio.file.Files.deleteIfExists(p); } catch (Exception e) {} });
        } catch (Exception e) {}
    }

    @Test
    void usageTracked() throws Exception {
        providerRegistry.provider.firstResponse = ChatResponse.text("ok", new ChatResponse.Usage(50, 10, 60));
        var config = AgentConfig.builder("build").build();
        var session = new InMemorySession(config);

        agentLoop.process(session, "track this", config).get();

        assertEquals(60, usageTracker.getTotal().totalTokens());
        assertEquals(50, usageTracker.getTotal().promptTokens());
    }

    // --- Stubs ---

    static class StubProvider implements Provider {
        ChatResponse firstResponse = ChatResponse.text("Hello!", ChatResponse.Usage.EMPTY);
        ChatResponse followUpResponse = ChatResponse.text("Hello!", ChatResponse.Usage.EMPTY);
        private int callCount;

        @Override
        public String name() { return "stub"; }

        @Override
        public CompletableFuture<ChatResponse> chat(ChatRequest request) {
            var r = callCount++ == 0 ? firstResponse : followUpResponse;
            return CompletableFuture.completedFuture(r);
        }

        @Override
        public CompletableFuture<Void> chatStream(ChatRequest request, StreamObserver<ChatChunk> observer) {
            var r = callCount++ == 0 ? firstResponse : followUpResponse;
            return CompletableFuture.runAsync(() -> {
                if (r.type() == ChatResponse.Type.TEXT) {
                    r.text().ifPresent(t -> {
                        observer.onNext(ChatChunk.text(t));
                    });
                } else {
                    for (var tc : r.toolCalls()) {
                        observer.onNext(ChatChunk.toolCall(tc.id(), tc.toolId(), tc.args().toString()));
                    }
                }
                observer.onNext(ChatChunk.usage(r.usage()));
                observer.onComplete();
            });
        }

        @Override
        public Optional<ModelRef> defaultModel() { return Optional.of(ModelRef.of("stub", "test-model")); }

        @Override
        public boolean supportsModel(String modelId) { return true; }
    }

    static class StubProviderRegistry implements ProviderRegistry {
        StubProvider provider = new StubProvider();

        @Override
        public void register(Provider p) {}

        @Override
        public Optional<Provider> getProvider(String id) { return Optional.of(provider); }

        @Override
        public List<Provider> allProviders() { return List.of(provider); }

        @Override
        public Optional<ModelRef> defaultModel() { return provider.defaultModel(); }
    }

    static class StubToolRegistry implements ToolRegistry {
        java.util.Map<String, Tool<?>> tools = new java.util.concurrent.ConcurrentHashMap<>();

        StubToolRegistry() {
            var snap = new SnapshotManager();
            add(new io.opencode.core.tool.impl.ReadTool());
            add(new io.opencode.core.tool.impl.WriteTool(snap));
            add(new io.opencode.core.tool.impl.GlobTool());
            add(new io.opencode.core.tool.impl.GrepTool());
            add(new io.opencode.core.tool.impl.ShellTool());
            add(new io.opencode.core.tool.impl.EditTool(snap));
        }

        void add(Tool<?> t) { tools.put(t.id(), t); }

        @Override
        public List<String> ids() { return List.copyOf(tools.keySet()); }

        @Override
        public List<Tool<?>> all() { return List.copyOf(tools.values()); }

        @Override
        public Optional<Tool<?>> get(String id) { return Optional.ofNullable(tools.get(id)); }

        @Override
        public List<Tool<?>> tools(ToolFilter filter) { return all(); }

        @Override
        public void register(Tool<?> tool) { tools.put(tool.id(), tool); }

        @Override
        public void unregister(String id) { tools.remove(id); }
    }

    static class StubPromptLoader extends PromptLoader {
        @Override
        public String load(String name) {
            if ("build".equals(name)) return "You are a build agent.\n";
            return "";
        }
    }

    static class StubPermissionChecker implements PermissionChecker {
        PermissionAction forcedAction = null;

        @Override
        public PermissionAction check(String toolId, String resource, PermissionRules rules) {
            return forcedAction != null ? forcedAction : PermissionAction.ALLOW;
        }
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

    static PluginManager noopPluginManager() {
        var reg = new ToolRegistry() {
            public void register(Tool<?> t) {}
            public void unregister(String id) {}
            public java.util.List<String> ids() { return java.util.List.of(); }
            public java.util.List<Tool<?>> all() { return java.util.List.of(); }
            public java.util.Optional<Tool<?>> get(String id) { return java.util.Optional.empty(); }
            public java.util.List<Tool<?>> tools(ToolRegistry.ToolFilter f) { return java.util.List.of(); }
        };
        return new PluginManager(reg, java.util.List.of());
    }

    static LspService noopLspService() {
        return new LspService() {
            @Override
            public java.util.Optional<io.opencode.core.lsp.LspService.ServerConfig> findConfigFor(java.nio.file.Path p) {
                return java.util.Optional.empty();
            }
            @Override
            public java.util.concurrent.CompletableFuture<com.fasterxml.jackson.databind.JsonNode> execute(
                    String action, java.nio.file.Path filePath, java.nio.file.Path rootPath,
                    int line, int character) {
                return java.util.concurrent.CompletableFuture.completedFuture(null);
            }
        };
    }
}
