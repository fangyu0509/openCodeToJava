package io.opencode.core.provider;

import java.util.Optional;

/**
 * ChatChunk —— 流式聊天响应中的单个数据块
 * 表示一次 SSE 事件中的增量数据，可能包含文本增量、工具调用增量或用量信息
 */
public record ChatChunk(
    Optional<String> textDelta,                // 文本内容增量（逐字逐词到达）
    Optional<ToolCallDelta> toolCallDelta,     // 工具调用增量（分块累积）
    Optional<ChatResponse.Usage> usage         // 最终 Token 用量（通常在流结束时提供）
) {
    /**
     * ToolCallDelta —— 工具调用的增量数据
     * 由于工具调用的参数可能跨多个 Chunk 传输，需要累积拼接
     */
    public record ToolCallDelta(
        String id,              // 工具调用 ID
        String toolId,          // 工具名称
        String argsJsonDelta    // 参数 JSON 的增量片段
    ) {}

    /** 创建纯文本增量块 */
    public static ChatChunk text(String delta) {
        return new ChatChunk(Optional.of(delta), Optional.empty(), Optional.empty());
    }

    /** 创建工具调用增量块 */
    public static ChatChunk toolCall(String id, String toolId, String argsDelta) {
        return new ChatChunk(Optional.empty(),
            Optional.of(new ToolCallDelta(id, toolId, argsDelta)), Optional.empty());
    }

    /** 创建包含 Token 用量信息的块（通常在流结束时发出） */
    public static ChatChunk usage(ChatResponse.Usage usage) {
        return new ChatChunk(Optional.empty(), Optional.empty(), Optional.of(usage));
    }

    /** 创建流结束信号块（所有字段均为空） */
    public static ChatChunk done() {
        return new ChatChunk(Optional.empty(), Optional.empty(), Optional.empty());
    }
}
