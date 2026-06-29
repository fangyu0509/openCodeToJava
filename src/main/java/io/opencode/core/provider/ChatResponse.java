package io.opencode.core.provider;

import io.opencode.core.session.FilePart;

import java.util.List;
import java.util.Optional;

/**
 * ChatResponse —— LLM 聊天响应的不可变数据载体
 * 支持两种响应类型：纯文本回复（TEXT）和工具调用（TOOL_CALL）
 */
public record ChatResponse(
    Type type,                  // 响应类型：TEXT 或 TOOL_CALL
    Optional<String> text,      // 文本回复内容（仅 TEXT 类型时存在）
    List<ToolCall> toolCalls,   // 工具调用列表（仅 TOOL_CALL 类型时存在）
    Usage usage                 // Token 用量统计
) {
    /** 响应类型枚举：文本回复 or 工具调用 */
    public enum Type { TEXT, TOOL_CALL }

    /**
     * ToolCall —— 工具调用记录
     * 表示 AI 决定调用某个外部工具及其参数
     */
    public record ToolCall(
        String id,                              // 调用唯一标识
        String toolId,                          // 被调用的工具名称
        com.fasterxml.jackson.databind.JsonNode args  // 调用参数（JSON 格式）
    ) {}

    /** Token 用量统计：提示词、补全和总 Token 数 */
    public record Usage(
        int promptTokens,       // 输入提示词消耗的 Token 数
        int completionTokens,   // 输出补全消耗的 Token 数
        int totalTokens         // 总 Token 消耗数
    ) {
        /** 空用量常量，用于无法获取用量信息时的默认值 */
        public static final Usage EMPTY = new Usage(0, 0, 0);
    }

    /** 工厂方法：创建一个纯文本类型的响应 */
    public static ChatResponse text(String text, Usage usage) {
        return new ChatResponse(Type.TEXT, Optional.of(text), List.of(), usage);
    }

    /** 工厂方法：创建一个包含多个工具调用的响应 */
    public static ChatResponse toolCalls(List<ToolCall> calls, Usage usage) {
        return new ChatResponse(Type.TOOL_CALL, Optional.empty(), calls, usage);
    }

    /** 工厂方法：创建一个包含单个工具调用的响应 */
    public static ChatResponse toolCall(ToolCall call, Usage usage) {
        return new ChatResponse(Type.TOOL_CALL, Optional.empty(), List.of(call), usage);
    }
}
