package io.opencode.core.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.opencode.core.agent.AgentMode;
import io.opencode.core.config.ReferenceConfig;
import io.opencode.core.provider.ModelRef;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

// 配置加载器，负责从文件系统和用户全局目录加载并合并 opencode 配置
@Component
public class ConfigLoader {
    private static final Logger log = LoggerFactory.getLogger(ConfigLoader.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    // 全局配置文件路径：~/.config/opencode/opencode.json
    private static final Path GLOBAL_CONFIG = java.nio.file.Path.of(
        System.getProperty("user.home"), ".config", "opencode", "opencode.json");

    // 加载配置：优先加载项目目录下的 opencode.json 或 opencode.jsonc，然后与全局配置合并
    public OpenCodeConfig load(Path workspaceDir) {
        var jsonFile = workspaceDir.resolve("opencode.json");
        var jsoncFile = workspaceDir.resolve("opencode.jsonc");

        Path configFile = null;
        // 按优先级选择配置文件：.json > .jsonc
        if (Files.exists(jsonFile)) {
            configFile = jsonFile;
        } else if (Files.exists(jsoncFile)) {
            configFile = jsoncFile;
        }

        JsonNode root = null;
        // 解析项目配置文件
        if (configFile != null) {
            try {
                root = MAPPER.readTree(Files.readString(configFile));
            } catch (IOException e) {
                log.warn("Failed to parse {}: {}", configFile, e.getMessage());
            }
        }

        // 与全局配置合并，全局配置作为 base，项目配置作为 override
        if (Files.exists(GLOBAL_CONFIG)) {
            try {
                var globalRoot = MAPPER.readTree(Files.readString(GLOBAL_CONFIG));
                if (root != null) {
                    root = mergeConfigs(globalRoot, root);
                } else {
                    root = globalRoot;
                }
                log.info("Loaded global config from {}", GLOBAL_CONFIG);
            } catch (IOException e) {
                log.warn("Failed to parse global config {}: {}", GLOBAL_CONFIG, e.getMessage());
            }
        }

        // 无配置时返回默认配置
        if (root == null) {
            log.info("No opencode.json found in {} or {}", workspaceDir, GLOBAL_CONFIG);
            return OpenCodeConfig.defaults(workspaceDir);
        }

        return parse(root, workspaceDir);
    }

    // 递归合并两个 JSON 节点，数组类字段（providers/agents/mcpServers）完全覆盖
    private JsonNode mergeConfigs(JsonNode base, JsonNode override) {
        if (base.isObject() && override.isObject()) {
            var merged = MAPPER.createObjectNode();
            base.fieldNames().forEachRemaining(k -> merged.set(k, base.get(k)));
            override.fieldNames().forEachRemaining(k -> {
                if (k.equals("providers") || k.equals("agents") || k.equals("mcpServers")) {
                    // 数组字段完全覆盖而非合并
                    merged.set(k, override.get(k));
                } else if (base.has(k) && base.get(k).isObject() && override.get(k).isObject()) {
                    // 双方均为对象则递归合并
                    merged.set(k, mergeConfigs(base.get(k), override.get(k)));
                } else {
                    // 基本类型或一方非对象时以 override 为准
                    merged.set(k, override.get(k));
                }
            });
            return merged;
        }
        return override;
    }

    // 将解析后的 JSON 树转换为 OpenCodeConfig 对象
    private OpenCodeConfig parse(JsonNode root, Path workspaceDir) {
        // 版本号（兼容 $schema 字段）
        var version = root.has("$schema") ? root.get("$schema").asText() : "0.1.0";
        var defaultModel = root.has("model") ? Optional.of(parseModelRef(root.get("model").asText()))
            : Optional.<ModelRef>empty();

        // 解析提供商配置
        List<OpenCodeConfig.ProviderConfig> providers = new ArrayList<>();
        if (root.has("provider") && root.get("provider").isObject()) {
            var provObj = root.get("provider");
            provObj.fieldNames().forEachRemaining(id -> {
                var p = provObj.get(id);
                // API 密钥环境变量名：默认 <ID>_API_KEY
                var apiKeyEnv = p.has("env") && p.get("env").isArray()
                    ? p.get("env").get(0).asText() : id.toUpperCase() + "_API_KEY";
                var apiKey = p.has("options") && p.get("options").has("apiKey")
                    ? Optional.of(p.get("options").get("apiKey").asText()) : Optional.<String>empty();
                var baseUrl = p.has("options") && p.get("options").has("baseURL")
                    ? Optional.of(p.get("options").get("baseURL").asText()) : Optional.<String>empty();
                // 默认使用该提供商的第一个模型
                var pm = p.has("models") ? Optional.of(ModelRef.of(id, p.get("models").fieldNames().next()))
                    : Optional.<ModelRef>empty();
                providers.add(new OpenCodeConfig.ProviderConfig(id, apiKeyEnv, apiKey, baseUrl, pm));
            });
        }

        // 服务器端口和主机
        var serverPort = root.has("server") && root.get("server").has("port")
            ? root.get("server").get("port").asInt() : 4096;
        var serverHost = root.has("server") && root.get("server").has("hostname")
            ? root.get("server").get("hostname").asText() : "127.0.0.1";

        var telemetry = root.has("telemetry") && root.get("telemetry").asBoolean();

        // 会话压缩配置
        var compactThreshold = root.has("compaction") && root.get("compaction").has("threshold")
            ? root.get("compaction").get("threshold").asInt(100_000) : 100_000;
        var compactReserved = root.has("compaction") && root.get("compaction").has("reserved")
            ? root.get("compaction").get("reserved").asInt(5) : 5;

        // 解析 MCP 服务器配置（命令、参数、环境变量）
        List<OpenCodeConfig.McpServerConfig> mcpServers = new ArrayList<>();
        if (root.has("mcpServers") && root.get("mcpServers").isObject()) {
            var mcpObj = root.get("mcpServers");
            mcpObj.fieldNames().forEachRemaining(name -> {
                var s = mcpObj.get(name);
                var command = s.has("command") ? s.get("command").asText() : "";
                var args = s.has("args") && s.get("args").isArray()
                    ? java.util.stream.StreamSupport.stream(s.get("args").spliterator(), false)
                        .map(JsonNode::asText).toList()
                    : List.<String>of();
                Map<String, String> env = new HashMap<>();
                if (s.has("env") && s.get("env").isObject()) {
                    s.get("env").fieldNames().forEachRemaining(k -> env.put(k, s.get("env").get(k).asText()));
                }
                mcpServers.add(new OpenCodeConfig.McpServerConfig(name, command, args, env));
            });
        }

        return new OpenCodeConfig(
            version, workspaceDir,
            Optional.of(workspaceDir.resolve(".opencode")),
            defaultModel, providers, List.of(),
            new OpenCodeConfig.ServerConfig(serverPort, serverHost),
            mcpServers,
            telemetry,
            compactThreshold,
            compactReserved,
            loadReferences(root)  // 加载 references 部分
        );
    }

    // 从 JSON 中提取 references 对象，每个 key 为一个引用配置
    private List<ReferenceConfig> loadReferences(JsonNode json) {
        var refs = json.get("references");
        if (refs == null || !refs.isObject()) return List.of();
        var result = new java.util.ArrayList<ReferenceConfig>();
        var fields = refs.fields();
        while (fields.hasNext()) {
            var entry = fields.next();
            var name = entry.getKey();
            var node = entry.getValue();
            var path = node.has("path") ? java.util.Optional.of(java.nio.file.Path.of(node.get("path").asText())) : java.util.Optional.<java.nio.file.Path>empty();
            var gitRepo = node.has("gitRepo") || node.has("git") ? java.util.Optional.of(node.has("gitRepo") ? node.get("gitRepo").asText() : node.get("git").asText()) : java.util.Optional.<String>empty();
            var patterns = node.has("patterns") ? fromJsonArray(node.get("patterns")) : java.util.List.<String>of();
            var description = node.has("description") ? node.get("description").asText("") : "";
            result.add(new ReferenceConfig(name, description, path, gitRepo, patterns));
        }
        return java.util.List.copyOf(result);
    }

    // 将 JSON 数组转为 Java List<String>
    private static java.util.List<String> fromJsonArray(JsonNode arr) {
        if (arr == null || !arr.isArray()) return java.util.List.of();
        var list = new java.util.ArrayList<String>();
        for (var e : arr) list.add(e.asText());
        return java.util.List.copyOf(list);
    }

    // 解析模型引用表达式，格式为 "providerId/modelId"
    private ModelRef parseModelRef(String expr) {
        var parts = expr.split("/", 2);
        if (parts.length == 2) {
            return ModelRef.of(parts[0], parts[1]);
        }
        return ModelRef.of(parts[0], "default");
    }
}
