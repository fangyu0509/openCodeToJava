package io.opencode.core.plugin;

import io.opencode.core.session.Message;
import io.opencode.core.session.Session;
import io.opencode.core.tool.Tool;
import io.opencode.core.tool.ToolRegistry;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 插件管理器，负责插件的注册、卸载和事件分发。
 * 在 Spring 启动时自动加载所有 Plugin Bean，并向 ToolRegistry 注册其工具。
 * 提供 fireXxx 系列方法遍历所有插件触发对应的事件回调。
 */
@Component
public class PluginManager {
    private static final Logger log = LoggerFactory.getLogger(PluginManager.class);

    private final Map<String, Plugin> plugins = new ConcurrentHashMap<>(); // 插件名 -> 插件实例
    private final ToolRegistry toolRegistry; // 工具注册表
    private final List<Plugin> pluginBeans;  // Spring 自动注入的所有 Plugin Bean

    public PluginManager(ToolRegistry toolRegistry, List<Plugin> pluginBeans) {
        this.toolRegistry = toolRegistry;
        this.pluginBeans = pluginBeans;
    }

    // Spring 初始化后自动加载所有插件 Bean
    @PostConstruct
    public void loadPlugins() {
        for (var plugin : pluginBeans) {
            register(plugin);
        }
        log.info("Loaded {} plugin(s)", plugins.size());
    }

    // 注册一个插件：调用 onLoad，注册其所有工具，存入插件列表
    public void register(Plugin plugin) {
        var name = plugin.name();
        if (plugins.containsKey(name)) {
            log.warn("Plugin '{}' already registered, skipping", name);
            return;
        }
        try {
            plugin.onLoad();
            for (var tool : plugin.tools()) {
                toolRegistry.register(tool);
                log.info("  Registered tool '{}' from plugin '{}'", tool.id(), name);
            }
            plugins.put(name, plugin);
            log.info("Loaded plugin: {} v{}", name, plugin.version());
        } catch (Exception e) {
            log.error("Failed to load plugin '{}': {}", name, e.getMessage());
        }
    }

    // 卸载插件：从工具注册表移除其工具，调用 onUnload，从插件列表移除
    public void unload(String name) {
        var plugin = plugins.remove(name);
        if (plugin != null) {
            try {
                for (var tool : plugin.tools()) {
                    toolRegistry.unregister(tool.id());
                }
                plugin.onUnload();
                log.info("Unloaded plugin: {}", name);
            } catch (Exception e) {
                log.error("Failed to unload plugin '{}': {}", name, e.getMessage());
            }
        }
    }

    // 列出所有已加载的插件信息
    public List<PluginInfo> listPlugins() {
        var result = new ArrayList<PluginInfo>();
        for (var entry : plugins.entrySet()) {
            var p = entry.getValue();
            result.add(new PluginInfo(p.name(), p.version(),
                p.tools().stream().map(Tool::id).toList()));
        }
        return result;
    }

    // 向所有插件广播会话开始事件
    public void fireSessionStart(Session session) {
        for (var p : plugins.values()) {
            try { p.onSessionStart(session); } catch (Exception e) {
                log.warn("Plugin '{}' onSessionStart error: {}", p.name(), e.getMessage());
            }
        }
    }

    // 向所有插件广播消息事件
    public void fireMessage(Session session, Message message) {
        for (var p : plugins.values()) {
            try { p.onMessage(session, message); } catch (Exception e) {
                log.warn("Plugin '{}' onMessage error: {}", p.name(), e.getMessage());
            }
        }
    }

    // 向所有插件广播工具即将执行事件
    public void fireToolExecute(String toolId, Map<String, Object> args) {
        for (var p : plugins.values()) {
            try { p.onToolExecute(toolId, args); } catch (Exception e) {
                log.warn("Plugin '{}' onToolExecute error: {}", p.name(), e.getMessage());
            }
        }
    }

    // 向所有插件广播工具执行结果事件
    public void fireToolResult(String toolId, String output) {
        for (var p : plugins.values()) {
            try { p.onToolResult(toolId, output); } catch (Exception e) {
                log.warn("Plugin '{}' onToolResult error: {}", p.name(), e.getMessage());
            }
        }
    }

    // 向所有插件广播错误事件
    public void fireError(Session session, String error) {
        for (var p : plugins.values()) {
            try { p.onError(session, error); } catch (Exception e) {
                log.warn("Plugin '{}' onError error: {}", p.name(), e.getMessage());
            }
        }
    }

    // 插件信息记录，包含名称、版本和工具ID列表
    public record PluginInfo(String name, String version, List<String> tools) {}
}
