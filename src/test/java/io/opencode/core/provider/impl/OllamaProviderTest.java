package io.opencode.core.provider.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
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

class OllamaProviderTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private HttpServer server;
    private OllamaProvider provider;
    private volatile String responseBody;

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.setExecutor(java.util.concurrent.Executors.newSingleThreadExecutor());
        server.createContext("/api/chat", exchange -> {
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            var resp = responseBody != null ? responseBody : defaultTextResponse();
            exchange.sendResponseHeaders(200, resp.getBytes(StandardCharsets.UTF_8).length);
            exchange.getResponseBody().write(resp.getBytes(StandardCharsets.UTF_8));
            exchange.close();
        });
        server.start();
        provider = new OllamaProvider();
        provider = new OllamaProvider("http://localhost:" + server.getAddress().getPort());
    }

    @AfterEach
    void tearDown() {
        if (server != null) server.stop(0);
    }

    private String defaultTextResponse() {
        return """
            {"model":"llama3","created_at":"2024-01-01T00:00:00Z","message":{"role":"assistant","content":"Hello from Ollama!"},"done":true}
            """.strip();
    }

    @Test
    void name() {
        assertEquals("ollama", provider.name());
    }

    @Test
    void defaultModel() {
        assertTrue(provider.defaultModel().isPresent());
        assertEquals("ollama", provider.defaultModel().get().providerId());
    }

    @Test
    void supportsModel() {
        assertTrue(provider.supportsModel("any-model"));
    }

    @Test
    void chatReturnsText() throws Exception {
        responseBody = defaultTextResponse();
        var request = ChatRequest.builder("You are helpful", List.of(Message.userText("hi")),
                ModelRef.of("ollama", "llama3"))
            .stream(false).build();

        var response = provider.chat(request).get(5, TimeUnit.SECONDS);

        assertEquals(ChatResponse.Type.TEXT, response.type());
        assertEquals("Hello from Ollama!", response.text().orElse(""));
    }

    @Test
    void chatSendsCorrectRequestBody() throws Exception {
        responseBody = defaultTextResponse();
        var request = ChatRequest.builder("You are helpful", List.of(Message.userText("hi")),
                ModelRef.of("ollama", "llama3"))
            .stream(false).build();

        provider.chat(request).get(5, TimeUnit.SECONDS);
    }

    @Test
    void chatReturnsToolCall() throws Exception {
        responseBody = """
            {"model":"llama3","created_at":"2024-01-01T00:00:00Z","message":{"role":"assistant","content":null,"tool_calls":[{"function":{"name":"read","arguments":"{\\"filePath\\":\\"/tmp/test.txt\\"}"}}]},"done":true}
            """.strip();

        var request = ChatRequest.builder("You are helpful", List.of(Message.userText("read file")),
                ModelRef.of("ollama", "llama3"))
            .stream(false).build();

        var response = provider.chat(request).get(5, TimeUnit.SECONDS);

        assertEquals(ChatResponse.Type.TOOL_CALL, response.type());
        assertEquals(1, response.toolCalls().size());
        var tc = response.toolCalls().get(0);
        assertEquals("read", tc.toolId());
        assertEquals("/tmp/test.txt", tc.args().get("filePath").asText());
    }

    @Test
    void chatHandlesApiError() throws Exception {
        server.stop(0);
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.setExecutor(java.util.concurrent.Executors.newSingleThreadExecutor());
        server.createContext("/api/chat", exchange -> {
            exchange.sendResponseHeaders(500, 0);
            exchange.getResponseBody().write("Internal Server Error".getBytes(StandardCharsets.UTF_8));
            exchange.close();
        });
        server.start();
        provider = new OllamaProvider("http://localhost:" + server.getAddress().getPort());

        var request = ChatRequest.builder("test", List.of(Message.userText("hi")),
                ModelRef.of("ollama", "test")).stream(false).build();
        var future = provider.chat(request);
        assertThrows(java.util.concurrent.ExecutionException.class, () -> future.get(5, TimeUnit.SECONDS));
    }

    @Test
    void emptyMessageResponse() throws Exception {
        responseBody = """
            {"model":"llama3","message":{"role":"assistant","content":null},"done":true}
            """.strip();
        var request = ChatRequest.builder("test", List.of(Message.userText("hi")),
                ModelRef.of("ollama", "test")).stream(false).build();
        var response = provider.chat(request).get(5, TimeUnit.SECONDS);
        assertEquals(ChatResponse.Type.TEXT, response.type());
        assertEquals("", response.text().orElse(""));
    }
}
