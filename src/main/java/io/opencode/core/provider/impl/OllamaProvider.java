package io.opencode.core.provider.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.opencode.core.provider.*;
import io.opencode.core.session.Message;
import io.opencode.core.util.Retry;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * OllamaProvider —— 本地 Ollama 服务的提供商实现
 * 默认连接 localhost:11434，支持本地 LLM 模型的非流式和流式对话
 * Ollama 不需要 API Key 认证
 */
@Component
public class OllamaProvider implements Provider, ConfigurableProvider {

    private static final String DEFAULT_BASE_URL = "http://localhost:11434";
    private static final String DEFAULT_MODEL = "llama3";

    private final HttpClient httpClient;
    private final ObjectMapper mapper;
    private String baseUrl;

    /** 从环境变量或默认值初始化 Base URL */
    public OllamaProvider() {
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();
        this.mapper = new ObjectMapper();
        this.baseUrl = System.getenv("OLLAMA_BASE_URL") != null
            ? System.getenv("OLLAMA_BASE_URL") : DEFAULT_BASE_URL;
    }

    /** 允许在测试中直接指定 Base URL */
    OllamaProvider(String baseUrl) {
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();
        this.mapper = new ObjectMapper();
        this.baseUrl = baseUrl;
    }

    /** Ollama 不需要 API Key，仅可配置 Base URL */
    @Override
    public void configure(String apiKey, String baseUrl) {
        if (baseUrl != null && !baseUrl.isBlank()) this.baseUrl = baseUrl;
    }

    @Override
    public String name() { return "ollama"; }

