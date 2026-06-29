package io.opencode.core.provider.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpServer;
import io.opencode.core.provider.ChatRequest;
import io.opencode.core.provider.ChatResponse;
import io.opencode.core.provider.ModelRef;
import io.opencode.core.session.Message;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class AnthropicProviderTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private HttpServer server;
    private AnthropicProvider provider;
    private volatile String lastRequestBody;
    private volatile String responseBody;
    private volatile String lastAuthHeader;

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.setExecutor(java.util.concurrent.Executors.newSingleThreadExecutor());
        server.createContext("/v1/messages", exchange -> {
            lastRequestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            lastAuthHeader = exchange.getRequestHeaders().getFirst("x-api-key");
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            var resp = responseBody != null ? responseBody : defaultTextResponse();
            exchange.sendResponseHeaders(200, resp.getBytes(StandardCharsets.UTF_8).length);
            exchange.getResponseBody().write(resp.getBytes(StandardCharsets.UTF_8));
            exchange.close();
        });
        server.start();
        provider = new AnthropicProvider();
        provider.configure("test-anthropic-key", "http://localhost:" + server.getAddress().getPort());
    }

    @AfterEach
    void tearDown() {
        if (server != null) server.stop(0);
    }

    private String defaultTextResponse() {
        return """
            {"id":"msg_01","type":"message","role":"assistant","content":[{"type":"text","text":"Hello from Claude!"}],"model":"claude-3","stop_reason":"end_turn","usage":{"input_tokens":10,"output_tokens":5}}
            """.strip();
    }

    @Test
    void name() {
        assertEquals("anthropic", provider.name());
    }

    @Test
    void defaultModel() {
        assertTrue(provider.defaultModel().isPresent());
        assertEquals("anthropic", provider.defaultModel().get().providerId());
    }

    @Test
    void supportsModel() {
        assertTrue(provider.supportsModel("any-model"));
    }

    @Test
    void chatReturnsText() throws Exception {
        responseBody = defaultTextResponse();
        var request = ChatRequest.builder("You are Claude", List.of(Message.userText("hi")),
                ModelRef.of("anthropic", "claude-3"))
            .stream(false).build();

        var response = provider.chat(request).get(5, TimeUnit.SECONDS);

        assertEquals(ChatResponse.Type.TEXT, response.type());
        assertEquals("Hello from Claude!", response.text().orElse(""));
        assertEquals(15, response.usage().totalTokens());
    }

    @Test
    void chatSendsCorrectHeaders() throws Exception {
        responseBody = defaultTextResponse();
        var request = ChatRequest.builder("You are Claude", List.of(Message.userText("hi")),
                ModelRef.of("anthropic", "claude-3"))
            .stream(false).build();

        provider.chat(request).get(5, TimeUnit.SECONDS);

        assertNotNull(lastAuthHeader);
        assertEquals("test-anthropic-key", lastAuthHeader);
    }

    @Test
    void chatSendsCorrectRequestBody() throws Exception {
        responseBody = defaultTextResponse();
        var request = ChatRequest.builder("You are Claude", List.of(Message.userText("hi")),
                ModelRef.of("anthropic", "claude-3"))
            .stream(false).build();

        provider.chat(request).get(5, TimeUnit.SECONDS);

        assertNotNull(lastRequestBody);
        var body = mapper.readTree(lastRequestBody);
        assertEquals("claude-3", body.get("model").asText());
        assertEquals("user", body.get("messages").get(0).get("role").asText());
    }

    @Test
    void chatReturnsToolCall() throws Exception {
        responseBody = """
            {"id":"msg_02","type":"message","role":"assistant","content":[{"type":"tool_use","id":"toolu_123","name":"read","input":{"filePath":"/tmp/test.txt"}}],"model":"claude-3","stop_reason":"tool_use","usage":{"input_tokens":20,"output_tokens":10}}
            """.strip();

        var request = ChatRequest.builder("You are Claude", List.of(Message.userText("read file")),
                ModelRef.of("anthropic", "claude-3"))
            .stream(false).build();

        var response = provider.chat(request).get(5, TimeUnit.SECONDS);

        assertEquals(ChatResponse.Type.TOOL_CALL, response.type());
        assertEquals(1, response.toolCalls().size());
        var tc = response.toolCalls().get(0);
        assertEquals("toolu_123", tc.id());
        assertEquals("read", tc.toolId());
        assertEquals("/tmp/test.txt", tc.args().get("filePath").asText());
    }

    @Test
    void chatHandlesApiError() throws Exception {
        server.stop(0);
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.setExecutor(java.util.concurrent.Executors.newSingleThreadExecutor());
        server.createContext("/v1/messages", exchange -> {
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            var error = "{\"error\":{\"message\":\"Insufficient credits\",\"type\":\"authentication_error\"}}";
            exchange.sendResponseHeaders(401, error.getBytes(StandardCharsets.UTF_8).length);
            exchange.getResponseBody().write(error.getBytes(StandardCharsets.UTF_8));
            exchange.close();
        });
        server.start();
        provider.configure("test-key", "http://localhost:" + server.getAddress().getPort());

        var request = ChatRequest.builder("test", List.of(Message.userText("hi")),
                ModelRef.of("anthropic", "test")).stream(false).build();

        var future = provider.chat(request);
        assertThrows(java.util.concurrent.ExecutionException.class, () -> future.get(5, TimeUnit.SECONDS));
    }

    @Test
    void missingApiKey() {
        var np = new AnthropicProvider();
        np.configure(null, null);
        var request = ChatRequest.builder("test", List.of(Message.userText("hi")),
                ModelRef.of("anthropic", "test")).stream(false).build();
        var future = np.chat(request);
        assertThrows(java.util.concurrent.ExecutionException.class, () -> future.get(5, TimeUnit.SECONDS));
    }
}
