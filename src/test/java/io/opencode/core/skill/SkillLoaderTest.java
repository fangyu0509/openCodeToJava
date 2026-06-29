package io.opencode.core.skill;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class SkillLoaderTest {

    @Test
    void loadsSkillFromFile(@TempDir Path tempDir) throws Exception {
        var skillFile = tempDir.resolve("SKILL.md");
        Files.writeString(skillFile, """
            ---
            name: test-skill
            description: A test skill
            ---
            # Test Skill
            This is the skill body.
            """);
        var result = SkillLoader.load(skillFile);
        assertTrue(result.isPresent());
        var skill = result.get();
        assertEquals("test-skill", skill.name());
        assertEquals("A test skill", skill.description());
        assertEquals(skillFile, skill.path());
        assertTrue(skill.body().contains("This is the skill body."));
    }

    @Test
    void returnsEmptyForMissingFrontmatter(@TempDir Path tempDir) throws Exception {
        var skillFile = tempDir.resolve("SKILL.md");
        Files.writeString(skillFile, "# No frontmatter");
        assertTrue(SkillLoader.load(skillFile).isEmpty());
    }

    @Test
    void returnsEmptyForMissingFile() {
        assertTrue(SkillLoader.load(Path.of("/nonexistent/SKILL.md")).isEmpty());
    }

    @Test
    void returnsEmptyForMissingName(@TempDir Path tempDir) throws Exception {
        var skillFile = tempDir.resolve("SKILL.md");
        Files.writeString(skillFile, """
            ---
            description: no name
            ---
            Body
            """);
        assertTrue(SkillLoader.load(skillFile).isEmpty());
    }

    @Test
    void loadsFromContentString() {
        var result = SkillLoader.loadContent("""
            ---
            name: inline
            description: Inline skill
            ---
            Inline body
            """.strip(), Path.of("test"));
        assertTrue(result.isPresent());
        assertEquals("inline", result.get().name());
    }

    @Test
    void handlesNoDescription(@TempDir Path tempDir) throws Exception {
        var skillFile = tempDir.resolve("SKILL.md");
        Files.writeString(skillFile, """
            ---
            name: minimal
            ---
            Minimal body
            """);
        var result = SkillLoader.load(skillFile);
        assertTrue(result.isPresent());
        assertEquals("minimal", result.get().name());
        assertEquals("", result.get().description());
    }
}
