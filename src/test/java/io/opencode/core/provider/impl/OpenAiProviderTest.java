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

class OpenAiProviderTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private HttpServer server;
    private OpenAiProvider provider;
    private String baseUrl;
    private volatile String lastRequestBody;
    private volatile String responseBody;

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.setExecutor(java.util.concurrent.Executors.newSingleThreadExecutor());
        server.createContext("/chat/completions", exchange -> {
            lastRequestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            var resp = responseBody != null ? responseBody : defaultResponse();
            exchange.sendResponseHeaders(200, resp.getBytes(StandardCharsets.UTF_8).length);
            exchange.getResponseBody().write(resp.getBytes(StandardCharsets.UTF_8));
            exchange.close();
        });
        server.start();
        var port = server.getAddress().getPort();
        baseUrl = "http://localhost:" + port;
        provider = new OpenAiProvider();
        provider.configure("test-key", baseUrl);
    }

    @AfterEach
    void tearDown() {
        if (server != null) server.stop(0);
    }

    private String defaultResponse() {
        var root = mapper.createObjectNode();
        root.put("id", "chatcmpl-123");
        root.put("object", "chat.completion");
        root.put("created", System.currentTimeMillis() / 1000);
        root.put("model", "test-model");
        var choices = root.putArray("choices");
        var choice = choices.addObject();
        choice.put("index", 0);
        choice.put("finish_reason", "stop");
        var msg = choice.putObject("message");
        msg.put("role", "assistant");
        msg.put("content", "Hello from test!");
        var usage = root.putObject("usage");
        usage.put("prompt_tokens", 10);
        usage.put("completion_tokens", 5);
        usage.put("total_tokens", 15);
        return root.toString();
    }

    @Test
    void name() {
        assertEquals("openai", provider.name());
    }

    @Test
    void defaultModel() {
        assertTrue(provider.defaultModel().isPresent());
        assertEquals("openai", provider.defaultModel().get().providerId());
    }

    @Test
    void supportsModel() {
        assertTrue(provider.supportsModel("any-model"));
    }

    @Test
    void chatReturnsText() throws Exception {
        var request = ChatRequest.builder("You are helpful", List.of(Message.userText("hi")),
                ModelRef.of("openai", "test-model"))
            .stream(false).build();

        var future = provider.chat(request);
        var response = future.get(5, TimeUnit.SECONDS);

        assertEquals(ChatResponse.Type.TEXT, response.type());
        assertEquals("Hello from test!", response.text().orElse(""));
        assertEquals(15, response.usage().totalTokens());
    }

    @Test
    void chatSendsCorrectRequestBody() throws Exception {
        var request = ChatRequest.builder("You are helpful", List.of(Message.userText("hi")),
                ModelRef.of("openai", "test-model"))
            .stream(false).build();

        provider.chat(request).get(5, TimeUnit.SECONDS);

        assertNotNull(lastRequestBody);
        var body = mapper.readTree(lastRequestBody);
        assertEquals("test-model", body.get("model").asText());
        assertEquals("system", body.get("messages").get(0).get("role").asText());
        assertEquals("user", body.get("messages").get(1).get("role").asText());
        assertEquals("auto", body.get("tool_choice").asText());
    }

    @Test
    void chatReturnsToolCall() throws Exception {
        ObjectNode root = mapper.createObjectNode();
        root.put("id", "chatcmpl-456");
        root.put("object", "chat.completion");
        root.put("created", System.currentTimeMillis() / 1000);
        root.put("model", "test-model");
        var choices = root.putArray("choices");
        var choice = choices.addObject();
        choice.put("index", 0);
        choice.put("finish_reason", "tool_calls");
        var msg = choice.putObject("message");
        msg.put("role", "assistant");
        msg.putNull("content");
        var tcs = msg.putArray("tool_calls");
        var tc = tcs.addObject();
        tc.put("id", "call-1");
        tc.put("type", "function");
        var fn = tc.putObject("function");
        fn.put("name", "read");
        fn.put("arguments", "{\"filePath\":\"/tmp/test.txt\"}");
        var usage = root.putObject("usage");
        usage.put("prompt_tokens", 20);
        usage.put("completion_tokens", 10);
        usage.put("total_tokens", 30);
        responseBody = root.toString();

        var request = ChatRequest.builder("You are helpful", List.of(Message.userText("read a file")),
                ModelRef.of("openai", "test-model"))
            .stream(false).build();

        var response = provider.chat(request).get(5, TimeUnit.SECONDS);

        assertEquals(ChatResponse.Type.TOOL_CALL, response.type());
        assertEquals(1, response.toolCalls().size());
        var tcResponse = response.toolCalls().get(0);
        assertEquals("call-1", tcResponse.id());
        assertEquals("read", tcResponse.toolId());
        assertEquals("/tmp/test.txt", tcResponse.args().get("filePath").asText());
    }

    @Test
    void chatHandlesApiError() throws Exception {
        server.stop(0);
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.setExecutor(java.util.concurrent.Executors.newSingleThreadExecutor());
        server.createContext("/chat/completions", exchange -> {
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            var error = "{\"error\":{\"message\":\"Rate limit exceeded\",\"type\":\"rate_limit\"}}";
            exchange.sendResponseHeaders(429, error.getBytes(StandardCharsets.UTF_8).length);
            exchange.getResponseBody().write(error.getBytes(StandardCharsets.UTF_8));
            exchange.close();
        });
        server.start();
        // Need to update the baseUrl since port may have changed
        provider.configure("test-key", "http://localhost:" + server.getAddress().getPort());

        var request = ChatRequest.builder("test", List.of(Message.userText("hi")),
                ModelRef.of("openai", "test-model")).stream(false).build();

        var future = provider.chat(request);
        assertThrows(java.util.concurrent.ExecutionException.class, () -> future.get(5, TimeUnit.SECONDS));
    }

    @Test
    void missingApiKey() {
        var providerNoKey = new OpenAiProvider();
        providerNoKey.configure(null, null);
        var request = ChatRequest.builder("test", List.of(Message.userText("hi")),
                ModelRef.of("openai", "test-model")).stream(false).build();
        var future = providerNoKey.chat(request);
        assertThrows(java.util.concurrent.ExecutionException.class, () -> future.get(5, TimeUnit.SECONDS));
    }
}
