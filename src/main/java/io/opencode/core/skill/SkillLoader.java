package io.opencode.core.skill;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 技能加载器，负责从 SKILL.md 文件或 .skill 包中解析加载技能
 * 技能文件格式为 YAML 风格的 frontmatter（--- 分隔）后跟 Markdown 正文
 */
public class SkillLoader {
    private static final Logger log = LoggerFactory.getLogger(SkillLoader.class);

    /**
     * 从指定路径的 SKILL.md 文件加载技能
     * 如果文件不存在或格式无效，返回 Optional.empty()
     */
    public static Optional<Skill> load(Path skillFile) {
        try {
            if (!Files.isRegularFile(skillFile)) return Optional.empty();
            var content = Files.readString(skillFile, StandardCharsets.UTF_8).strip();
            return parseContent(content, skillFile);
        } catch (IOException e) {
            log.debug("Failed to load skill from {}: {}", skillFile, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * 从给定的文本内容加载技能（通常用于从 .skill 包中读取的 ZIP 条目内容）
     *
     * @param content 技能内容文本
     * @param source  来源路径（用于显示错误信息）
     */
    public static Optional<Skill> loadContent(String content, Path source) {
        return parseContent(content.strip(), source);
    }

    /**
     * 解析 frontmatter + Markdown 正文的技能文件格式
     * 格式：
     *   ---
     *   name: skill-name
     *   description: ...
     *   ---
     *   Markdown 正文内容...
     *
     * 必须包含 name 字段才会被识别为有效技能
     */
    private static Optional<Skill> parseContent(String content, Path source) {
        if (!content.startsWith("---")) return Optional.empty();
        var end = content.indexOf("---", 3);
        if (end < 0) return Optional.empty();

        var frontmatter = content.substring(3, end).strip();
        var body = content.substring(end + 3).strip();
        var name = parseField(frontmatter, "name");
        var description = parseField(frontmatter, "description");
        if (name.isEmpty()) {
            log.debug("Skill at {} has no name in frontmatter", source);
            return Optional.empty();
        }
        return Optional.of(new Skill(name.get(), description.orElse(""), source, body));
    }

    /**
     * 从 frontmatter 中解析指定字段的值
     * 格式为 "fieldName: value"，支持引号包裹的值
     */
    private static Optional<String> parseField(String frontmatter, String field) {
        return frontmatter.lines()
            .filter(l -> l.startsWith(field + ":"))
            .map(l -> l.substring(field.length() + 1).strip().replaceAll("^\"|\"$", ""))
            .findFirst();
    }
}
