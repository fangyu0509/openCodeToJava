package io.opencode.core.session;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.opencode.core.agent.AgentConfig;
import io.opencode.core.model.MessageId;
import io.opencode.core.model.SessionId;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.util.Collections.emptyList;

// 基于文件的会话实现 — 消息与配置持久化到磁盘 JSONL + JSON 文件
public class FileSession implements Session {
    private static final ObjectMapper MAPPER = new ObjectMapper();          // JSON 解析器
    private static final Logger log = LoggerFactory.getLogger(FileSession.class);
    // 单线程 IO 执行器，用于异步文件读写
    private static final ExecutorService IO_EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        var t = new Thread(r, "file-session-io");
        t.setDaemon(true);
        return t;
    });

    // JVM 关闭时优雅关闭 IO 线程池
    static {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            IO_EXECUTOR.shutdown();
        }));
    }

    private final SessionId id;          // 会话唯一 ID
    private AgentConfig agentConfig;     // agent 配置
    private String title;                // 会话标题
    private final Path dataDir;          // 数据存储根目录
    private final List<Message> messages = new ArrayList<>(); // 消息列表

    // 构造：随机生成 ID，使用默认空消息列表
    public FileSession(AgentConfig agentConfig, Path dataDir) {
        this.id = SessionId.random();
        this.agentConfig = agentConfig;
        this.dataDir = dataDir;
    }

    // 构造：使用指定的 ID 和已有消息列表（用于从磁盘恢复）
    public FileSession(SessionId id, AgentConfig agentConfig, Path dataDir, List<Message> messages) {
        this.id = id;
        this.agentConfig = agentConfig;
        this.dataDir = dataDir;
        this.messages.addAll(messages);
    }

    @Override
    public SessionId id() { return id; }

    @Override
    public AgentConfig agentConfig() { return agentConfig; }

    @Override
    public String title() { return title != null ? title : ""; }

    @Override
    // 设置标题并立即持久化
    public void setTitle(String t) {
        this.title = t;
        persist();
    }

    @Override
    public void updateAgentConfig(AgentConfig config) { this.agentConfig = config; }

    @Override
    // 返回不可变的消息列表副本
    public List<Message> messages() { return List.copyOf(messages); }

    @Override
    // 追加消息，自动生成标题，并触发持久化
    public void append(Message message) {
        messages.add(message);
        if ((title == null || title.isBlank()) && message instanceof Message.TextMessage t && "user".equals(t.role())) {
            title = t.text().replaceAll("\\s+", " ").strip();
            if (title.length() > 60) title = title.substring(0, 57) + "...";
        }
        persist();
    }

    @Override
    // 异步持久化：将消息写为 JSONL，config 写为 JSON
    public CompletableFuture<Void> persist() {
        return CompletableFuture.runAsync(() -> {
            log.debug("Persisting session {}", id());
            try {
                var sessionDir = dataDir.resolve("sessions").resolve(id().value());
                Files.createDirectories(sessionDir);

                // 将每条消息序列化为一行 JSON
                var messagesFile = sessionDir.resolve("messages.jsonl");
                var lines = new ArrayList<String>();
                for (var msg : messages()) {
                    lines.add(serialize(msg));
                }
                Files.writeString(messagesFile, String.join("\n", lines));

                // 写入 agent 名称与标题到 config.json
                var configFile = sessionDir.resolve("config.json");
                var escapedName = escape(agentConfig.name());
                var escapedTitle = escape(title != null ? title : "");
                var configJson = "{\"name\":\"" + escapedName + "\",\"title\":\"" + escapedTitle + "\"}";
                Files.writeString(configFile, configJson);
            } catch (Exception e) {
                throw new RuntimeException("Failed to persist session " + id(), e);
            }
        }, IO_EXECUTOR);
    }

    @Override
    // 异步加载：从磁盘恢复标题和消息列表
    public CompletableFuture<Void> load() {
        return CompletableFuture.runAsync(() -> {
            log.debug("Loading session {}", id());
            try {
                var sessionDir = dataDir.resolve("sessions").resolve(id().value());

                // 读取 config.json 恢复标题
                var configFile = sessionDir.resolve("config.json");
                if (Files.exists(configFile)) {
                    var configRoot = MAPPER.readTree(Files.readString(configFile));
                    if (configRoot.has("title")) {
                        title = configRoot.get("title").asText();
                    }
                }

                // 读取 messages.jsonl，逐行反序列化
                var messagesFile = sessionDir.resolve("messages.jsonl");
                if (!Files.exists(messagesFile)) return;

                var content = Files.readString(messagesFile);
                if (content.isBlank()) return;

                messages.clear();
                for (var line : content.split("\n")) {
                    if (line.isBlank()) continue;
                    try {
                        var msg = deserialize(line);
                        if (msg != null) messages.add(msg);
                    } catch (Exception e) {
                        log.warn("Skipping malformed line in session {}: {}", id(), e.getMessage());
                    }
                }
            } catch (Exception e) {
                throw new RuntimeException("Failed to load session " + id(), e);
            }
        }, IO_EXECUTOR);
    }

    @Override
    public void clear() { messages.clear(); }

    @Override
    // 创建分支：新会话使用独立的 fork 目录
    public Session fork(int upToMessageIndex) {
        var fork = new FileSession(agentConfig, dataDir.resolve("fork-" + java.time.Instant.now().toEpochMilli()));
        fork.setTitle(title());
        var msgs = messages.subList(0, Math.min(upToMessageIndex, messages.size()));
        fork.messages.addAll(msgs);
        return fork;
    }

    // 将 Message 序列化为 JSON 行字符串
    private String serialize(Message msg) {
        if (msg instanceof Message.TextMessage t) {
            return "{\"type\":\"text\",\"id\":\"" + t.id().value() + "\",\"ts\":" + t.timestamp()
                + ",\"role\":\"" + t.role() + "\",\"text\":\"" + escape(t.text()) + "\"}";
        } else if (msg instanceof Message.ToolCallMessage t) {
            return "{\"type\":\"tool_call\",\"id\":\"" + t.id().value() + "\",\"ts\":" + t.timestamp()
                + ",\"tool\":\"" + escape(t.toolId()) + "\",\"callId\":\"" + escape(t.callId()) + "\",\"args\":" + t.args().toString() + "}";
        } else if (msg instanceof Message.ToolResultMessage t) {
            return "{\"type\":\"tool_result\",\"id\":\"" + t.id().value() + "\",\"ts\":" + t.timestamp()
                + ",\"tool\":\"" + escape(t.toolId()) + "\",\"callId\":\"" + escape(t.callId()) + "\",\"output\":\"" + escape(t.output()) + "\"}";
        } else if (msg instanceof Message.FileMessage t) {
            return "{\"type\":\"file\",\"id\":\"" + t.id().value() + "\",\"ts\":" + t.timestamp()
                + ",\"role\":\"" + t.role() + "\",\"path\":\"" + escape(t.file().path()) + "\"}";
        }
        return "{}";
    }

    // 转义字符串中的特殊字符（反斜杠、引号、换行等），防止 JSON 注入
    private String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }

    // 安全读取 JSON 节点的字符串字段，不存在时返回默认值
    private String jsonText(com.fasterxml.jackson.databind.JsonNode root, String field, String fallback) {
        return root.has(field) ? root.get(field).asText() : fallback;
    }

    // 安全读取 JSON 节点的长整型字段，不存在时返回默认值
    private long jsonLong(com.fasterxml.jackson.databind.JsonNode root, String field, long fallback) {
        return root.has(field) ? root.get(field).asLong() : fallback;
    }

    // 将 JSON 行反序列化为 Message 对象，根据 type 字段分发到不同的 record
    private Message deserialize(String line) throws Exception {
        var root = MAPPER.readTree(line);
        var type = root.get("type").asText();
        var id = jsonText(root, "id", MessageId.random().value());
        var ts = jsonLong(root, "ts", System.currentTimeMillis());
        return switch (type) {
            case "text" -> new Message.TextMessage(
                new MessageId(id), root.get("role").asText(),
                root.get("text").asText(), ts);
            case "tool_call" -> new Message.ToolCallMessage(
                new MessageId(id), "assistant", root.get("tool").asText(),
                root.get("callId").asText(), MAPPER.readTree(root.get("args").toString()),
                ts);
            case "tool_result" -> new Message.ToolResultMessage(
                new MessageId(id), "tool", root.get("tool").asText(),
                root.get("callId").asText(), root.get("output").asText(),
                List.of(), ts);
            case "file" -> new Message.FileMessage(
                new MessageId(id), root.get("role").asText(),
                io.opencode.core.session.FilePart.reference(root.get("path").asText()),
                ts);
            default -> {
                org.slf4j.LoggerFactory.getLogger(getClass())
                    .warn("Unknown message type in session file: {}", type);
                yield null;
            }
        };
    }
}
