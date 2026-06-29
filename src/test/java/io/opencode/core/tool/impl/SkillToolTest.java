package io.opencode.core.tool.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.opencode.core.config.OpenCodeConfig;
import io.opencode.core.skill.SkillService;
import io.opencode.core.tool.ToolContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SkillToolTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private SkillTool tool;
    private ToolContext ctx;

    @BeforeEach
    void setUp() {
        var config = OpenCodeConfig.defaults(java.nio.file.Path.of("/tmp"));
        var skillService = new SkillService(config);
        skillService.loadAllSkills();
        tool = new SkillTool(skillService);
        ctx = new ToolContext(null, null, "test", null, null, List.of(), r -> {}, q -> {});
    }

    @Test
    void returnsErrorForMissingParameter() {
        var result = tool.execute(MAPPER.createObjectNode(), ctx);
        assertTrue(result.output().contains("name"));
    }

    @Test
    void returnsErrorForUnknownSkill() {
        var args = MAPPER.createObjectNode();
        args.put("name", "nonexistent");
        var result = tool.execute(args, ctx);
        assertTrue(result.output().contains("not found"));
    }

    @Test
    void toolIdAndDescription() {
        assertEquals("skill", tool.id());
        assertNotNull(tool.description());
        assertNotNull(tool.parameters());
    }
}
