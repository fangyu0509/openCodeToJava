package io.opencode.core.agent;

/**
 * 代理模式枚举：
 * SUBAGENT - 子代理，用于多步骤子任务
 * PRIMARY  - 主代理，具有完整文件访问权限
 * ALL      - 全部模式
 * PLAN     - 仅规划模式（只读），只生成执行计划不执行工具
 */
public enum AgentMode {
    SUBAGENT,
    PRIMARY,
    ALL,
    PLAN
}
