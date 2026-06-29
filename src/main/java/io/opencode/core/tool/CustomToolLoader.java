package io.opencode.core.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.opencode.core.tool.util.JsonSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

// 自定义工具加载器：从 .opencode/tools 目录加载 JSON 定义的自定义脚本工具
public class CustomToolLoader {
    private static final Logger log = LoggerFactory.getLogger(CustomToolLoader.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Path toolsDir;

    // 构造加载器，指定工作区根目录
    public CustomToolLoader(Path workspaceDir) {
        this.toolsDir = workspaceDir.resolve(".opencode").resolve("tools");
    }

    // 加载默认目录下的所有自定义工具
    public List<Tool> loadTools() {
        return loadTools(toolsDir);
    }

    // 从指定目录加载所有 .json 文件作为自定义工具
    public List<Tool> loadTools(Path dir) {
        var tools = new ArrayList<Tool>();
        if (!Files.isDirectory(dir)) return List.of();
        try (var stream = Files.newDirectoryStream(dir, "*.json")) {
            for (var file : stream) {
                try {
                    var tool = parseToolFile(file);
                    if (tool != null) tools.add(tool);
                } catch (Exception e) {
                    log.warn("Failed to load custom tool from {}: {}", file, e.getMessage());
                }
            }
        } catch (IOException e) {
            log.debug("No custom tools directory at {}", dir);
        }
        return List.copyOf(tools);
    }

    // 解析单个 JSON 工具定义文件，返回 CustomScriptTool 实例
    private Tool parseToolFile(Path file) throws IOException {
        var root = MAPPER.readTree(file.toFile());
        var name = root.get("name");
        if (name == null) name = root.get("id");
        var toolName = name != null ? name.asText() : fileNameWithoutExt(file);
        var description = root.has("description") ? root.get("description").asText("") : "";
        var command = root.has("command") ? root.get("command") : null;
        // command 字段必须存在且为数组
        if (command == null || !command.isArray() || command.isEmpty()) {
            log.warn("Custom tool '{}' missing 'command' array", toolName);
            return null;
        }
        var cmdParts = new String[command.size()];
        for (int i = 0; i < command.size(); i++) cmdParts[i] = command.get(i).asText();
        var schema = buildArgsSchema(root);
        return new CustomScriptTool(toolName, description, cmdParts, schema, file.getParent());
    }

    // 从文件名中去除扩展名
    private static String fileNameWithoutExt(Path file) {
        var name = file.getFileName().toString();
        var dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    // 从 JSON 根节点构建参数 JSON Schema
    private static JsonSchema buildArgsSchema(JsonNode root) {
        var args = root.get("args");
        if (args != null && args.isObject()) {
            var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            var schema = mapper.createObjectNode();
            schema.put("type", "object");
            schema.set("properties", args);
            return JsonSchema.fromNode(schema);
        }
        return JsonSchema.empty();
    }

    // 自定义脚本工具实现：通过 ProcessBuilder 执行 shell 命令
    public static class CustomScriptTool implements Tool<Tool.Metadata> {
        private final String name;           // 工具名称
        private final String description;    // 工具描述
        private final String[] command;      // 命令及参数模板
        private final JsonSchema inputSchema;  // 输入参数 Schema
        private final Path workDir;          // 工作目录

        public CustomScriptTool(String name, String description, String[] command,
                         JsonSchema inputSchema, Path workDir) {
            this.name = name;
            this.description = description;
            this.command = command;
            this.inputSchema = inputSchema;
            this.workDir = workDir;
        }

        @Override
        public String id() { return name; }

        @Override
        public String description() { return description; }

        @Override
        public JsonSchema parameters() {
            return inputSchema;
        }

        @Override
        // 执行命令：将 JSON 参数作为命令的最后一个参数传入，超时 30 秒
        public ExecuteResult<Tool.Metadata> execute(JsonNode args, ToolContext ctx) {
            var cmd = new String[command.length + 1];
            System.arraycopy(command, 0, cmd, 0, command.length);
            cmd[command.length] = args != null ? args.toString() : "";
            try {
                var pb = new ProcessBuilder(cmd);
                pb.directory(workDir.toFile());
                pb.redirectErrorStream(true);
                var process = pb.start();
                process.waitFor(30, TimeUnit.SECONDS);
                var output = new String(process.getInputStream().readAllBytes(),
                    java.nio.charset.StandardCharsets.UTF_8);
                return ExecuteResult.of(command[0], Tool.Metadata.EMPTY, output);
            } catch (IOException e) {
                return ExecuteResult.of(command[0], Tool.Metadata.EMPTY, "Error: " + e.getMessage());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return ExecuteResult.of(command[0], Tool.Metadata.EMPTY, "Interrupted");
            }
        }
    }
}
