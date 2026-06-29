package io.opencode.core.permission;

// 权限规则记录：包含一个 glob 模式及对应的动作
public record PermissionRule(
    String pattern,       // glob 匹配模式，如 "**/*.java"
    PermissionAction action // 匹配后执行的动作
) {
    // 紧凑构造器：pattern 不能为空，action 默认为 DENY
    public PermissionRule {
        if (pattern == null || pattern.isBlank()) {
            throw new IllegalArgumentException("pattern must not be blank");
        }
        if (action == null) {
            action = PermissionAction.DENY;
        }
    }

    // 创建一条允许规则
    public static PermissionRule allow(String pattern) {
        return new PermissionRule(pattern, PermissionAction.ALLOW);
    }

    // 创建一条拒绝规则
    public static PermissionRule deny(String pattern) {
        return new PermissionRule(pattern, PermissionAction.DENY);
    }

    // 创建一条询问规则（需要用户确认）
    public static PermissionRule ask(String pattern) {
        return new PermissionRule(pattern, PermissionAction.ASK);
    }
}
