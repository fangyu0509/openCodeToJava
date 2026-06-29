package io.opencode.core.permission;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

// 基于 glob 模式匹配的权限检查器，按工具 ID 选择对应规则列表进行匹配
@Component
public class GlobPermissionChecker implements PermissionChecker {
    @Override
    // 根据工具 ID 将操作映射到对应的规则列表，并返回首个匹配结果
    public PermissionAction check(String toolId, String resource, PermissionRules rules) {
        // 将工具 ID 映射到权限规则分类
        var ruleList = switch (toolId) {
            case "read", "glob", "grep" -> rules.read();
            case "write", "edit", "apply_patch" -> rules.edit();
            case "shell" -> rules.shell();
            case "task" -> rules.task();
            default -> rules.externalDirectory();
        };
        return matchFirst(ruleList, resource);
    }

    // 顺序遍历规则，返回第一个匹配规则的 action；无匹配则默认 DENY
    private PermissionAction matchFirst(List<PermissionRule> rules, String resource) {
        for (var rule : rules) {
            if (globMatches(rule.pattern(), resource)) {
                return rule.action();
            }
        }
        return PermissionAction.DENY;
    }

    // 判断 glob 模式是否匹配给定路径
    static boolean globMatches(String pattern, String path) {
        var regex = toRegex(pattern);
        return Pattern.matches(regex, path);
    }

    // 将 glob 模式转换为正则表达式
    // 支持：**（跨目录匹配）、*（单层匹配）、?（单个字符）
    private static String toRegex(String glob) {
        var sb = new StringBuilder("^");
        for (int i = 0; i < glob.length(); i++) {
            char c = glob.charAt(i);
            switch (c) {
                case '*' -> {
                    // ** 匹配任意路径（包括 /），* 只匹配当前目录
                    if (i + 1 < glob.length() && glob.charAt(i + 1) == '*') {
                        sb.append(".*");
                        i++;
                        if (i + 1 < glob.length() && glob.charAt(i + 1) == '/') i++;
                    } else {
                        sb.append("[^/]*");
                    }
                }
                case '?' -> sb.append("[^/]");  // ? 匹配单个非路径分隔符字符
                case '.' -> sb.append("\\.");   // 转义 .
                case '/' -> sb.append("/");
                default -> sb.append(c);
            }
        }
        sb.append("$");
        return sb.toString();
    }
}
