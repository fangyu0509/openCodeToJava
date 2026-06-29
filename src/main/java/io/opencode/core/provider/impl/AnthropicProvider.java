package io.opencode.core.provider.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.opencode.core.provider.*;
import io.opencode.core.session.Message;
import io.opencode.core.util.ImageUtils;
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
 * AnthropicProvider —— Anthropic Claude API 的提供商实现
 * 支持 Claude Sonnet 等模型的对话与流式响应
 * API 格式与 OpenAI 不同，使用 /v1/messages 端点
 */
@Component
public class AnthropicProvider implements Provider, ConfigurableProvider {

    private static final String DEFAULT_BASE_URL = "https://api.anthropic.com";
    private static final String DEFAULT_MODEL = "claude-sonnet-4-20250514";
    private static final String API_VERSION = "2023-06-01";  // Anthropic API 版本号

    private final HttpClient httpClient;
    private final ObjectMapper mapper;
    private String apiKey;
    private String baseUrl;

    /** 初始化 HTTP 客户端，优先从环境变量读取 API Key 和 Base URL */
    public AnthropicProvider() {
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();
        this.mapper = new ObjectMapper();
        this.apiKey = System.getenv("ANTHROPIC_API_KEY");
        this.baseUrl = System.getenv("ANTHROPIC_BASE_URL") != null
            ? System.getenv("ANTHROPIC_BASE_URL") : DEFAULT_BASE_URL;
    }

    /** 动态注入配置，非空值才覆盖原有字段 */
    @Override
    public void configure(String apiKey, String baseUrl) {
        if (apiKey != null && !apiKey.isBlank()) this.apiKey = apiKey;
        if (baseUrl != null && !baseUrl.isBlank()) this.baseUrl = baseUrl;
    }

    @Override
    public String name() { return "anthropic"; }

