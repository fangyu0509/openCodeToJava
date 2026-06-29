package io.opencode.core.permission;

// 权限检查器接口，根据工具 ID、资源和规则决定是否允许操作
public interface PermissionChecker {
    // 检查给定工具在指定资源上的权限动作（ALLOW/DENY/ASK）
    PermissionAction check(String toolId, String resource, PermissionRules rules);

    // 便捷方法：检查是否允许（返回 true 当且仅当 check 返回 ALLOW）
    default boolean isAllowed(String toolId, String resource, PermissionRules rules) {
        return check(toolId, resource, rules) == PermissionAction.ALLOW;
    }
}
