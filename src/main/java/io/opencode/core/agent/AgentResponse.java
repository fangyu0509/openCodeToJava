package io.opencode.core.agent;

import java.util.Optional;

/**
 * 代理处理结果记录，包含响应类型、文本内容、工具调用信息和已使用的步数。
 */
public record AgentResponse(
    Type type,                // 响应类型（TEXT/TOOL_CALL/ERROR/MAX_STEPS）
    Optional<String> text,    // 文本响应内容
    Optional<ToolCall> toolCall, // 工具调用信息
    int stepsUsed             // 已使用的步数
) {
    // 响应类型枚举
    public enum Type { TEXT, TOOL_CALL, ERROR, MAX_STEPS }

    // 工具调用记录，包含工具ID、调用ID和JSON参数
    public record ToolCall(
        String toolId,
        String callId,
        com.fasterxml.jackson.databind.JsonNode args
    ) {}

    // 创建文本响应
    public static AgentResponse text(String text, int stepsUsed) {
        return new AgentResponse(Type.TEXT, Optional.of(text), Optional.empty(), stepsUsed);
    }

    // 创建工具调用响应
    public static AgentResponse toolCall(ToolCall call, int stepsUsed) {
        return new AgentResponse(Type.TOOL_CALL, Optional.empty(), Optional.of(call), stepsUsed);
    }

    // 创建错误响应
    public static AgentResponse error(String message) {
        return new AgentResponse(Type.ERROR, Optional.of(message), Optional.empty(), 0);
    }

    // 创建达到最大步数的响应
    public static AgentResponse maxSteps(int steps) {
        return new AgentResponse(Type.MAX_STEPS, Optional.of("Max steps (" + steps + ") reached"), Optional.empty(), steps);
    }
}
