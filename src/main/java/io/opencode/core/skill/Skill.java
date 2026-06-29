package io.opencode.core.skill;

import java.nio.file.Path;

/**
 * 技能记录，表示一个可加载的技能（Skill），包含名称、描述、来源路径和正文内容
 * 技能通常以 Markdown 文件（SKILL.md）或 .skill 包的形式存在，为 AI 代理提供领域知识和指令
 */
public record Skill(
    String name,        // 技能名称
    String description, // 技能描述
    Path path,          // 技能文件或包的路径
    String body         // 技能正文内容（即 Markdown 正文部分）
) {}
