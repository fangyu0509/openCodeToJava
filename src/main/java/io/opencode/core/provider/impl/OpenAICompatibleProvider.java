package io.opencode.core.provider.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.opencode.core.provider.*;
import io.opencode.core.session.Message;
import io.opencode.core.util.Retry;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * OpenAICompatibleProvider —— 兼容 OpenAI API 格式的通用提供商
 * 用于对接 DeepSeek、Groq、Together AI 等第三方 API 服务
 * 通过构造函数动态设置提供商名称、API Key 和 Base URL
 */
public class OpenAICompatibleProvider implements Provider, ConfigurableProvider {

    private static final String DEFAULT_MODEL = "gpt-4o";

    private final HttpClient httpClient;
    private final ObjectMapper mapper;
    private final String providerName;   // 提供商名称，如 "deepseek"
    private String apiKey;
    private String baseUrl;
    private String defaultModel;

    public OpenAICompatibleProvider(String name, String apiKey, String baseUrl) {
        this.providerName = name;
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.defaultModel = DEFAULT_MODEL;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();
        this.mapper = new ObjectMapper();
    }

    @Override
    public void configure(String apiKey, String baseUrl) {
        if (apiKey != null && !apiKey.isBlank()) this.apiKey = apiKey;
        if (baseUrl != null && !baseUrl.isBlank()) this.baseUrl = baseUrl;
    }

    /** 设置自定义的默认模型（通常从配置文件读取） */
    public void setDefaultModel(String model) {
        if (model != null && !model.isBlank()) this.defaultModel = model;
    }

    @Override
    public String name() { return providerName; }

    /**
     * 非流式聊天请求
     * 完全兼容 OpenAI 的 /chat/completions 端点格式
     */
    @Override
    public CompletableFuture<ChatResponse> chat(ChatRequest request) {
        if (apiKey == null || apiKey.isBlank()) {
            return CompletableFuture.failedFuture(
                new RuntimeException(providerName + " API key not configured"));
        }
        return CompletableFuture.supplyAsync(() -> {
            try {
                var body = buildRequestBody(request);
                var httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/chat/completions"))
                    .timeout(Duration.ofSeconds(120))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

                var response = Retry.withBackoff(() -> {
                    try {
                        var r = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
                        if (r.statusCode() == 429 || r.statusCode() >= 500) {
                            throw new RuntimeException("HTTP " + r.statusCode());
                        }
                        return r;
                    } catch (RuntimeException e) { throw e; }
                    catch (Exception e) { throw new RuntimeException(e.getMessage()); }
                }, 2);

                var root = mapper.readTree(response.body());
                if (response.statusCode() != 200) {
                    var error = root.has("error") ? root.get("error").toString() : response.body();
                    throw new RuntimeException(providerName + " API error (" + response.statusCode() + "): " + error);
                }
                return parseChatResponse(root);
            } catch (Exception e) {
                throw new RuntimeException(providerName + " chat failed: " + e.getMessage(), e);
            }
        });
    }

