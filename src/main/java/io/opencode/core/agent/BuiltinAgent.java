package io.opencode.core.agent;

/**
 * 内置代理枚举，定义系统自带的代理类型及其模式与描述。
 * BUILD     - 主代理，有完整文件访问权限
 * PLAN      - 只读规划代理
 * GENERAL   - 通用子代理，用于多步骤任务
 * EXPLORE   - 快速代码库探索（只读）
 * ARCHITECT - 系统架构设计分析（只读）
 * ASK       - 代码库问答与解释
 * COMPACTION - 上下文压缩
 * TITLE     - 对话标题生成
 * SUMMARY   - 对话摘要生成
 */
public enum BuiltinAgent {
    BUILD("build", AgentMode.PRIMARY, "Primary agent with full file access"),
    PLAN("plan", AgentMode.PRIMARY, "Read-only planning agent"),
    GENERAL("general", AgentMode.SUBAGENT, "General-purpose subagent for multi-step tasks"),
    EXPLORE("explore", AgentMode.SUBAGENT, "Fast codebase exploration (read-only)"),
    ARCHITECT("architect", AgentMode.SUBAGENT, "System architecture design and analysis (read-only)"),
    ASK("ask", AgentMode.SUBAGENT, "Codebase Q&A and explanation"),
    COMPACTION("compaction", AgentMode.PRIMARY, "Context compaction"),
    TITLE("title", AgentMode.PRIMARY, "Conversation title generation"),
    SUMMARY("summary", AgentMode.PRIMARY, "Conversation summary generation");

    private final String id;          // 代理唯一标识
    private final AgentMode mode;     // 代理模式
    private final String description; // 代理描述

    BuiltinAgent(String id, AgentMode mode, String description) {
        this.id = id;
        this.mode = mode;
        this.description = description;
    }

    public String id() { return id; }
    public AgentMode mode() { return mode; }
    public String description() { return description; }

    // 根据ID查找内置代理，找不到时抛出异常
    public static BuiltinAgent fromId(String id) {
        for (var a : values()) {
            if (a.id.equals(id)) return a;
        }
        throw new IllegalArgumentException("Unknown agent: " + id);
    }
}
