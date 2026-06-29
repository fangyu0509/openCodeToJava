package io.opencode.core.agent;

import io.opencode.core.session.Session;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * 代理循环接口，定义代理处理用户输入的核心方法。
 * 实现类负责：与LLM通信、执行工具调用、管理会话上下文、处理流式输出。
 */
public interface AgentLoop {
    // 处理用户输入，返回代理响应（无事件回调版本）
    CompletableFuture<AgentResponse> process(
        Session session,
        String userInput,
        AgentConfig config
    );

    // 处理用户输入，支持通过 onEvent 回调实时推送 SSE 事件
    CompletableFuture<AgentResponse> process(
        Session session,
        String userInput,
        AgentConfig config,
        Consumer<String> onEvent
    );
}
