package io.opencode.core.session;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.opencode.core.agent.AgentConfig;
import io.opencode.core.model.SessionId;
import io.opencode.core.config.OpenCodeConfig;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

// 会话管理器 — 全局会话注册中心，负责会话的创建、查询、删除以及启动/关闭时的持久化
@Service
public class SessionManager {
    private static final Logger log = LoggerFactory.getLogger(SessionManager.class);
    // 会话 ID -> Session 的并发映射，保证线程安全
    private final Map<SessionId, Session> sessions = new ConcurrentHashMap<>();
    private final OpenCodeConfig config;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public SessionManager(OpenCodeConfig config) {
        this.config = config;
    }

    @PostConstruct
    // 应用启动时扫描磁盘上的 sessions 目录，反序列化所有已有的 FileSession
    public void loadSessions() {
        if (config.dataDir().isEmpty()) return;
        var sessionsDir = config.dataDir().get().resolve("sessions");
        if (!Files.exists(sessionsDir)) return;

        try (var dirs = Files.list(sessionsDir)) {
            var loaded = 0;
            for (var sessionDir : dirs.toList()) {
                if (!Files.isDirectory(sessionDir)) continue;
                try {
                    // 每个子目录需同时包含 config.json 与 messages.jsonl 才视为有效会话
                    var configFile = sessionDir.resolve("config.json");
                    var messagesFile = sessionDir.resolve("messages.jsonl");
                    if (!Files.exists(configFile) || !Files.exists(messagesFile)) continue;

                    var configRoot = MAPPER.readTree(Files.readString(configFile));
                    var agentConfig = AgentConfig.builder(configRoot.get("name").asText()).build();
                    var sid = new SessionId(sessionDir.getFileName().toString());
                    var session = new FileSession(sid, agentConfig, config.dataDir().get(), List.of());
                    session.load().get();                     // 同步等待加载完成
                    sessions.put(sid, session);
                    loaded++;
                } catch (Exception e) {
                    log.warn("Failed to load session from {}: {}", sessionDir, e.getMessage());
                }
            }
            log.info("Loaded {} session(s) from disk", loaded);
        } catch (Exception e) {
            log.warn("Failed to list sessions directory: {}", e.getMessage());
        }
    }

    // 将会话注册到管理器并返回
    public Session create(Session session) {
        sessions.put(session.id(), session);
        return session;
    }

    // 根据 SessionId 获取会话
    public Optional<Session> get(SessionId id) {
        return Optional.ofNullable(sessions.get(id));
    }

    // 根据字符串形式的 ID 获取会话（内部转为 SessionId）
    public Optional<Session> get(String id) {
        return get(new SessionId(id));
    }

    // 移除会话（不触发持久化，调用方需自行处理）
    public void remove(SessionId id) {
        sessions.remove(id);
    }

    // 当前管理的会话数量
    public int count() {
        return sessions.size();
    }

    // 返回所有会话的不可变列表
    public List<Session> all() {
        return List.copyOf(sessions.values());
    }

    @PreDestroy
    // 应用关闭前，将所有 FileSession 持久化到磁盘
    public void shutdown() {
        log.info("Persisting {} session(s) on shutdown...", sessions.size());
        for (var session : sessions.values()) {
            if (session instanceof FileSession fs) {
                try {
                    fs.persist().get();   // 同步等待写盘完成
                } catch (Exception e) {
                    log.warn("Failed to persist session {}: {}", fs.id(), e.getMessage());
                }
            }
        }
        log.info("Session persistence complete");
    }
}
