package io.opencode.core.session;

import io.opencode.core.agent.AgentConfig;
import io.opencode.core.model.SessionId;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

// 基于内存的会话实现 — 数据保存在 JVM 堆中，不进行磁盘持久化
public class InMemorySession implements Session {
    private final SessionId id;           // 会话唯一 ID
    private AgentConfig agentConfig;      // agent 配置（可更新）
    private String title;                 // 自动生成的会话标题
    private final List<Message> messages = new ArrayList<>(); // 消息列表

    // 构造：随机生成会话 ID
    public InMemorySession(AgentConfig agentConfig) {
        this.id = SessionId.random();
        this.agentConfig = agentConfig;
    }

    // 构造：使用指定的会话 ID
    public InMemorySession(SessionId id, AgentConfig agentConfig) {
        this.id = id;
        this.agentConfig = agentConfig;
    }

    @Override
    public SessionId id() { return id; }

    @Override
    public AgentConfig agentConfig() { return agentConfig; }

    @Override
    public String title() { return title != null ? title : ""; }

    @Override
    public void setTitle(String t) { this.title = t; }

    @Override
    // 返回不可变的消息列表快照，防止外部篡改
    public List<Message> messages() { return List.copyOf(messages); }

    @Override
    // 追加消息；若标题为空且消息是用户文本，自动截取前 60 字符作为标题
    public void append(Message message) {
        messages.add(message);
        if ((title == null || title.isBlank()) && message instanceof Message.TextMessage t && "user".equals(t.role())) {
            title = t.text().replaceAll("\\s+", " ").strip();
            if (title.length() > 60) title = title.substring(0, 57) + "...";
        }
    }

    @Override
    // 内存会话无需持久化，直接返回已完成 Future
    public CompletableFuture<Void> persist() { return CompletableFuture.completedFuture(null); }

    @Override
    // 内存会话无需加载，直接返回已完成 Future
    public CompletableFuture<Void> load() { return CompletableFuture.completedFuture(null); }

    @Override
    public void clear() { messages.clear(); }

    @Override
    // 允许调用方更新 agent 配置（例如切换模型）
    public void updateAgentConfig(AgentConfig config) {
        this.agentConfig = config;
    }

    @Override
    // 创建当前会话的一个分支（fork），复制标题和指定范围的消息
    public Session fork(int upToMessageIndex) {
        var fork = new InMemorySession(agentConfig);
        fork.title = title;
        var msgs = messages.subList(0, Math.min(upToMessageIndex, messages.size()));
        fork.messages.addAll(msgs);
        return fork;
    }
}
