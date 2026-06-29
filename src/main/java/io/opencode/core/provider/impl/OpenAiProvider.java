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
 * OpenAiProvider —— OpenAI API 的提供商实现
 * 支持 GPT-4o 等模型的非流式和流式对话，以及工具调用（function calling）
 * 实现 Provider 和 ConfigurableProvider 接口
 */
@Component
public class OpenAiProvider implements Provider, ConfigurableProvider {

    private static final String DEFAULT_BASE_URL = "https://api.openai.com/v1";
    private static final String DEFAULT_MODEL = "gpt-4o";

    private final HttpClient httpClient;
    private final ObjectMapper mapper;
    private String apiKey;
    private String baseUrl;

    /** 初始化 HTTP 客户端和 Jackson 映射器，优先从环境变量读取凭证 */
    public OpenAiProvider() {
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();
        this.mapper = new ObjectMapper();
        this.apiKey = System.getenv("OPENAI_API_KEY");
        this.baseUrl = System.getenv("OPENAI_BASE_URL") != null
            ? System.getenv("OPENAI_BASE_URL") : DEFAULT_BASE_URL;
    }

    /** 动态注入 API Key 和 Base URL，非空时才覆盖原有值 */
    @Override
    public void configure(String apiKey, String baseUrl) {
        if (apiKey != null && !apiKey.isBlank()) this.apiKey = apiKey;
        if (baseUrl != null && !baseUrl.isBlank()) this.baseUrl = baseUrl;
    }

    @Override
    public String name() { return "openai"; }

