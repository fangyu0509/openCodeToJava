package io.opencode.core.acp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import io.opencode.core.agent.AgentConfig;
import io.opencode.core.agent.AgentLoop;
import io.opencode.core.agent.AgentResponse;
import io.opencode.core.model.SessionId;
import io.opencode.core.session.InMemorySession;
import io.opencode.core.session.Message;
import io.opencode.core.session.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

// ACP（Agent Communication Protocol）服务器，通过 stdin/stdout 以 JSON-RPC 方式与代理交互
public class AcpServer {
    private static final Logger log = LoggerFactory.getLogger(AcpServer.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String VERSION = "0.1.0";

    private final AgentLoop agentLoop;                 // 代理执行循环
    private final AgentConfig defaultConfig;            // 默认代理配置
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();  // 会话缓存
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final JsonNodeFactory factory = JsonNodeFactory.instance;

    public AcpServer(AgentLoop agentLoop, AgentConfig defaultConfig) {
        this.agentLoop = agentLoop;
        this.defaultConfig = defaultConfig;
    }

    // 启动主循环：从 stdin 读取逐行 JSON-RPC 消息并处理
    public void start() {
        var reader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        var writer = new PrintWriter(new OutputStreamWriter(System.out, StandardCharsets.UTF_8), true);
        var line = new StringBuilder();
        running.set(true);

        while (running.get()) {
            try {
                var ch = reader.read();
                if (ch < 0) break;
                line.append((char) ch);
                if ((char) ch == '\n') {
                    var msg = line.toString().strip();
                    line.setLength(0);
                    if (msg.isEmpty()) continue;
                    handleMessage(msg, writer);
                }
            } catch (Exception e) {
                log.error("ACP read error", e);
                break;
            }
        }
    }

    // 处理一条 JSON-RPC 消息，支持 initialize/shutdown/process 方法
    private void handleMessage(String json, PrintWriter writer) {
        try {
            var root = MAPPER.readTree(json);
            var id = root.has("id") ? root.get("id") : null;
            var method = root.has("method") ? root.get("method").asText("") : "";

            switch (method) {
                case "initialize" -> sendResult(writer, id, factory.objectNode()
                    .put("version", VERSION)
                    .put("name", "opencode")
                    .set("capabilities", factory.objectNode()
                        .put("streaming", true)
                        .put("tools", true)));
                case "shutdown" -> {
                    sendResult(writer, id, factory.textNode("ok"));
                    running.set(false);  // 停止主循环
                }
                case "process" -> {
                    // 创建或复用会话，追加用户输入后异步请求代理处理
                    var params = root.get("params");
                    var input = params != null && params.has("input")
                        ? params.get("input").asText("") : "";
                    var sessionId = params != null && params.has("sessionId")
                        ? params.get("sessionId").asText() : java.util.UUID.randomUUID().toString();
                    var session = sessions.computeIfAbsent(sessionId,
                        k -> new InMemorySession(new SessionId(sessionId), defaultConfig));
                    session.append(Message.userText(input));
                    agentLoop.process(session, input, defaultConfig)
                        .thenAccept(response -> {
                            var result = factory.objectNode();
                            result.put("sessionId", sessionId);
                            result.put("output", response.text().orElse(""));
                            result.put("type", response.type().name());
                            sendResult(writer, id, result);
                        })
                        .exceptionally(e -> {
                            sendError(writer, id, -1, e.getMessage());
                            return null;
                        });
                }
                default -> sendError(writer, id, -32601, "Method not found: " + method);
            }
        } catch (Exception e) {
            log.error("ACP handle error", e);
            sendError(writer, null, -32700, "Parse error");
        }
    }

    // 发送 JSON-RPC 成功响应到 stdout
    private void sendResult(PrintWriter writer, JsonNode id, JsonNode result) {
        try {
            var msg = MAPPER.writeValueAsString(
                Map.of("jsonrpc", "2.0", "id", id, "result", result));
            writer.println(msg);
            writer.flush();
        } catch (Exception e) {
            log.error("ACP send error", e);
        }
    }

    // 发送 JSON-RPC 错误响应到 stdout
    private void sendError(PrintWriter writer, JsonNode id, int code, String message) {
        try {
            var err = factory.objectNode();
            err.put("code", code);
            err.put("message", message);
            var msg = MAPPER.writeValueAsString(
                Map.of("jsonrpc", "2.0", "id", id, "error", err));
            writer.println(msg);
            writer.flush();
        } catch (Exception e) {
            log.error("ACP send error", e);
        }
    }
}
