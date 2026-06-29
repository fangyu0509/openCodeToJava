package io.opencode.server;

import io.opencode.core.mcp.McpService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

// MCP（Model Context Protocol）服务器管理 REST 控制器，支持列出、启动和停止 MCP 服务器
@RestController
@RequestMapping("/api/mcp")
@CrossOrigin(originPatterns = "*", allowedHeaders = "*")
public class McpController {

    private final McpService mcpService;

    public McpController(McpService mcpService) {
        this.mcpService = mcpService;
    }

    // 列出所有运行中的 MCP 服务器
    @GetMapping("/servers")
    public List<String> listServers() {
        return mcpService.listServers();
    }

    // 启动一个 MCP 服务器，需提供名称和启动命令
    @PostMapping("/servers")
    public Map<String, String> startServer(@RequestBody Map<String, Object> body) {
        var name = (String) body.get("name");
        var command = (String) body.get("command");
        if (name == null || command == null) {
            return Map.of("error", "name and command are required");
        }
        @SuppressWarnings("unchecked")
        var args = (List<String>) body.getOrDefault("args", List.of());
        @SuppressWarnings("unchecked")
        var env = (Map<String, String>) body.getOrDefault("env", Map.of());
        mcpService.startServer(new McpService.ServerConfig(name, command, args, env));
        return Map.of("status", "started", "name", name);
    }

    // 停止指定名称的 MCP 服务器
    @DeleteMapping("/servers/{name}")
    public Map<String, String> stopServer(@PathVariable String name) {
        mcpService.stopServer(name);
        return Map.of("status", "stopped", "name", name);
    }
}
