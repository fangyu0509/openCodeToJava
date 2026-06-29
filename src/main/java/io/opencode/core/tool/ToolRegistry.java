package io.opencode.core.tool;

import io.opencode.core.agent.AgentConfig;
import io.opencode.core.provider.ModelRef;

import java.util.List;
import java.util.Optional;

// 工具注册中心接口：管理工具的注册、查询和过滤
public interface ToolRegistry {
    // 返回所有已注册工具 ID 的列表
    List<String> ids();
    // 返回所有已注册工具的列表
    List<Tool<?>> all();
    // 根据 ID 查询工具
    Optional<Tool<?>> get(String id);
    // 根据过滤条件筛选可用工具列表
    List<Tool<?>> tools(ToolFilter filter);
    // 注册一个新工具
    void register(Tool<?> tool);
    // 取消注册指定 ID 的工具
    void unregister(String id);

    // 工具过滤条件：包含模型引用和代理配置
    record ToolFilter(ModelRef modelRef, AgentConfig agentConfig) {
    }
}
