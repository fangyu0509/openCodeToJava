package io.opencode.core.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

// MCP（Model Context Protocol）客户端，管理一个 MCP 服务器子进程的 JSON-RPC 通信
public class McpClient implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(McpClient.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String name;                         // 服务器名称
    private final Process process;                     // MCP 服务器子进程
    private final BufferedReader reader;                // 进程标准输出读取器
    private final OutputStream writer;                  // 进程标准输入写入器
    private final AtomicInteger requestId = new AtomicInteger(1);
    private final Map<Integer, CompletableFuture<JsonNode>> pending = new ConcurrentHashMap<>();
    private volatile boolean running = true;

    // 启动 MCP 服务器子进程并初始化通信
    public McpClient(String name, String command, String... args) throws IOException {
        this.name = name;
        var pb = new ProcessBuilder(command);
        for (var a : args) pb.command().add(a);
        pb.directory(new File(System.getProperty("user.dir")));
        this.process = pb.start();
        this.reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
        this.writer = process.getOutputStream();
        startReader();
    }

    // 启动后台线程读取 MCP 服务器的响应消息
    private void startReader() {
        var thread = new Thread(() -> {
            try {
                while (running) {
                    var line = reader.readLine();
                    if (line == null) break;
                    if (!line.startsWith("Content-Length:")) continue;
                    var length = Integer.parseInt(line.substring(16).trim());
                    reader.readLine();
                    var body = new char[length];
                    var total = 0;
                    while (total < length) {
                        var read = reader.read(body, total, length - total);
                        if (read < 0) break;
                        total += read;
                    }
                    if (total < length) break;
                    handleMessage(new String(body));
                }
            } catch (IOException e) {
                if (running) running = false;
            }
        }, "mcp-reader-" + name);
        thread.setDaemon(true);
        thread.start();
    }

    // 处理收到的 JSON-RPC 消息，匹配 ID 完成对应的 Future
    private void handleMessage(String raw) {
        try {
            var msg = MAPPER.readTree(raw);
            var idNode = msg.get("id");
            if (idNode != null) {
                var id = idNode.asInt();
                var future = pending.remove(id);
                if (future != null) {
                    if (msg.has("result")) future.complete(msg.get("result"));
                    else if (msg.has("error")) future.completeExceptionally(
                        new RuntimeException(msg.get("error").get("message").asText()));
                }
            }
        } catch (Exception e) {
            log.debug("MCP parse error: {}", e.getMessage());
        }
    }

    // 发送 initialize 请求，声明客户端能力和信息
    public CompletableFuture<JsonNode> initialize() {
        var params = MAPPER.createObjectNode();
        var caps = params.putObject("capabilities");
        caps.putObject("tools").put("listChanged", true);
        caps.putObject("resources").put("subscribe", false);

        var info = params.putObject("clientInfo");
        info.put("name", "opencode-java");
        info.put("version", "0.1.0");

        return sendRequest("initialize", params).thenApply(result -> {
            sendNotification("notifications/initialized", MAPPER.createObjectNode());
            return result;
        });
    }

    // 列出 MCP 服务器提供的所有工具
    public CompletableFuture<List<ToolInfo>> listTools() {
        return sendRequest("tools/list", MAPPER.createObjectNode()).thenApply(result -> {
            var tools = new ArrayList<ToolInfo>();
            var arr = result.get("tools");
            if (arr != null && arr.isArray()) {
                for (var t : arr) {
                    tools.add(new ToolInfo(
                        t.get("name").asText(),
                        t.has("description") ? t.get("description").asText() : "",
                        t.get("inputSchema")
                    ));
                }
            }
            return tools;
        });
    }

    // 调用 MCP 服务器上的指定工具
    public CompletableFuture<JsonNode> callTool(String toolName, JsonNode arguments) {
        var params = MAPPER.createObjectNode();
        params.put("name", toolName);
        params.set("arguments", arguments);
        return sendRequest("tools/call", params);
    }

    // 发送 JSON-RPC 请求并返回 Future
    private CompletableFuture<JsonNode> sendRequest(String method, JsonNode params) {
        var id = requestId.getAndIncrement();
        var future = new CompletableFuture<JsonNode>();
        pending.put(id, future);
        try {
            var node = MAPPER.createObjectNode();
            node.put("jsonrpc", "2.0");
            node.put("id", id);
            node.put("method", method);
            node.set("params", params);
            writeMessage(node);
        } catch (Exception e) {
            pending.remove(id);
            future.completeExceptionally(e);
        }
        return future;
    }

    // 发送 JSON-RPC 通知（无 ID）
    private void sendNotification(String method, JsonNode params) {
        try {
            var node = MAPPER.createObjectNode();
            node.put("jsonrpc", "2.0");
            node.put("method", method);
            node.set("params", params);
            writeMessage(node);
        } catch (Exception e) {
            log.debug("MCP notification failed: {}", e.getMessage());
        }
    }

    // 写入 MCP 消息（Content-Length 头 + JSON 体）
    private void writeMessage(ObjectNode node) throws IOException {
        var body = MAPPER.writeValueAsString(node);
        var header = "Content-Length: " + body.getBytes(StandardCharsets.UTF_8).length + "\r\n\r\n";
        synchronized (writer) {
            writer.write(header.getBytes(StandardCharsets.UTF_8));
            writer.write(body.getBytes(StandardCharsets.UTF_8));
            writer.flush();
        }
    }

    @Override
    // 关闭连接：发送 exit、关闭流、销毁进程、清理待处理请求
    public void close() {
        running = false;
        try { sendNotification("exit", MAPPER.createObjectNode()); } catch (Exception e) { /* ignore */ }
        try { reader.close(); } catch (Exception e) { /* ignore */ }
        try { writer.close(); } catch (Exception e) { /* ignore */ }
        process.destroy();
        try { process.waitFor(3, java.util.concurrent.TimeUnit.SECONDS); } catch (Exception e) { /* ignore */ }
        if (process.isAlive()) process.destroyForcibly();
        for (var f : pending.values()) f.completeExceptionally(new RuntimeException("Connection closed"));
        pending.clear();
    }

    public boolean isAlive() { return process.isAlive(); }
    public String name() { return name; }

    // MCP 工具信息：名称、描述、输入 JSON Schema
    public record ToolInfo(String name, String description, JsonNode inputSchema) {}
}
