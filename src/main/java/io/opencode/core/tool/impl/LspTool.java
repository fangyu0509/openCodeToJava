package io.opencode.core.tool.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.opencode.core.lsp.LspService;
import io.opencode.core.tool.ExecuteResult;
import io.opencode.core.tool.Tool;
import io.opencode.core.tool.ToolContext;
import io.opencode.core.tool.util.ToolUtils;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.Optional;

// LSP 工具：通过语言服务器协议提供代码智能（跳转定义、查找引用、悬停提示、诊断信息）
@Component
public class LspTool implements Tool<Tool.Metadata> {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final LspService lspService;

    public LspTool(LspService lspService) {
        this.lspService = lspService;
    }

    @Override
    public String id() { return "lsp"; }

    @Override
    public String description() {
        return "Query language servers for code intelligence. Actions: definition (go to definition), references (find all references), hover (get info at position), diagnostics (get file errors/warnings).";
    }

    @Override
    public io.opencode.core.tool.util.JsonSchema parameters() {
        return ToolUtils.schema()
            .string("action", "Action to perform: definition, references, hover, diagnostics", true)
            .string("filePath", "Absolute path to the file", true)
            .number("line", "Line number (0-indexed), required for definition/references/hover")
            .number("character", "Character offset (0-indexed), required for definition/references/hover")
            .string("rootPath", "Project root path (defaults to user.dir)")
            .build();
    }

    @Override
    // 执行 LSP 操作：查找文件的 LSP 配置，调用 LspService 执行对应操作，格式化结果
    public ExecuteResult<Metadata> execute(JsonNode args, ToolContext context) {
        var action = args.get("action").asText();
        var filePath = Path.of(args.get("filePath").asText());
        var rootPath = args.has("rootPath") && !args.get("rootPath").isNull()
            ? Path.of(args.get("rootPath").asText())
            : Path.of(System.getProperty("user.dir"));
        var line = args.has("line") ? args.get("line").asInt() : 0;
        var character = args.has("character") ? args.get("character").asInt() : 0;

        if (!java.nio.file.Files.isRegularFile(filePath)) {
            return ExecuteResult.of("File not found: " + filePath, Metadata.EMPTY,
                "Error: file not found at " + filePath);
        }

        try {
            var config = lspService.findConfigFor(filePath);
            if (config.isEmpty()) {
                return ExecuteResult.of("No LSP server found for " + filePath, Metadata.EMPTY,
                    "No language server configured for " + filePath);
            }

            var result = lspService.execute(action, filePath, rootPath, line, character).get();
            var formatted = formatResult(action, result, filePath, line, character);
            return ExecuteResult.of(formatted, Metadata.EMPTY, formatted);
        } catch (Exception e) {
            return ExecuteResult.of("LSP error: " + e.getMessage(), Metadata.EMPTY,
                "LSP " + action + " failed: " + e.getMessage());
        }
    }

    // 根据操作类型格式化 LSP 返回的结果
    private String formatResult(String action, JsonNode result, Path filePath, int line, int character) {
        switch (action) {
            // 跳转到定义：返回文件 URI 和位置
            case "definition" -> {
                if (result == null || result.isNull()) return "No definition found.";
                var uri = result.has("uri") ? result.get("uri").asText() : "";
                var rLine = result.has("range") && result.get("range").has("start")
                    ? result.get("range").get("start").get("line").asInt() : -1;
                var rChar = result.has("range") && result.get("range").has("start")
                    ? result.get("range").get("start").get("character").asInt() : -1;
                return "Definition at " + uri + ":" + rLine + ":" + rChar;
            }
            // 查找所有引用：列出每个引用的位置
            case "references" -> {
                if (result == null || !result.isArray() || result.isEmpty()) return "No references found.";
                var sb = new StringBuilder("Found " + result.size() + " reference(s):\n");
                for (var ref : result) {
                    var uri = ref.get("uri").asText();
                    var rLine = ref.get("range").get("start").get("line").asInt();
                    var rChar = ref.get("range").get("start").get("character").asInt();
                    sb.append("  ").append(uri).append(":").append(rLine).append(":").append(rChar).append("\n");
                }
                return sb.toString();
            }
            // 悬停提示：返回类型信息和文档
            case "hover" -> {
                if (result == null || result.isNull() || !result.has("contents")) return "No hover info.";
                var contents = result.get("contents");
                if (contents.isArray()) {
                    var sb = new StringBuilder();
                    for (var c : contents) {
                        if (c.has("value")) sb.append(c.get("value").asText()).append("\n");
                        else if (c.isTextual()) sb.append(c.asText()).append("\n");
                    }
                    return sb.toString().trim();
                }
                if (contents.has("value")) return contents.get("value").asText();
                return contents.asText();
            }
            // 诊断信息：返回文件的错误和警告
            case "diagnostics" -> {
                if (result == null || !result.has("diagnostics") || !result.get("diagnostics").isArray())
                    return "No diagnostics.";
                var diags = result.get("diagnostics");
                if (diags.isEmpty()) return "No issues found.";
                var sb = new StringBuilder("Found " + diags.size() + " issue(s):\n");
                for (var d : diags) {
                    var severity = d.has("severity") ? severityName(d.get("severity").asInt()) : "?";
                    var message = d.get("message").asText();
                    var dLine = d.has("range") ? d.get("range").get("start").get("line").asInt() : -1;
                    sb.append("  [").append(severity).append("] Line ").append(dLine).append(": ").append(message).append("\n");
                }
                return sb.toString();
            }
            default -> {
                return result != null ? result.toPrettyString() : "No result.";
            }
        }
    }

    // 将 LSP 诊断严重级别数值转换为可读字符串
    private static String severityName(int s) {
        return switch (s) {
            case 1 -> "ERROR";
            case 2 -> "WARNING";
            case 3 -> "INFO";
            case 4 -> "HINT";
            default -> "UNKNOWN";
        };
    }
}
