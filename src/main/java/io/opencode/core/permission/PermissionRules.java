package io.opencode.core.permission;

import java.util.List;

// 权限规则集合，按操作类别（读/编辑/Shell/外部目录/Task）分组
public record PermissionRules(
    List<PermissionRule> read,              // 文件读取类操作（read/glob/grep）
    List<PermissionRule> edit,              // 文件写入类操作（write/edit/apply_patch）
    List<PermissionRule> shell,             // Shell 命令执行
    List<PermissionRule> externalDirectory, // 外部目录访问
    List<PermissionRule> task               // 子任务调度
) {
    // 紧凑构造器：所有字段防御性复制为不可变列表，null 视为空列表
    public PermissionRules {
        read = read != null ? List.copyOf(read) : List.of();
        edit = edit != null ? List.copyOf(edit) : List.of();
        shell = shell != null ? List.copyOf(shell) : List.of();
        externalDirectory = externalDirectory != null ? List.copyOf(externalDirectory) : List.of();
        task = task != null ? List.copyOf(task) : List.of();
    }

    // 默认规则：所有操作均无规则（最终走 DENY）
    public static PermissionRules defaultRules() {
        return new PermissionRules(List.of(), List.of(), List.of(), List.of(), List.of());
    }

    // 严格模式：只允许读取和 task，拒绝写入、Shell 和外部目录
    public static PermissionRules strict() {
        return new PermissionRules(
            List.of(PermissionRule.allow("**/*")),
            List.of(PermissionRule.deny("**/*")),
            List.of(PermissionRule.deny("**/*")),
            List.of(PermissionRule.deny("**/*")),
            List.of(PermissionRule.allow("**/*"))
        );
    }

    // 宽松模式：允许读写和 task，Shell 和外部目录需用户确认
    public static PermissionRules permissive() {
        return new PermissionRules(
            List.of(PermissionRule.allow("**/*")),
            List.of(PermissionRule.allow("**/*")),
            List.of(PermissionRule.ask("**/*")),
            List.of(PermissionRule.ask("**/*")),
            List.of(PermissionRule.allow("**/*"))
        );
    }
}
