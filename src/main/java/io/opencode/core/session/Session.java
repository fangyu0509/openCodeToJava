package io.opencode.core.session;

import io.opencode.core.model.SessionId;
import io.opencode.core.agent.AgentConfig;

import java.util.List;
import java.util.concurrent.CompletableFuture;

// 会话核心接口 — 定义一次对话的生命周期，包括消息管理、持久化、快照分离等
public interface Session {
    // 获取会话唯一标识
    SessionId id();
    // 获取该会话绑定的 agent 配置
    AgentConfig agentConfig();
    // 获取当前会话的所有消息（不可变列表）
    List<Message> messages();
    // 追加一条消息到会话末尾
    void append(Message message);
    // 将会话持久化到存储层（异步）
    CompletableFuture<Void> persist();
    // 从存储层加载会话数据（异步）
    CompletableFuture<Void> load();
    // 清空当前会话的所有消息
    void clear();
    // 更新 agent 配置，默认不支持此操作
    default void updateAgentConfig(AgentConfig config) {
        throw new UnsupportedOperationException("Agent config cannot be updated for this session type");
    }

    // 获取会话标题（默认返回空字符串）
    default String title() { return ""; }
    // 设置会话标题（默认空实现）
    default void setTitle(String title) {}
    // 从当前会话 fork 出一个新会话，包含前 upToMessageIndex 条消息
    Session fork(int upToMessageIndex);
}
