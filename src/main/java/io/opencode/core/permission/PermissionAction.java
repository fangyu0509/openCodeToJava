package io.opencode.core.permission;

// 权限动作枚举：允许、拒绝、询问用户
public enum PermissionAction {
    ALLOW, // 允许执行
    DENY,  // 拒绝执行
    ASK    // 需要用户确认
}