    /**
     * 发送非流式聊天请求
     * 1. 校验 API Key 是否存在
     * 2. 构建请求体并发送 POST 请求到 /chat/completions
     * 3. 对 429/5xx 错误进行指数退避重试
     * 4. 解析响应中的 choices[0].message 并返回 ChatResponse
     */
    @Override
    public CompletableFuture<ChatResponse> chat(ChatRequest request) {
        if (apiKey == null || apiKey.isBlank()) {
            return CompletableFuture.failedFuture(
                new RuntimeException("OPENAI_API_KEY environment variable not set"));
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

                // 带指数退避的重试逻辑，最多重试 2 次
                var response = Retry.withBackoff(() -> {
                    try {
                        var r = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
                        if (r.statusCode() == 429 || r.statusCode() >= 500) {
                            throw new RuntimeException("HTTP " + r.statusCode());
                        }
                        return r;
                    } catch (RuntimeException e) {
                        throw e;
                    } catch (Exception e) {
                        throw new RuntimeException(e.getMessage());
                    }
                }, 2);

                var root = mapper.readTree(response.body());
                if (response.statusCode() != 200) {
                    var error = root.has("error") ? root.get("error").toString() : response.body();
                    throw new RuntimeException("OpenAI API error (" + response.statusCode() + "): " + error);
                }

                return parseChatResponse(root);
            } catch (Exception e) {
                throw new RuntimeException("OpenAI chat failed: " + e.getMessage(), e);
            }
        });
    }

    /**
     * 发起流式聊天请求
     * 通过 SSE (Server-Sent Events) 逐行读取响应流
     * 处理文本增量、工具调用增量（按 index 累积）和最终用量
     */
    @Override
    public CompletableFuture<Void> chatStream(ChatRequest request, StreamObserver<ChatChunk> observer) {
        return CompletableFuture.runAsync(() -> {
            try {
                var body = buildRequestBody(request);
                var jsonBody = mapper.readTree(body);
                ((ObjectNode) jsonBody).put("stream", true);  // 强制启用流式模式
                body = mapper.writeValueAsString(jsonBody);

                var httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/chat/completions"))
                    .timeout(Duration.ofSeconds(120))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

                // 按行读取 SSE 流
                var response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofLines());
                var usage = ChatResponse.Usage.EMPTY;
                // 按 index 累积工具调用增量数据（工具调用参数可能跨多个块）
                var toolCallAccumulators = new java.util.HashMap<Integer, ToolCallAccumulator>();

                response.body().forEach(line -> {
                    if (!line.startsWith("data: ")) return;
                    var data = line.substring(6);
                    if ("[DONE]".equals(data)) {
                        observer.onComplete();
                        return;
                    }
                    try {
                        var chunk = mapper.readTree(data);
                        var choices = chunk.get("choices");
                        if (choices == null || choices.isEmpty()) return;

                        var delta = choices.get(0).get("delta");
                        if (delta == null) return;

                        // 处理文本内容增量
                        if (delta.has("content") && !delta.get("content").isNull()) {
                            observer.onNext(ChatChunk.text(delta.get("content").asText()));
                        }

                        // 处理工具调用增量（累积各 index 的 id、name 和 arguments 片段）
                        if (delta.has("tool_calls")) {
                            for (var tc : delta.get("tool_calls")) {
                                var idx = tc.get("index").asInt();
                                var acc = toolCallAccumulators.computeIfAbsent(idx, k -> new ToolCallAccumulator());
                                if (tc.has("id") && !tc.get("id").isNull()) {
                                    acc.id = tc.get("id").asText();
                                }
                                var fn = tc.get("function");
                                if (fn != null) {
                                    if (fn.has("name") && !fn.get("name").isNull()) {
                                        acc.toolId = fn.get("name").asText();
                                    }
                                    if (fn.has("arguments") && !fn.get("arguments").isNull()) {
                                        acc.argsBuilder.append(fn.get("arguments").asText());
                                    }
                                }
                            }
                        }

                        // 处理结束原因：当 finish_reason 存在时触发
                        if (choices.get(0).has("finish_reason") && !choices.get(0).get("finish_reason").isNull()) {
                            var reason = choices.get(0).get("finish_reason").asText();

                            // 读取最终用量信息
                            var usageNode = chunk.get("usage");
                            if (usageNode != null && !usageNode.isNull()) {
                                var finalUsage = new ChatResponse.Usage(
                                    usageNode.get("prompt_tokens").asInt(),
                                    usageNode.get("completion_tokens").asInt(),
                                    usageNode.get("total_tokens").asInt()
                                );
                                observer.onNext(ChatChunk.usage(finalUsage));
                            }

                            // 如果结束原因是工具调用，发出累积的工具调用块
                            if ("tool_calls".equals(reason)) {
                                for (var acc : toolCallAccumulators.values()) {
                                    observer.onNext(ChatChunk.toolCall(acc.id, acc.toolId, acc.argsBuilder.toString()));
                                }
                            }
                            observer.onComplete();
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
        return Optional.of(ModelRef.of("openai", DEFAULT_MODEL));
    }

    @Override
    public boolean supportsModel(String modelId) {
        return true;  // OpenAI 支持所有已发布的模型 ID
    }

    /** 构建 OpenAI 格式的 JSON 请求体 */
    private String buildRequestBody(ChatRequest request) {
        var root = mapper.createObjectNode();
        root.put("model", request.model().modelId());
        root.set("messages", buildMessages(request));
        root.put("tool_choice", "auto");  // 允许模型自动选择是否调用工具

        if (request.temperature().isPresent()) root.put("temperature", request.temperature().get());
        if (request.topP().isPresent()) root.put("top_p", request.topP().get());
        if (request.maxTokens().isPresent()) root.put("max_tokens", request.maxTokens().get());

        // 添加工具定义
        if (!request.tools().isEmpty()) {
            root.set("tools", buildToolDefinitions(request.tools()));
        }

        return root.toPrettyString();
    }

    /**
     * 构建消息数组
     * 支持 TextMessage、ToolCallMessage、ToolResultMessage 和 FileMessage
     * 其中 FileMessage 如果是图片且包含 Base64 内容，则转为 image_url 格式
     */
    private ArrayNode buildMessages(ChatRequest request) {
        var messages = mapper.createArrayNode();

        // 添加系统提示词
        var sys = messages.addObject();
        sys.put("role", "system");
        sys.put("content", request.systemPrompt());

        for (var msg : request.messages()) {
            if (msg instanceof Message.TextMessage t) {
                var m = messages.addObject();
                m.put("role", t.role());
                m.put("content", t.text());
            } else if (msg instanceof Message.ToolCallMessage t) {
                // AI 发起的工具调用消息
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
                // 工具执行结果反馈
                var m = messages.addObject();
                m.put("role", "tool");
                m.put("tool_call_id", t.callId());
                m.put("content", t.output());
            } else if (msg instanceof Message.FileMessage t) {
                var m = messages.addObject();
                m.put("role", t.role());
                var contentArray = m.putArray("content");
                var file = t.file();
                // 如果是图片且内容可用，使用 image_url 格式内联显示
                if (file.mediaType().isPresent() && ImageUtils.isImage(file.mediaType().get()) && file.content().isPresent()) {
                    var imgItem = contentArray.addObject();
                    imgItem.put("type", "image_url");
                    var url = imgItem.putObject("image_url");
                    url.put("url", "data:" + file.mediaType().get() + ";base64," + file.content().get());
                } else {
                    // 非图片文件或内容不可用时，用文本占位描述
                    var item = contentArray.addObject();
                    item.put("type", "text");
                    var label = file.content().isPresent()
                        ? "[File: " + file.path() + "]"
                        : "[File: " + file.path() + " (content not available)]";
                    item.put("text", label);
                }
            }
        }

        return messages;
    }

    /** 构建 OpenAI 格式的工具定义 JSON 数组 */
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

    /** 解析 OpenAI 的非流式响应，提取首个 choice 中的文本或工具调用 */
    private ChatResponse parseChatResponse(JsonNode root) {
        var choice = root.get("choices").get(0);
        var message = choice.get("message");

        // 读取 Token 用量
        var usageNode = root.get("usage");
        var usage = usageNode != null ? new ChatResponse.Usage(
            usageNode.get("prompt_tokens").asInt(),
            usageNode.get("completion_tokens").asInt(),
            usageNode.get("total_tokens").asInt()
        ) : ChatResponse.Usage.EMPTY;

        // 优先检查工具调用
        var toolCalls = message.get("tool_calls");
        if (toolCalls != null && !toolCalls.isEmpty()) {
            var tc = toolCalls.get(0);
            var callId = tc.get("id").asText();
            var fn = tc.get("function");
            var toolId = fn.get("name").asText();
            JsonNode args;
            try {
                args = mapper.readTree(fn.get("arguments").asText());
            } catch (Exception e) {
                args = mapper.createObjectNode();
            }
            return ChatResponse.toolCall(new ChatResponse.ToolCall(callId, toolId, args), usage);
        }

        // 检查文本回复
        var content = message.get("content");
        if (content != null && !content.isNull()) {
            return ChatResponse.text(content.asText(), usage);
        }

        return ChatResponse.text("", usage);
    }

    /** 工具调用累积器 —— 用于在流式响应中拼接跨多个块的工具调用数据 */
    private static class ToolCallAccumulator {
        String id = "";
        String toolId = "";
        StringBuilder argsBuilder = new StringBuilder();
    }
}
