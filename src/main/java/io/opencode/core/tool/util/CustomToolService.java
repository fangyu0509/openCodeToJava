package io.opencode.core.tool.util;

import io.opencode.core.tool.CustomToolLoader;
import io.opencode.core.tool.Tool;
import io.opencode.core.tool.ToolRegistry;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.file.Path;

// 自定义工具服务：在应用启动时加载并注册自定义工具，提供重载功能
@Service
public class CustomToolService {
    private static final Logger log = LoggerFactory.getLogger(CustomToolService.class);

    private final ToolRegistry toolRegistry;
    private final Path toolsDir;  // 自定义工具目录

    public CustomToolService(ToolRegistry toolRegistry) {
        this.toolRegistry = toolRegistry;
        this.toolsDir = Path.of(System.getProperty("user.dir"))
            .resolve(".opencode").resolve("tools");
    }

    // 应用启动后自动加载自定义工具
    @PostConstruct
    public void load() {
        var loader = new CustomToolLoader(toolsDir.getParent().getParent());
        var tools = loader.loadTools(toolsDir);
        for (var tool : tools) {
            toolRegistry.register(tool);
            log.info("Registered custom tool: {}", tool.id());
        }
        if (!tools.isEmpty()) {
            log.info("Loaded {} custom tool(s)", tools.size());
        }
    }

    // 重新加载自定义工具：先注销所有 CustomScriptTool，再重新加载
    public int reload() {
        for (var t : toolRegistry.all()) {
            if (t instanceof CustomToolLoader.CustomScriptTool) {
                toolRegistry.unregister(t.id());
            }
        }
        var loader = new CustomToolLoader(toolsDir.getParent().getParent());
        var tools = loader.loadTools(toolsDir);
        for (var tool : tools) {
            toolRegistry.register(tool);
        }
        return tools.size();
    }

    public Path toolsDir() { return toolsDir; }
}