    /**
     * 非流式聊天请求
     * 使用 x-api-key 头进行认证，请求 /v1/messages 端点
     * 支持 429/5xx 重试
     */
    @Override
    public CompletableFuture<ChatResponse> chat(ChatRequest request) {
        if (apiKey == null || apiKey.isBlank()) {
            return CompletableFuture.failedFuture(
                new RuntimeException("ANTHROPIC_API_KEY environment variable not set"));
        }
        return CompletableFuture.supplyAsync(() -> {
            try {
                var body = buildRequestBody(request);
                var httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/v1/messages"))
                    .timeout(Duration.ofSeconds(120))
                    .header("Content-Type", "application/json")
                    .header("x-api-key", apiKey)
                    .header("anthropic-version", API_VERSION)
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

                // 使用指数退避重试
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
                    var err = root.has("error") ? root.get("error").toString() : response.body();
                    throw new RuntimeException("Anthropic API error (" + response.statusCode() + "): " + err);
                }

                return parseChatResponse(root);
            } catch (Exception e) {
                throw new RuntimeException("Anthropic chat failed: " + e.getMessage(), e);
            }
        });
    }

    /**
     * 流式聊天请求
     * Anthropic 的 SSE 事件类型包括：
     * - content_block_start: 内容块开始（含工具调用起始信息）
     * - content_block_delta: 内容增量（文本增量）
     * - content_block_stop: 内容块结束
     * - message_delta: 消息增量（含停止原因和用量）
     * - message_stop: 消息结束
     * - ping: 心跳保活
     */
    @Override
    public CompletableFuture<Void> chatStream(ChatRequest request, StreamObserver<ChatChunk> observer) {
        if (apiKey == null || apiKey.isBlank()) {
            return CompletableFuture.failedFuture(
                new RuntimeException("ANTHROPIC_API_KEY environment variable not set"));
        }
        return CompletableFuture.runAsync(() -> {
            try {
                var jsonBody = mapper.readTree(buildRequestBody(request));
                ((ObjectNode) jsonBody).put("stream", true);
                var body = mapper.writeValueAsString(jsonBody);

                var httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/v1/messages"))
                    .timeout(Duration.ofSeconds(120))
                    .header("Content-Type", "application/json")
                    .header("x-api-key", apiKey)
                    .header("anthropic-version", API_VERSION)
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

                var response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofLines());
                var usage = ChatResponse.Usage.EMPTY;
                // 按 index 字符串累积每个工具调用的数据
                var toolCallAccumulators = new java.util.HashMap<String, ToolCallAccumulator>();

                response.body().forEach(line -> {
                    if (!line.startsWith("data: ")) return;
                    var data = line.substring(6);
                    if ("[DONE]".equals(data)) {
                        observer.onComplete();
                        return;
                    }
                    try {
                        var event = mapper.readTree(data);
                        var type = event.get("type").asText();

                        switch (type) {
                            case "content_block_delta" -> {
                                // 文本增量：累积文本片段
                                var delta = event.get("delta");
                                if (delta != null && delta.has("type") && "text_delta".equals(delta.get("type").asText())) {
                                    observer.onNext(ChatChunk.text(delta.get("text").asText()));
                                }
                            }
                            case "content_block_start" -> {
                                // 内容块开始：如果是 tool_use 类型，记录工具调用的 id 和 name
                                var block = event.get("content_block");
                                if (block != null && "tool_use".equals(block.get("type").asText())) {
                                    var idx = event.get("index").asText();
                                    var acc = new ToolCallAccumulator();
                                    acc.id = block.get("id").asText();
                                    acc.toolId = block.get("name").asText();
                                    var input = block.get("input");
                                    if (input != null) acc.argsBuilder.append(input.toString());
                                    toolCallAccumulators.put(idx, acc);
                                }
                            }
                            case "content_block_stop" -> {
                                // 内容块停止：不需要特殊处理，在 message_delta 中发出工具调用结果
                            }
                            case "message_delta" -> {
                                // 消息增量：处理停止原因和最终用量
                                var delta = event.get("delta");
                                if (delta != null && delta.has("stop_reason")) {
                                    var reason = delta.get("stop_reason").asText();
                                    if ("tool_use".equals(reason)) {
                                        // 结束原因为工具调用时，发出累积的工具调用
                                        for (var acc : toolCallAccumulators.values()) {
                                            observer.onNext(ChatChunk.toolCall(
                                                acc.id, acc.toolId, acc.argsBuilder.toString()));
                                        }
                                    }
                                }
                                // 读取输入/输出 Token 用量
                                var usageNode = event.get("usage");
                                if (usageNode != null) {
                                    var finalUsage = new ChatResponse.Usage(
                                        usageNode.get("input_tokens").asInt(),
                                        usageNode.get("output_tokens").asInt(),
                                        usageNode.get("input_tokens").asInt() + usageNode.get("output_tokens").asInt()
                                    );
                                    observer.onNext(ChatChunk.usage(finalUsage));
                                }
                            }
                            case "message_stop" -> {
                                // 消息停止：已在 message_delta 中处理完成
                            }
                            case "ping" -> {}
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
        return Optional.of(ModelRef.of("anthropic", DEFAULT_MODEL));
    }

    @Override
    public boolean supportsModel(String modelId) {
        return true;
    }

    /** 构建 Anthropic 格式的请求体 */
    private String buildRequestBody(ChatRequest request) {
        var root = mapper.createObjectNode();
        root.put("model", request.model().modelId());
        root.put("max_tokens", 4096);  // Anthropic 需要显式设置 max_tokens

        // Anthropic 的 system prompt 使用顶层字段而非消息列表
        if (request.systemPrompt() != null && !request.systemPrompt().isBlank()) {
            root.put("system", request.systemPrompt());
        }

        root.set("messages", buildMessages(request));

        if (!request.tools().isEmpty()) {
            root.set("tools", buildToolDefinitions(request.tools()));
        }

        return root.toPrettyString();
    }

    /**
     * 构建 Anthropic 格式的消息数组
     * 注意：assistant 消息需要使用 content 数组格式
     * tool_result 消息需要合并到前一个 user 消息的 content 数组中
     */
    private ArrayNode buildMessages(ChatRequest request) {
        var messages = mapper.createArrayNode();

        for (var msg : request.messages()) {
            if (msg instanceof Message.TextMessage t) {
                if ("assistant".equals(t.role())) {
                    // Anthropic 的 assistant 消息需要 content 数组格式
                    var m = messages.addObject();
                    m.put("role", "assistant");
                    var content = m.putArray("content");
                    var item = content.addObject();
                    item.put("type", "text");
                    item.put("text", t.text());
                } else {
                    var m = messages.addObject();
                    m.put("role", t.role());
                    m.put("content", t.text());
                }
            } else if (msg instanceof Message.ToolCallMessage t) {
                // 工具调用消息
                var m = messages.addObject();
                m.put("role", "assistant");
                var content = m.putArray("content");
                var tc = content.addObject();
                tc.put("type", "tool_use");
                tc.put("id", t.callId());
                tc.put("name", t.toolId());
                tc.set("input", t.args());
            } else if (msg instanceof Message.ToolResultMessage t) {
                // 工具结果：尽量合并到上一个 user 消息的 content 数组
                var existing = !messages.isEmpty() ? messages.get(messages.size() - 1) : null;
                if (existing != null && "user".equals(existing.get("role").asText())) {
                    var content = (ArrayNode) existing.withArray("content");
                    var tr = content.addObject();
                    tr.put("type", "tool_result");
                    tr.put("tool_use_id", t.callId());
                    tr.put("content", t.output());
                } else {
                    var m = messages.addObject();
                    m.put("role", "user");
                    var content = m.putArray("content");
                    var tr = content.addObject();
                    tr.put("type", "tool_result");
                    tr.put("tool_use_id", t.callId());
                    tr.put("content", t.output());
                }
            } else if (msg instanceof Message.FileMessage t) {
                var m = messages.addObject();
                m.put("role", t.role());
                var file = t.file();
                // 图片文件使用 base64 内联格式
                if (file.mediaType().isPresent() && ImageUtils.isImage(file.mediaType().get()) && file.content().isPresent()) {
                    var content = m.putArray("content");
                    var imgItem = content.addObject();
                    imgItem.put("type", "image");
                    var source = imgItem.putObject("source");
                    source.put("type", "base64");
                    source.put("media_type", file.mediaType().get());
                    source.put("data", file.content().get());
                } else {
                    m.put("content", "[File: " + file.path() + "]");
                }
            }
        }

        return messages;
    }

    /** 构建 Anthropic 格式的工具定义（input_schema 而非 parameters） */
    private ArrayNode buildToolDefinitions(java.util.List<ChatRequest.ToolDefinition> tools) {
        var arr = mapper.createArrayNode();
        for (var t : tools) {
            var tool = arr.addObject();
            tool.put("name", t.id());
            tool.put("description", t.description() != null ? t.description() : "");
            // Anthropic 使用 input_schema 字段名
            tool.set("input_schema", t.jsonSchema());
        }
        return arr;
    }

    /** 解析 Anthropic 响应，提取首个文本块或工具调用 */
    private ChatResponse parseChatResponse(JsonNode root) {
        var content = root.get("content");
        var usageNode = root.get("usage");
        var usage = usageNode != null ? new ChatResponse.Usage(
            usageNode.get("input_tokens").asInt(),
            usageNode.get("output_tokens").asInt(),
            usageNode.get("input_tokens").asInt() + usageNode.get("output_tokens").asInt()
        ) : ChatResponse.Usage.EMPTY;

        if (content != null) {
            // 优先检查工具调用
            for (var block : content) {
                if ("tool_use".equals(block.get("type").asText())) {
                    return ChatResponse.toolCall(new ChatResponse.ToolCall(
                        block.get("id").asText(),
                        block.get("name").asText(),
                        block.get("input")
                    ), usage);
                }
            }

            // 如果没有工具调用，取文本块
            for (var block : content) {
                if ("text".equals(block.get("type").asText())) {
                    return ChatResponse.text(block.get("text").asText(), usage);
                }
            }
        }

        return ChatResponse.text("", usage);
    }

    /** 流式响应中累积工具调用数据的辅助类 */
    private static class ToolCallAccumulator {
        String id = "";
        String toolId = "";
        StringBuilder argsBuilder = new StringBuilder();
    }
}
