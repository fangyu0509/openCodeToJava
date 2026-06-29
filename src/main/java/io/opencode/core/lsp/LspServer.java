package io.opencode.core.lsp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

// LSP 语言服务器客户端，管理子进程并通过 JSON-RPC 通信
public class LspServer implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(LspServer.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String name;        // 服务器名称（如 "typescript"）
    private final Path rootPath;      // 项目根路径
    private final Process process;    // LSP 服务器子进程
    private final JsonRpc rpc;        // JSON-RPC 通信层
    private volatile boolean initialized = false;

    // LSP 服务器能力：是否支持跳转定义、查找引用、悬停提示、诊断
    public record Capabilities(boolean hasDefinition, boolean hasReferences, boolean hasHover, boolean hasDiagnostics) {}

    // 启动 LSP 服务器子进程，参数为命令及参数
    public LspServer(String name, Path rootPath, String command, String... args) throws IOException {
        this.name = name;
        this.rootPath = rootPath;
        var pb = new ProcessBuilder(command);
        for (var a : args) pb.command().add(a);
        pb.directory(rootPath.toFile());
        this.process = pb.start();
        this.rpc = new JsonRpc(process.getInputStream(), process.getOutputStream());
    }

    // 发送 initialize 请求，包含客户端能力信息；初始化完成后发送 initialized 通知
    public CompletableFuture<Capabilities> initialize() {
        var params = MAPPER.createObjectNode();
        var processNode = params.putObject("processId");
        processNode.put("pid", ProcessHandle.current().pid());
        params.put("rootUri", rootPath.toUri().toString());
        params.put("rootPath", rootPath.toString());
        var caps = params.putObject("capabilities");
        var td = caps.putObject("textDocument");
        var hover = td.putObject("hover");
        var formats = hover.putArray("contentFormat");
        formats.add("markdown");
        var general = caps.putObject("general");
        var encodings = general.putArray("positionEncodings");
        encodings.add("utf-16");

        return rpc.sendRequest("initialize", params).thenApply(result -> {
            var capsResult = result.get("capabilities");
            var tdCaps = capsResult.get("textDocument");
            initialized = true;
            rpc.sendNotification("initialized", MAPPER.createObjectNode());
            return new Capabilities(
                hasField(tdCaps, "definition"),
                hasField(tdCaps, "references"),
                hasField(tdCaps, "hover"),
                hasField(tdCaps, "diagnostics")
            );
        });
    }

    // 通知服务器打开文档（didOpen），发送文件完整内容
    public void openDocument(Path filePath) {
        var params = MAPPER.createObjectNode();
        var td = params.putObject("textDocument");
        td.put("uri", filePath.toUri().toString());
        td.put("languageId", detectLanguage(filePath));
        td.put("version", 1);
        td.put("text", readFile(filePath));
        rpc.sendNotification("textDocument/didOpen", params);
    }

    // 通知服务器文档内容变更（didChange），发送新完整内容
    public void changeDocument(Path filePath, String content) {
        var params = MAPPER.createObjectNode();
        var td = params.putObject("textDocument");
        td.put("uri", filePath.toUri().toString());
        td.put("version", System.currentTimeMillis());
        var changes = params.putArray("contentChanges");
        var change = changes.addObject();
        change.put("text", content);
        rpc.sendNotification("textDocument/didChange", params);
    }

    // 跳转到定义位置
    public CompletableFuture<JsonNode> goToDefinition(Path filePath, int line, int character) {
        return sendPosition("textDocument/definition", filePath, line, character);
    }

    // 查找符号引用
    public CompletableFuture<JsonNode> findReferences(Path filePath, int line, int character) {
        var params = positionParams(filePath, line, character);
        params.put("context", MAPPER.createObjectNode().put("includeDeclaration", true));
        return rpc.sendRequest("textDocument/references", params);
    }

    // 获取悬停提示信息
    public CompletableFuture<JsonNode> hover(Path filePath, int line, int character) {
        return sendPosition("textDocument/hover", filePath, line, character);
    }

    // 获取文档诊断结果
    public CompletableFuture<JsonNode> getDiagnostics(Path filePath) {
        var params = MAPPER.createObjectNode();
        params.put("uri", filePath.toUri().toString());
        return rpc.sendRequest("textDocument/diagnostic", params);
    }

    // 通用方法：在指定位置发送 LSP 请求
    private CompletableFuture<JsonNode> sendPosition(String method, Path filePath, int line, int character) {
        return rpc.sendRequest(method, positionParams(filePath, line, character));
    }

    // 构造包含文件 URI 和位置信息的参数对象
    private ObjectNode positionParams(Path filePath, int line, int character) {
        var params = MAPPER.createObjectNode();
        var td = params.putObject("textDocument");
        td.put("uri", filePath.toUri().toString());
        var pos = params.putObject("position");
        pos.put("line", line);
        pos.put("character", character);
        return params;
    }

    @Override
    // 关闭 LSP 服务器：发送 shutdown 和 exit 通知，销毁进程
    public void close() {
        try {
            rpc.sendNotification("shutdown", MAPPER.createObjectNode());
        } catch (Exception e) { /* ignore */ }
        try {
            rpc.sendNotification("exit", MAPPER.createObjectNode());
        } catch (Exception e) { /* ignore */ }
        rpc.close();
        process.destroy();
        try { process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS); } catch (Exception e) {}
        if (process.isAlive()) process.destroyForcibly();
    }

    public boolean isAlive() { return process.isAlive(); }
    public boolean isInitialized() { return initialized; }
    public String name() { return name; }

    // 根据文件扩展名检测 LSP 语言 ID
    private static String detectLanguage(Path path) {
        var name = path.toString().toLowerCase();
        if (name.endsWith(".java")) return "java";
        if (name.endsWith(".py")) return "python";
        if (name.endsWith(".ts") || name.endsWith(".tsx")) return "typescript";
        if (name.endsWith(".js") || name.endsWith(".jsx")) return "javascript";
        if (name.endsWith(".rs")) return "rust";
        if (name.endsWith(".go")) return "go";
        if (name.endsWith(".rb")) return "ruby";
        if (name.endsWith(".kt") || name.endsWith(".kts")) return "kotlin";
        if (name.endsWith(".swift")) return "swift";
        if (name.endsWith(".c") || name.endsWith(".h")) return "c";
        if (name.endsWith(".cpp") || name.endsWith(".hpp") || name.endsWith(".cc")) return "cpp";
        if (name.endsWith(".cs")) return "csharp";
        if (name.endsWith(".php")) return "php";
        if (name.endsWith(".scala")) return "scala";
        if (name.endsWith(".vue")) return "vue";
        if (name.endsWith(".svelte")) return "svelte";
        if (name.endsWith(".astro")) return "astro";
        if (name.endsWith(".dart")) return "dart";
        return "plaintext";
    }

    // 读取文件内容
    private static String readFile(Path path) {
        try { return java.nio.file.Files.readString(path); }
        catch (IOException e) { return ""; }
    }

    // 检查 JSON 节点是否包含非 null 的指定字段
    private static boolean hasField(JsonNode node, String field) {
        return node != null && node.has(field) && !node.get(field).isNull();
    }
}
