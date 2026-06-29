package io.opencode.core.tool;

import io.opencode.core.agent.AgentConfig;
import io.opencode.core.provider.ModelRef;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

// 默认工具注册中心实现：基于 ConcurrentHashMap 存储工具，支持按代理角色过滤
@Service
public class DefaultToolRegistry implements ToolRegistry {
    // 线程安全的工具存储映射
    private final Map<String, Tool<?>> tools = new ConcurrentHashMap<>();

    // 只读工具集合：探索和架构师代理只能使用这些工具
    private static final Set<String> READ_ONLY_TOOLS = Set.of(
        "read", "glob", "grep", "lsp", "webfetch", "websearch", "skill"
    );

    // 通用工具集合：普通代理可用的工具
    private static final Set<String> GENERAL_TOOLS = Set.of(
        "read", "write", "glob", "grep", "lsp", "webfetch", "websearch", "skill", "task", "question"
    );

    // 通过工具列表初始化注册中心
    public DefaultToolRegistry(List<Tool<?>> toolList) {
        toolList.forEach(t -> tools.put(t.id(), t));
    }

    @Override
    public List<String> ids() {
        return List.copyOf(tools.keySet());
    }

    @Override
    public List<Tool<?>> all() {
        return List.copyOf(tools.values());
    }

    @Override
    public Optional<Tool<?>> get(String id) {
        return Optional.ofNullable(tools.get(id));
    }

    @Override
    // 根据代理角色过滤可用工具，带点号的工具（自定义工具）始终可用
    public List<Tool<?>> tools(ToolFilter filter) {
        if (filter == null || filter.agentConfig() == null) return all();
        var agentName = filter.agentConfig().name();
        Set<String> allowed;
        switch (agentName) {
            case "explore", "architect" -> allowed = READ_ONLY_TOOLS;
            case "general", "ask" -> allowed = GENERAL_TOOLS;
            default -> { return all(); }
        }
        return tools.values().stream()
            .filter(t -> allowed.contains(t.id()) || t.id().contains("."))
            .toList();
    }

    @Override
    public void register(Tool<?> tool) {
        tools.put(tool.id(), tool);
    }

    @Override
    public void unregister(String id) {
        tools.remove(id);
    }
}
