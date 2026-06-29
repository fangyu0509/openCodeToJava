package io.opencode.core.provider;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Provider 接口 —— 所有 AI 模型提供商的统一抽象
 * 定义了与 LLM 交互的核心方法：单次对话、流式对话、默认模型及模型支持检查
 */
public interface Provider {
    /** 返回提供商唯一标识名称，例如 "openai"、"anthropic" */
    String name();

    /** 发送聊天请求，返回包含完整回复的 ChatResponse（非流式调用） */
    CompletableFuture<ChatResponse> chat(ChatRequest request);

    /** 发起流式聊天请求，通过 observer 逐块接收 ChatChunk 数据 */
    CompletableFuture<Void> chatStream(ChatRequest request, StreamObserver<ChatChunk> observer);

    /** 返回此提供商建议的默认模型引用（providerId + modelId） */
    Optional<ModelRef> defaultModel();

    /** 判断当前提供商是否支持指定的 modelId */
    boolean supportsModel(String modelId);
}
