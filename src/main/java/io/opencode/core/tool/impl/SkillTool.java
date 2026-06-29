package io.opencode.core.tool.impl;

import com.fasterxml.jackson.databind.JsonNode;
import io.opencode.core.skill.SkillService;
import io.opencode.core.tool.ExecuteResult;
import io.opencode.core.tool.Tool;
import io.opencode.core.tool.ToolContext;
import io.opencode.core.tool.util.JsonSchema;
import io.opencode.core.tool.util.ToolUtils;
import org.springframework.stereotype.Component;

// 技能加载工具：根据名称加载技能指令到当前对话中
@Component
public class SkillTool implements Tool<Tool.Metadata> {

    private final SkillService skillService;

    public SkillTool(SkillService skillService) {
        this.skillService = skillService;
    }

    @Override
    public String id() { return "skill"; }

    @Override
    public String description() {
        return "Load a skill's instructions into the conversation. " +
            "Use this when a user request matches a skill's purpose. " +
            "The 'name' parameter should match a skill listed in the system prompt. " +
            "Returns the full skill instructions.";
    }

    @Override
    public JsonSchema parameters() {
        return ToolUtils.schema()
            .string("name", "The name of the skill to load", true)
            .build();
    }

    @Override
    // 执行加载：查找指定技能，如果不存在则列出可用技能供参考
    public ExecuteResult<Metadata> execute(JsonNode args, ToolContext context) {
        var name = args.has("name") ? args.get("name").asText() : "";
        if (name.isBlank()) {
            return ExecuteResult.of("Error: 'name' parameter is required", Metadata.EMPTY, "name parameter required");
        }
        var skillOpt = skillService.getSkill(name);
        if (skillOpt.isEmpty()) {
            var available = skillService.listSkills().stream()
                .map(s -> s.name()).toList();
            return ExecuteResult.of(
                "Skill '" + name + "' not found. Available skills: " + available,
                Metadata.EMPTY,
                "skill not found: " + name);
        }
        var skill = skillOpt.get();
        var content = "## Loaded Skill: " + skill.name() + "\n\n" + skill.body();
        return ExecuteResult.of(content, Metadata.EMPTY, content);
    }
}
