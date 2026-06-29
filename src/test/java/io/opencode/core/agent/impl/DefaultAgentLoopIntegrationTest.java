package io.opencode.core.agent.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.opencode.core.agent.AgentConfig;
import io.opencode.core.agent.AgentMode;
import io.opencode.core.agent.AgentResponse;
import io.opencode.core.config.OpenCodeConfig;
import io.opencode.core.event.SimpleEventBus;
import io.opencode.core.formatter.FormatterService;
import io.opencode.core.permission.GlobPermissionChecker;
import io.opencode.core.tool.util.ReferenceService;
import io.opencode.core.permission.PermissionRules;
import io.opencode.core.prompt.PromptLoader;
import io.opencode.core.lsp.LspService;
import io.opencode.core.plugin.PluginManager;
import io.opencode.core.session.SharedSessionService;
import io.opencode.core.session.SnapshotManager;
import io.opencode.core.tool.util.FileSearchService;
import io.opencode.core.tool.util.ProjectAnalyzer;
import io.opencode.core.provider.ChatChunk;
import io.opencode.core.provider.ChatRequest;
import io.opencode.core.provider.ChatResponse;
import io.opencode.core.provider.DefaultProviderRegistry;
import io.opencode.core.provider.ModelRef;
import io.opencode.core.provider.Provider;
import io.opencode.core.provider.StreamObserver;
import io.opencode.core.provider.UsageTracker;
import io.opencode.core.session.InMemorySession;
import io.opencode.core.session.Message;
import io.opencode.core.session.Session;
import io.opencode.core.tool.ExecuteResult;
import io.opencode.core.tool.Tool;
import io.opencode.core.tool.ToolContext;
import io.opencode.core.tool.ToolRegistry;
import io.opencode.core.tool.util.JsonSchema;
import io.opencode.core.tool.util.ToolUtils;
import io.opencode.core.skill.SkillService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class DefaultAgentLoopIntegrationTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private DefaultProviderRegistry providerRegistry;
    private ToolRegistry toolRegistry;
    private PromptLoader promptLoader;
    private DefaultAgentLoop agentLoop;
    private TestProvider testProvider;

    @BeforeEach
    void setUp() {
        providerRegistry = new DefaultProviderRegistry(List.of());
        testProvider = new TestProvider();
        providerRegistry.register(testProvider);
        toolRegistry = new io.opencode.core.tool.DefaultToolRegistry(List.of(new EchoTool()));
        promptLoader = new PromptLoader();
        var noopPlugin = new PluginManager(toolRegistry, List.of());
        var noopLsp = new LspService();
        agentLoop = new DefaultAgentLoop(providerRegistry, toolRegistry, promptLoader,
            new GlobPermissionChecker(), new SimpleEventBus(), new UsageTracker(),
            new SnapshotManager(), new ProjectAnalyzer(), new FileSearchService(),
            new SharedSessionService(), new SkillService(OpenCodeConfig.defaults(Path.of("/tmp"))),
            noopPlugin, noopLsp, new FormatterService(), new ReferenceService());
    }

    @Test
    void processTextResponse() throws Exception {
        testProvider.setResponse(ChatResponse.text("Hello from AI", ChatResponse.Usage.EMPTY));

        var config = AgentConfig.builder("build")
            .model(ModelRef.of("test", "test-model"))
            .mode(AgentMode.ALL)
            .build();
        var session = new InMemorySession(config);
        var response = agentLoop.process(session, "hello", config).get();

        assertEquals(AgentResponse.Type.TEXT, response.type());
        assertEquals("Hello from AI", response.text().get());
        assertFalse(session.messages().isEmpty());
    }

    @Test
    void processToolCallThenText() throws Exception {
        var toolCallArgs = MAPPER.createObjectNode().put("input", "world");
        var toolCallResponse = ChatResponse.toolCalls(
            List.of(new ChatResponse.ToolCall("call-1", "echo", toolCallArgs)),
            ChatResponse.Usage.EMPTY);
        var textResponse = ChatResponse.text("Done", new ChatResponse.Usage(10, 20, 30));

        testProvider.setResponses(toolCallResponse, textResponse);

        var config = AgentConfig.builder("build")
            .model(ModelRef.of("test", "test-model"))
            .permission(PermissionRules.permissive())
            .mode(AgentMode.ALL)
            .maxSteps(5)
            .build();
        var session = new InMemorySession(config);
        var response = agentLoop.process(session, "do something", config).get();

        assertEquals(AgentResponse.Type.TEXT, response.type());
        assertTrue(response.text().get().contains("Done"));
    }

    @Test
    void processWithStreamingEvents() throws Exception {
        testProvider.setResponse(ChatResponse.text("streamed", ChatResponse.Usage.EMPTY));

        var config = AgentConfig.builder("build")
            .model(ModelRef.of("test", "test-model"))
            .mode(AgentMode.ALL)
            .build();
        var session = new InMemorySession(config);
        var events = new java.util.ArrayList<String>();
        agentLoop.process(session, "hi", config, events::add).get();

        assertFalse(events.isEmpty());
        assertTrue(events.stream().anyMatch(e -> e.contains("token")));
        assertTrue(events.stream().anyMatch(e -> e.contains("done")));
    }

    @Test
    void processMaxSteps() throws Exception {
        testProvider.setResponse(ChatResponse.toolCalls(
            List.of(new ChatResponse.ToolCall("call-1", "echo",
                MAPPER.createObjectNode().put("input", "x"))),
            ChatResponse.Usage.EMPTY));
        // Always returns tool call -> infinite loop, max steps should kick in

        var config = AgentConfig.builder("build")
            .model(ModelRef.of("test", "test-model"))
            .permission(PermissionRules.permissive())
            .mode(AgentMode.ALL)
            .maxSteps(2)
            .build();
        var session = new InMemorySession(config);
        var response = agentLoop.process(session, "loop", config).get();

        assertEquals(AgentResponse.Type.MAX_STEPS, response.type());
    }

    @Test
    void processPlanMode() throws Exception {
        testProvider.setResponses(
            ChatResponse.text("Step 1: do X\nStep 2: do Y", ChatResponse.Usage.EMPTY),
            ChatResponse.text("Plan executed", ChatResponse.Usage.EMPTY));

        var config = AgentConfig.builder("build")
            .model(ModelRef.of("test", "test-model"))
            .mode(AgentMode.PLAN)
            .build();
        var session = new InMemorySession(config);
        var response = agentLoop.process(session, "make a plan", config).get();

        assertEquals(AgentResponse.Type.TEXT, response.type());
    }

    @Test
    void processWithToolThatProducesLongOutput() throws Exception {
        var longOutput = "x".repeat(300_000);
        var resultArgs = MAPPER.createObjectNode().put("input", longOutput);
        var toolResponse = ChatResponse.toolCalls(
            List.of(new ChatResponse.ToolCall("call-1", "echo", resultArgs)),
            ChatResponse.Usage.EMPTY);
        var textResponse = ChatResponse.text("Done", ChatResponse.Usage.EMPTY);
        testProvider.setResponses(toolResponse, textResponse);

        var config = AgentConfig.builder("build")
            .model(ModelRef.of("test", "test-model"))
            .permission(PermissionRules.permissive())
            .mode(AgentMode.ALL)
            .maxSteps(5)
            .build();
        var session = new InMemorySession(config);
        var response = agentLoop.process(session, "long output", config).get();

        assertEquals(AgentResponse.Type.TEXT, response.type());
    }

    static class TestProvider implements Provider {
        private final AtomicReference<ChatResponse> singleResponse = new AtomicReference<>();
        private ChatResponse[] responses;
        private int index;

        void setResponse(ChatResponse r) { singleResponse.set(r); }
        void setResponses(ChatResponse... r) { this.responses = r; this.index = 0; }

        @Override public String name() { return "test"; }
        @Override public Optional<ModelRef> defaultModel() { return Optional.of(ModelRef.of("test", "test-model")); }
        @Override public boolean supportsModel(String m) { return true; }

        @Override
        public CompletableFuture<ChatResponse> chat(ChatRequest request) {
            var r = singleResponse.get();
            if (r != null) return CompletableFuture.completedFuture(r);
            if (responses != null && index < responses.length) {
                return CompletableFuture.completedFuture(responses[index++]);
            }
            return CompletableFuture.completedFuture(ChatResponse.text("ok", ChatResponse.Usage.EMPTY));
        }

        @Override
        public CompletableFuture<Void> chatStream(ChatRequest request, StreamObserver<ChatChunk> observer) {
            var r = singleResponse.get();
            if (r != null) {
                if (r.type() == ChatResponse.Type.TEXT) {
                    r.text().ifPresent(t -> observer.onNext(ChatChunk.text(t)));
                } else {
                    for (var tc : r.toolCalls()) {
                        observer.onNext(ChatChunk.toolCall(tc.id(), tc.toolId(), tc.args().toString()));
                    }
                }
                observer.onNext(ChatChunk.usage(r.usage()));
                observer.onComplete();
                return CompletableFuture.completedFuture(null);
            }
            if (responses != null && index < responses.length) {
                var resp = responses[index++];
                if (resp.type() == ChatResponse.Type.TEXT) {
                    resp.text().ifPresent(t -> observer.onNext(ChatChunk.text(t)));
                } else {
                    for (var tc : resp.toolCalls()) {
                        observer.onNext(ChatChunk.toolCall(tc.id(), tc.toolId(), tc.args().toString()));
                    }
                }
                observer.onNext(ChatChunk.usage(resp.usage()));
                observer.onComplete();
                return CompletableFuture.completedFuture(null);
            }
            observer.onNext(ChatChunk.text("ok"));
            observer.onComplete();
            return CompletableFuture.completedFuture(null);
        }
    }

    static class EchoTool implements Tool<Tool.Metadata> {
        @Override public String id() { return "echo"; }
        @Override public String description() { return "Echoes input back"; }
        @Override public JsonSchema parameters() {
            return ToolUtils.schema().string("input", "Input to echo", true).build();
        }
        @Override public ExecuteResult<Tool.Metadata> execute(JsonNode args, ToolContext ctx) {
            return ExecuteResult.of("echo", Tool.Metadata.EMPTY, args.get("input").asText());
        }
    }
}