    /**
     * 流式聊天请求
     * 实现与 OpenAiProvider 完全相同的 SSE 流处理逻辑
     * 包括文本增量、工具调用增量的累积和最终用量
     */
    @Override
    public CompletableFuture<Void> chatStream(ChatRequest request, StreamObserver<ChatChunk> observer) {
        return CompletableFuture.runAsync(() -> {
            try {
                var body = buildRequestBody(request);
                var jsonBody = mapper.readTree(body);
                ((ObjectNode) jsonBody).put("stream", true);
                body = mapper.writeValueAsString(jsonBody);

                var httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/chat/completions"))
                    .timeout(Duration.ofSeconds(120))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

                var response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofLines());
                var toolCallAccumulators = new java.util.HashMap<Integer, ToolCallAccumulator>();

                response.body().forEach(line -> {
                    if (!line.startsWith("data: ")) return;
                    var data = line.substring(6);
                    if ("[DONE]".equals(data)) { observer.onComplete(); return; }
                    try {
                        var chunk = mapper.readTree(data);
                        var choices = chunk.get("choices");
                        if (choices == null || choices.isEmpty()) return;
                        var delta = choices.get(0).get("delta");
                        if (delta == null) return;

                        if (delta.has("content") && !delta.get("content").isNull()) {
                            observer.onNext(ChatChunk.text(delta.get("content").asText()));
                        }
                        if (delta.has("tool_calls")) {
                            for (var tc : delta.get("tool_calls")) {
                                var idx = tc.get("index").asInt();
                                var acc = toolCallAccumulators.computeIfAbsent(idx, k -> new ToolCallAccumulator());
                                if (tc.has("id") && !tc.get("id").isNull()) acc.id = tc.get("id").asText();
                                var fn = tc.get("function");
                                if (fn != null) {
                                    if (fn.has("name") && !fn.get("name").isNull()) acc.toolId = fn.get("name").asText();
                                    if (fn.has("arguments") && !fn.get("arguments").isNull()) acc.argsBuilder.append(fn.get("arguments").asText());
                                }
                            }
                        }
                        if (choices.get(0).has("finish_reason") && !choices.get(0).get("finish_reason").isNull()) {
                            var reason = choices.get(0).get("finish_reason").asText();
                            var usageNode = chunk.get("usage");
                            if (usageNode != null && !usageNode.isNull()) {
                                observer.onNext(ChatChunk.usage(new ChatResponse.Usage(
                                    usageNode.get("prompt_tokens").asInt(),
                                    usageNode.get("completion_tokens").asInt(),
                                    usageNode.get("total_tokens").asInt())));
                            }
                            if ("tool_calls".equals(reason)) {
                                for (var acc : toolCallAccumulators.values()) {
                                    observer.onNext(ChatChunk.toolCall(acc.id, acc.toolId, acc.argsBuilder.toString()));
                                }
                            }
                            observer.onComplete();
                        }
                    } catch (Exception e) { observer.onError(e); }
                });
            } catch (Exception e) { observer.onError(e); }
        });
    }

    @Override
    public Optional<ModelRef> defaultModel() {
        return Optional.of(ModelRef.of(providerName, defaultModel));
    }

    @Override
    public boolean supportsModel(String modelId) { return true; }

    /** 构建与 OpenAI 兼容的请求体 */
    private String buildRequestBody(ChatRequest request) {
        var root = mapper.createObjectNode();
        root.put("model", request.model().modelId());
        root.set("messages", buildMessages(request));
        root.put("tool_choice", "auto");
        if (request.temperature().isPresent()) root.put("temperature", request.temperature().get());
        if (request.topP().isPresent()) root.put("top_p", request.topP().get());
        if (request.maxTokens().isPresent()) root.put("max_tokens", request.maxTokens().get());
        if (!request.tools().isEmpty()) root.set("tools", buildToolDefinitions(request.tools()));
        return root.toPrettyString();
    }

    /** 构建与 OpenAI 兼容的消息数组 */
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
                m.put("tool_call_id", t.callId());
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

    /** 构建与 OpenAI 兼容的工具定义数组 */
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

    /** 解析与 OpenAI 兼容格式的响应 */
    private ChatResponse parseChatResponse(JsonNode root) {
        var choice = root.get("choices").get(0);
        var message = choice.get("message");
        var usageNode = root.get("usage");
        var usage = usageNode != null ? new ChatResponse.Usage(
            usageNode.get("prompt_tokens").asInt(),
            usageNode.get("completion_tokens").asInt(),
            usageNode.get("total_tokens").asInt()
        ) : ChatResponse.Usage.EMPTY;

        var toolCalls = message.get("tool_calls");
        if (toolCalls != null && !toolCalls.isEmpty()) {
            var tc = toolCalls.get(0);
            var callId = tc.get("id").asText();
            var fn = tc.get("function");
            var toolId = fn.get("name").asText();
            JsonNode args;
            try { args = mapper.readTree(fn.get("arguments").asText()); }
            catch (Exception e) { args = mapper.createObjectNode(); }
            return ChatResponse.toolCall(new ChatResponse.ToolCall(callId, toolId, args), usage);
        }
        var content = message.get("content");
        if (content != null && !content.isNull()) return ChatResponse.text(content.asText(), usage);
        return ChatResponse.text("", usage);
    }

    /** 流式响应中累积工具调用数据的辅助类 */
    private static class ToolCallAccumulator {
        String id = ""; String toolId = ""; StringBuilder argsBuilder = new StringBuilder();
    }
}
