package io.opencode.core.session;

import io.opencode.core.model.MessageId;

import java.util.List;
import java.util.Optional;

// 消息密封接口 — 所有消息类型的统一定义，包含文本、工具调用、工具结果、文件四种具体类型
public sealed interface Message {
    // 获取消息唯一 ID
    MessageId id();
    // 获取消息角色（user / assistant / tool）
    String role();
    // 获取消息时间戳
    long timestamp();

    // 纯文本消息
    record TextMessage(
        MessageId id,
        String role,
        String text,
        long timestamp
    ) implements Message {}

    // 工具调用消息 — 记录调用的工具名称、调用 ID 和参数
    record ToolCallMessage(
        MessageId id,
        String role,
        String toolId,
        String callId,
        com.fasterxml.jackson.databind.JsonNode args,
        long timestamp
    ) implements Message {}

    // 工具执行结果消息 — 包含输出内容及可能附带的文件附件
    record ToolResultMessage(
        MessageId id,
        String role,
        String toolId,
        String callId,
        String output,
        List<FilePart> attachments,
        long timestamp
    ) implements Message {}

    // 文件消息 — 传递一个 FilePart 引用
    record FileMessage(
        MessageId id,
        String role,
        FilePart file,
        long timestamp
    ) implements Message {}

    // 快速创建用户文本消息的静态工厂
    static TextMessage userText(String text) {
        return new TextMessage(MessageId.random(), "user", text, System.currentTimeMillis());
    }

    // 快速创建助手文本消息的静态工厂
    static TextMessage assistantText(String text) {
        return new TextMessage(MessageId.random(), "assistant", text, System.currentTimeMillis());
    }

    // 快速创建工具调用消息的静态工厂
    static ToolCallMessage toolCall(String toolId, String callId, com.fasterxml.jackson.databind.JsonNode args) {
        return new ToolCallMessage(MessageId.random(), "assistant", toolId, callId, args, System.currentTimeMillis());
    }

    // 快速创建工具结果消息的静态工厂
    static ToolResultMessage toolResult(String toolId, String callId, String output) {
        return new ToolResultMessage(MessageId.random(), "tool", toolId, callId, output, List.of(), System.currentTimeMillis());
    }
}
