package io.opencode.core.provider;

import io.opencode.core.session.Message;

import java.util.List;
import java.util.Optional;

/**
 * ChatRequest —— LLM 聊天请求的不可变数据载体
 * 包含系统提示词、消息历史、目标模型、推理参数以及工具定义
 * 使用 Builder 模式构造实例
 */
public record ChatRequest(
    String systemPrompt,           // 系统提示词，设定 AI 的角色和行为
    List<Message> messages,        // 对话消息列表（用户、助手、工具等）
    ModelRef model,                // 目标模型引用
    Optional<Double> temperature,  // 采样温度，控制输出的随机性 (0~2)
    Optional<Double> topP,         // top-p 核采样参数
    Optional<Integer> maxTokens,   // 最大生成 Token 数限制
    List<ToolDefinition> tools,    // 可用的工具定义列表
    boolean stream                 // 是否采用流式响应
) {
    /**
     * ToolDefinition —— 工具（函数调用）定义
     * 描述一个可供 AI 调用的外部函数，包含 ID、描述和 JSON Schema 参数
     */
    public record ToolDefinition(
        String id,                            // 工具唯一标识
        String description,                   // 工具功能描述，帮助 AI 理解何时调用
        com.fasterxml.jackson.databind.JsonNode jsonSchema  // 参数的 JSON Schema 定义
    ) {}

    /** 创建 Builder 实例的入口方法 */
    public static Builder builder(String systemPrompt, List<Message> messages, ModelRef model) {
        return new Builder(systemPrompt, messages, model);
    }

    /** Builder 模式，用于构造 ChatRequest 实例 */
    public static class Builder {
        private final String systemPrompt;
        private final List<Message> messages;
        private final ModelRef model;
        private Optional<Double> temperature = Optional.empty();
        private Optional<Double> topP = Optional.empty();
        private Optional<Integer> maxTokens = Optional.empty();
        private List<ToolDefinition> tools = List.of();
        private boolean stream = true;  // 默认启用流式输出

        Builder(String systemPrompt, List<Message> messages, ModelRef model) {
            this.systemPrompt = systemPrompt;
            this.messages = messages;
            this.model = model;
        }

        public Builder temperature(Double v) { this.temperature = Optional.ofNullable(v); return this; }
        public Builder topP(Double v) { this.topP = Optional.ofNullable(v); return this; }
        public Builder maxTokens(Integer v) { this.maxTokens = Optional.ofNullable(v); return this; }
        public Builder tools(List<ToolDefinition> v) { this.tools = v; return this; }
        public Builder stream(boolean v) { this.stream = v; return this; }

        /** 构建不可变的 ChatRequest 实例，消息和工具列表会被防御性复制 */
        public ChatRequest build() {
            return new ChatRequest(systemPrompt, List.copyOf(messages), model, temperature, topP, maxTokens, List.copyOf(tools), stream);
        }
    }
}
