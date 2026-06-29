package io.opencode.core.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.opencode.core.tool.ExecuteResult;
import io.opencode.core.tool.Tool;
import io.opencode.core.tool.ToolContext;
import io.opencode.core.tool.ToolRegistry;
import io.opencode.core.tool.util.JsonSchema;
import io.opencode.core.config.OpenCodeConfig;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

// MCP 服务管理器：管理多个 MCP 服务器的生命周期，并将远程工具注册到 ToolRegistry
@Service
public class McpService {
    private static final Logger log = LoggerFactory.getLogger(McpService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ToolRegistry toolRegistry;           // 工具注册中心
    private final OpenCodeConfig openCodeConfig;       // 全局配置（读取 mcpServers 列表）
    private final Map<String, McpClient> clients = new ConcurrentHashMap<>();  // 已启动的客户端

    public record ServerConfig(String name, String command, List<String> args, Map<String, String> env) {}

    public McpService(ToolRegistry toolRegistry, OpenCodeConfig openCodeConfig) {
        this.toolRegistry = toolRegistry;
        this.openCodeConfig = openCodeConfig;
    }

    // 应用启动时自动启动配置中声明的所有 MCP 服务器
    @PostConstruct
    public void startConfiguredServers() {
        for (var cfg : openCodeConfig.mcpServers()) {
            var config = new ServerConfig(cfg.name(), cfg.command(), cfg.args(), cfg.env());
            CompletableFuture.runAsync(() -> startServer(config))
                .orTimeout(10, TimeUnit.SECONDS)
                .exceptionally(ex -> {
                    log.warn("Failed to auto-start MCP server '{}': {}", config.name(), ex.getMessage());
                    return null;
                });
        }
    }

    // 启动一个 MCP 服务器：创建客户端、初始化、获取工具列表并注册
    public void startServer(ServerConfig config) {
        var key = config.name();
        if (clients.containsKey(key)) {
            log.warn("MCP server '{}' already running", key);
            return;
        }
        try {
            var client = new McpClient(config.name(), config.command(),
                config.args().toArray(String[]::new));
            client.initialize().get();
            var tools = client.listTools().get();
            clients.put(key, client);
            log.info("Started MCP server '{}' with {} tool(s)", key, tools.size());

            // 将 MCP 工具包装为代理对象注册到工具注册中心
            for (var toolInfo : tools) {
                var proxy = new McpToolProxy(key, toolInfo, client);
                toolRegistry.register(proxy);
                log.info("  Registered MCP tool '{}.{}'", key, toolInfo.name());
            }
        } catch (Exception e) {
            log.error("Failed to start MCP server '{}': {}", key, e.getMessage());
        }
    }

    // 停止指定的 MCP 服务器并撤销其工具注册
    public void stopServer(String name) {
        var client = clients.remove(name);
        if (client != null) {
            unregisterTools(name);
            client.close();
            log.info("Stopped MCP server '{}'", name);
        }
    }

    // 按服务器名前缀注销所有关联的工具
    private void unregisterTools(String serverName) {
        var prefix = serverName + ".";
        for (var id : toolRegistry.ids()) {
            if (id.startsWith(prefix)) {
                toolRegistry.unregister(id);
            }
        }
    }

    // 列出运行中的服务器名称
    public List<String> listServers() {
        return List.copyOf(clients.keySet());
    }

    // 检查指定名称的服务器是否在运行
    public boolean isRunning(String name) {
        var client = clients.get(name);
        return client != null && client.isAlive();
    }

    // 应用关闭时停止所有 MCP 服务器
    @PreDestroy
    public void closeAll() {
        for (var entry : clients.entrySet()) {
            unregisterTools(entry.getKey());
            entry.getValue().close();
        }
        clients.clear();
    }

    // MCP 工具代理：将远程 MCP 工具适配为本地 Tool 接口
    private static class McpToolProxy implements Tool<Tool.Metadata> {
        private final String serverName;
        private final McpClient client;
        private final McpClient.ToolInfo toolInfo;

        McpToolProxy(String serverName, McpClient.ToolInfo toolInfo, McpClient client) {
            this.serverName = serverName;
            this.toolInfo = toolInfo;
            this.client = client;
        }

        @Override
        public String id() { return serverName + "." + toolInfo.name(); }

        @Override
        public String description() { return "[MCP " + serverName + "] " + toolInfo.description(); }

        @Override
        public JsonSchema parameters() {
            if (toolInfo.inputSchema() != null && !toolInfo.inputSchema().isNull()) {
                return JsonSchema.fromNode(toolInfo.inputSchema());
            }
            return JsonSchema.empty();
        }

        @Override
        // 执行 MCP 工具调用：通过客户端远程调用并解析返回结果
        public ExecuteResult<Metadata> execute(JsonNode args, ToolContext context) {
            try {
                var result = client.callTool(toolInfo.name(), args).get();
                var content = parseContent(result);
                return ExecuteResult.of(content, Metadata.EMPTY, content);
            } catch (Exception e) {
                var err = "MCP tool '" + id() + "' failed: " + e.getMessage();
                return ExecuteResult.of(err, Metadata.EMPTY, err);
            }
        }

        // 解析 MCP 工具响应中的 content 数组为纯文本
        private String parseContent(JsonNode result) {
            var content = result.get("content");
            if (content == null || !content.isArray()) return result.toPrettyString();
            var sb = new StringBuilder();
            for (var item : content) {
                var type = item.has("type") ? item.get("type").asText() : "text";
                switch (type) {
                    case "text" -> sb.append(item.has("text") ? item.get("text").asText() : "");
                    case "resource" -> sb.append("[Resource: ").append(item.get("resource").asText()).append("]");
                    default -> sb.append(item.toPrettyString());
                }
                sb.append("\n");
            }
            return sb.toString().trim();
        }
    }
}
