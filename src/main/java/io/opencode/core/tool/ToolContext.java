package io.opencode.core.tool;

import io.opencode.core.model.AbortSignal;
import io.opencode.core.model.MessageId;
import io.opencode.core.model.SessionId;
import io.opencode.core.session.Message;

import java.util.List;
import java.util.function.Consumer;

// 工具执行上下文：记录当前会话、消息、代理信息，提供中止信号和回调能力
public record ToolContext(
    SessionId sessionId,       // 当前会话 ID
    MessageId messageId,       // 当前消息 ID
    String agent,              // 当前代理名称
    AbortSignal abort,         // 中止信号，用于取消正在执行的工具
    String callId,             // 工具调用 ID
    List<Message> messages,    // 消息历史列表
    Consumer<ExecuteResult<?>> metadata,  // 上报元数据的回调函数
    Consumer<String> ask       // 向用户提问的回调函数
) {
    public static final ToolContext EMPTY = new ToolContext(
        null, null, "", new AbortSignal(), "", List.of(),
        r -> {}, q -> {}
    );

    // 紧凑构造器：确保 abort 信号不为 null
    public ToolContext {
        if (abort == null) {
            abort = new AbortSignal();
        }
    }

    // 上报元数据给回调
    public void reportMetadata(ExecuteResult<?> result) {
        metadata.accept(result);
    }

    // 向用户提问，需要用户确认或回答
    public void askPermission(String question) {
        ask.accept(question);
    }
}
