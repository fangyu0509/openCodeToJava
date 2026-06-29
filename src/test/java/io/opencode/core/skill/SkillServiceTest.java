package io.opencode.core.skill;

import io.opencode.core.config.OpenCodeConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class SkillServiceTest {

    @Test
    void loadsSkillsFromDataDir(@TempDir Path tempDir) throws Exception {
        var skillsDir = tempDir.resolve(".opencode").resolve("skills");
        Files.createDirectories(skillsDir);
        var skillFile = skillsDir.resolve("SKILL.md");
        Files.writeString(skillFile, """
            ---
            name: my-skill
            description: My skill
            ---
            Body
            """);
        var config = OpenCodeConfig.defaults(tempDir);
        var service = new SkillService(config);
        service.loadAllSkills();

        var skill = service.getSkill("my-skill");
        assertTrue(skill.isPresent());
        assertEquals("my-skill", skill.get().name());
    }

    @Test
    void returnsEmptyWhenNoSkillsConfigured() {
        var config = OpenCodeConfig.defaults(Path.of("/tmp"));
        var service = new SkillService(config);
        service.loadAllSkills();
        assertTrue(service.getSkill("nonexistent").isEmpty());
    }

    @Test
    void getSkillByName() {
        var config = OpenCodeConfig.defaults(Path.of("/tmp"));
        var service = new SkillService(config);
        service.loadAllSkills();
        assertTrue(service.getSkill("nonexistent").isEmpty());
    }

    @Test
    void getSkillBody() {
        var config = OpenCodeConfig.defaults(Path.of("/tmp"));
        var service = new SkillService(config);
        service.loadAllSkills();
        assertTrue(service.getSkillBody("nonexistent").isEmpty());
    }
}
