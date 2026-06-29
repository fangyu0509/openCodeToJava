package io.opencode.core.lsp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

// JSON-RPC 2.0 协议实现，基于标准输入/输出流进行消息读写（LSP 协议的基础通信层）
public class JsonRpc {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final AtomicInteger requestId = new AtomicInteger(1);       // 自增请求 ID
    private final Map<Integer, CompletableFuture<JsonNode>> pending = new ConcurrentHashMap<>(); // 待响应的请求
    private final BufferedReader reader;   // 输入流读取器
    private final OutputStream writer;     // 输出流写入器
    private volatile boolean running = true;

    // 使用输入/输出流初始化，并启动后台读取线程
    public JsonRpc(InputStream input, OutputStream output) {
        this.reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8));
        this.writer = output;
        startReader();
    }

    // 启动后台线程持续读取 LSP 消息，按 Content-Length 头解析消息体
    private void startReader() {
        var thread = new Thread(() -> {
            try {
                while (running) {
                    var line = reader.readLine();
                    if (line == null) break;
                    if (!line.startsWith("Content-Length:")) continue;
                    var length = Integer.parseInt(line.substring(16).trim());
                    reader.readLine(); // 跳过空行
                    var body = new char[length];
                    var total = 0;
                    // 循环读取直到获取完整消息体
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
        }, "lsp-reader");
        thread.setDaemon(true);
        thread.start();
    }

    // 处理收到的 JSON-RPC 消息：匹配 ID 到待完成的 Future
    private void handleMessage(String raw) {
        try {
            var msg = MAPPER.readTree(raw);
            // 成功响应：完成对应的 Future
            if (msg.has("id") && msg.has("result")) {
                var id = msg.get("id").asInt();
                var future = pending.remove(id);
                if (future != null) future.complete(msg.get("result"));
            // 错误响应：异常完成对应的 Future
            } else if (msg.has("id") && msg.has("error")) {
                var id = msg.get("id").asInt();
                var future = pending.remove(id);
                if (future != null) future.completeExceptionally(
                    new RuntimeException(msg.get("error").get("message").asText()));
            }
        } catch (Exception e) {
            // 忽略格式错误的消息
        }
    }

    // 发送请求（带 ID），返回 CompletableFuture 异步获取结果
    public CompletableFuture<JsonNode> sendRequest(String method, JsonNode params) {
        var id = requestId.getAndIncrement();
        var future = new CompletableFuture<JsonNode>();
        pending.put(id, future);
        try {
            var node = MAPPER.createObjectNode();
            node.put("jsonrpc", "2.0");
            node.put("id", id);
            node.put("method", method);
            node.set("params", params);
            send(node);
        } catch (Exception e) {
            pending.remove(id);
            future.completeExceptionally(e);
        }
        return future;
    }

    // 发送通知（无 ID），不期待响应
    public void sendNotification(String method, JsonNode params) {
        try {
            var node = MAPPER.createObjectNode();
            node.put("jsonrpc", "2.0");
            node.put("method", method);
            node.set("params", params);
            send(node);
        } catch (Exception e) {
            // 忽略通知发送失败
        }
    }

    // 实际写入消息：Content-Length 头 + 空行 + JSON 体（线程安全）
    private void send(ObjectNode node) throws IOException {
        var body = MAPPER.writeValueAsString(node);
        var header = "Content-Length: " + body.getBytes(StandardCharsets.UTF_8).length + "\r\n\r\n";
        synchronized (writer) {
            writer.write(header.getBytes(StandardCharsets.UTF_8));
            writer.write(body.getBytes(StandardCharsets.UTF_8));
            writer.flush();
        }
    }

    // 关闭连接，取消所有待处理的请求
    public void close() {
        running = false;
        try { reader.close(); } catch (Exception e) {}
        try { writer.close(); } catch (Exception e) {}
        for (var f : pending.values()) f.completeExceptionally(new RuntimeException("Connection closed"));
        pending.clear();
    }
}
