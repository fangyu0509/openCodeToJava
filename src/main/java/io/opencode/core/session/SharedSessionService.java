package io.opencode.core.session;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// 会话共享服务 — 将会话生成为短链接形式的分享 ID，支持导出和列出已分享的会话
@Service
public class SharedSessionService {
    private static final Logger log = LoggerFactory.getLogger(SharedSessionService.class);
    // 分享 ID -> 共享会话数据的映射
    private final Map<String, SharedSession> shares = new ConcurrentHashMap<>();
    private final ObjectMapper mapper = new ObjectMapper();
    private final SecureRandom random = new SecureRandom();

    // 共享会话的数据结构
    public record SharedSession(String id, List<Message> messages, String agent, String createdAt) {}

    // 导出时的数据传输结构（Message 降级为 MessageData，不包含 FilePart 等敏感信息）
    public record ShareData(List<MessageData> messages, String agent, String createdAt) {}

    // 导出用的扁平消息结构
    public record MessageData(String role, String type, String text, long timestamp) {}

    // 会话 ID -> 分享 ID 的逆向索引，用于重复分享时返回相同 ID
    private final Map<String, String> sessionToShareId = new ConcurrentHashMap<>();

    // 分享一个会话：若已分享过则直接返回已有 ID，否则生成新 ID 并保存快照
    public String share(Session session) {
        var existing = sessionToShareId.get(session.id().value());
        if (existing != null && shares.containsKey(existing)) return existing;
        var id = generateId();
        var createdAt = java.time.Instant.now().toString();
        shares.put(id, new SharedSession(id, List.copyOf(session.messages()), session.agentConfig().name(), createdAt));
        sessionToShareId.put(session.id().value(), id);
        log.info("Session {} shared as {}", session.id().value(), id);
        return id;
    }

    // 取消分享指定会话，同时清理逆向索引
    public boolean unshare(String sessionId) {
        var shareId = sessionToShareId.remove(sessionId);
        if (shareId != null) {
            shares.remove(shareId);
            log.info("Session {} unshared", sessionId);
            return true;
        }
        return false;
    }

    // 根据分享 ID 获取共享会话
    public SharedSession get(String shareId) {
        return shares.get(shareId);
    }

    // 将会话导出为 ShareData（不含敏感的内部结构），按消息类型提取文本内容
    public ShareData exportSession(Session session) {
        var messages = session.messages().stream()
            .map(m -> {
                String type;
                String text;
                if (m instanceof Message.TextMessage t) {
                    type = "text"; text = t.text();
                } else if (m instanceof Message.ToolCallMessage t) {
                    type = "tool_call"; text = t.args().toString();
                } else if (m instanceof Message.ToolResultMessage t) {
                    type = "tool_result"; text = t.output();
                } else if (m instanceof Message.FileMessage t) {
                    type = "file"; text = t.file().path();
                } else {
                    type = "unknown"; text = "";
                }
                return new MessageData(m.role(), type, text, m.timestamp());
            })
            .toList();
        return new ShareData(messages, session.agentConfig().name(), java.time.Instant.now().toString());
    }

    // 列出所有已分享的会话
    public List<SharedSession> listShares() {
        return List.copyOf(shares.values());
    }

    // 生成 8 字符 URL 安全的随机分享 ID（6 字节 Base64 编码）
    private String generateId() {
        var bytes = new byte[6];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