    /**
     * 非流式聊天请求
     * 发送到 /api/chat 端点，无认证头
     */
    @Override
    public CompletableFuture<ChatResponse> chat(ChatRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                var body = buildRequestBody(request);
                var httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/chat"))
                    .timeout(Duration.ofSeconds(120))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

                var response = Retry.withBackoff(() -> {
                    try {
                        var r = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
                        if (r.statusCode() == 429 || r.statusCode() >= 500) throw new RuntimeException("HTTP " + r.statusCode());
                        return r;
                    } catch (RuntimeException e) { throw e; }
                    catch (Exception e) { throw new RuntimeException(e.getMessage()); }
                }, 2);
                var root = mapper.readTree(response.body());

                if (response.statusCode() != 200) {
                    throw new RuntimeException("Ollama API error (" + response.statusCode() + "): " + response.body());
                }

                return parseChatResponse(root);
            } catch (Exception e) {
                throw new RuntimeException("Ollama chat failed: " + e.getMessage(), e);
            }
        });
    }

    /**
     * 流式聊天请求
     * Ollama 的流式格式每行一个完整 JSON 对象
     * 通过 "done" 字段标记流结束，并在最后一块提供用量信息
     */
    @Override
    public CompletableFuture<Void> chatStream(ChatRequest request, StreamObserver<ChatChunk> observer) {
        return CompletableFuture.runAsync(() -> {
            try {
                var jsonBody = mapper.readTree(buildRequestBody(request));
                ((ObjectNode) jsonBody).put("stream", true);
                var body = mapper.writeValueAsString(jsonBody);

                var httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/chat"))
                    .timeout(Duration.ofSeconds(120))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

                var response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofLines());

                response.body().forEach(line -> {
                    try {
                        var chunk = mapper.readTree(line);
                        // done 字段为 true 时表示流结束
                        if (chunk.has("done") && chunk.get("done").asBoolean()) {
                            var usageNode = chunk.get("usage");
                            if (usageNode != null) {
                                var usage = new ChatResponse.Usage(
                                    usageNode.get("prompt_tokens").asInt(0),
                                    usageNode.get("completion_tokens").asInt(0),
                                    usageNode.get("total_tokens").asInt(0)
                                );
                                observer.onNext(ChatChunk.usage(usage));
                            }
                            observer.onComplete();
                            return;
                        }
                        // 提取文本增量
                        var msg = chunk.get("message");
                        if (msg != null && msg.has("content") && !msg.get("content").isNull()) {
                            observer.onNext(ChatChunk.text(msg.get("content").asText()));
                        }
                        // 提取工具调用（Ollama 的 tool_calls 格式与 OpenAI 类似）
                        if (msg != null && msg.has("tool_calls")) {
                            for (var tc : msg.get("tool_calls")) {
                                var fn = tc.get("function");
                                if (fn != null) {
                                    observer.onNext(ChatChunk.toolCall(
                                        tc.has("id") ? tc.get("id").asText() : "",
                                        fn.get("name").asText(),
                                        fn.get("arguments").toString()
                                    ));
                                }
                            }
                        }
                    } catch (Exception e) {
                        observer.onError(e);
                    }
                });
            } catch (Exception e) {
                observer.onError(e);
            }
        });
    }

    @Override
    public Optional<ModelRef> defaultModel() {
        return Optional.of(ModelRef.of("ollama", DEFAULT_MODEL));
    }

    @Override
    public boolean supportsModel(String modelId) {
        return true;
    }

    /** 构建 Ollama 格式的请求体，结构类似 OpenAI */
    private String buildRequestBody(ChatRequest request) {
        var root = mapper.createObjectNode();
        root.put("model", request.model().modelId());
        root.set("messages", buildMessages(request));

        if (!request.tools().isEmpty()) {
            root.set("tools", buildToolDefinitions(request.tools()));
        }

        if (request.temperature().isPresent()) root.put("temperature", request.temperature().get());
        if (request.maxTokens().isPresent()) root.put("max_tokens", request.maxTokens().get());

        return root.toPrettyString();
    }

    /** 构建消息数组，格式与 OpenAI 兼容 */
    private ArrayNode buildMessages(ChatRequest request) {
        var messages = mapper.createArrayNode();

        var sys = messages.addObject();
        sys.put("role", "system");
        sys.put("content", request.systemPrompt());

        for (var msg : request.messages()) {
            if (msg instanceof Message.TextMessage t) {
                var m = messages.addObject();
                m.put("role", t.role());
                m.put("content", t.text());
            } else if (msg instanceof Message.ToolCallMessage t) {
                var m = messages.addObject();
                m.put("role", "assistant");
                m.putNull("content");
                var tcs = m.putArray("tool_calls");
                var tc = tcs.addObject();
                tc.put("id", t.callId());
                tc.put("type", "function");
                var fn = tc.putObject("function");
                fn.put("name", t.toolId());
                fn.put("arguments", t.args().toString());
            } else if (msg instanceof Message.ToolResultMessage t) {
                var m = messages.addObject();
                m.put("role", "tool");
                m.put("content", t.output());
            } else if (msg instanceof Message.FileMessage t) {
                var m = messages.addObject();
                m.put("role", t.role());
                var content = m.putArray("content");
                var item = content.addObject();
                item.put("type", "text");
                item.put("text", "[File: " + t.file().path() + "]");
            }
        }

        return messages;
    }

    /** 构建工具定义数组，与 OpenAI 格式一致 */
    private ArrayNode buildToolDefinitions(java.util.List<ChatRequest.ToolDefinition> tools) {
        var arr = mapper.createArrayNode();
        for (var t : tools) {
            var tool = arr.addObject();
            tool.put("type", "function");
            var fn = tool.putObject("function");
            fn.put("name", t.id());
            fn.put("description", t.description() != null ? t.description() : "");
            fn.set("parameters", t.jsonSchema());
        }
        return arr;
    }

    /** 解析 Ollama 响应，提取文本或工具调用 */
    private ChatResponse parseChatResponse(JsonNode root) {
        var message = root.get("message");
        if (message == null) {
            return ChatResponse.text("", ChatResponse.Usage.EMPTY);
        }

        var toolCalls = message.get("tool_calls");
        if (toolCalls != null && !toolCalls.isEmpty()) {
            var tc = toolCalls.get(0);
            var fn = tc.get("function");
            var toolId = fn.get("name").asText();
            var callId = tc.has("id") ? tc.get("id").asText() : toolId;

            JsonNode args;
            var argsNode = fn.get("arguments");
            if (argsNode.isTextual()) {
                try {
                    args = mapper.readTree(argsNode.asText());
                } catch (Exception e) {
                    args = mapper.createObjectNode();
                }
            } else {
                args = argsNode;
            }
            return ChatResponse.toolCall(new ChatResponse.ToolCall(callId, toolId, args), ChatResponse.Usage.EMPTY);
        }

        var content = message.get("content");
        if (content != null && !content.isNull()) {
            return ChatResponse.text(content.asText(), ChatResponse.Usage.EMPTY);
        }

        return ChatResponse.text("", ChatResponse.Usage.EMPTY);
    }
}
