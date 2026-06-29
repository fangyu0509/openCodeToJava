package io.opencode.core.tool.util;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ProjectAnalyzerTest {
    private final ProjectAnalyzer analyzer = new ProjectAnalyzer();

    @Test
    void testEmptyDirectory() throws Exception {
        var tempDir = Files.createTempDirectory("analyzer-test-empty");
        try {
            var info = analyzer.analyze(tempDir);
            assertEquals(tempDir.getFileName().toString(), info.projectName());
            assertTrue(info.languages().isEmpty());
            assertEquals(0, info.fileCount());
            assertEquals(1, info.directoryCount()); // the root dir itself
        } finally {
            Files.deleteIfExists(tempDir);
        }
    }

    @Test
    void testJavaProject() throws Exception {
        var tempDir = Files.createTempDirectory("analyzer-test-java");
        try {
            var srcDir = tempDir.resolve("src/main/java");
            Files.createDirectories(srcDir);
            Files.writeString(srcDir.resolve("Main.java"), "class Main {}");
            Files.writeString(tempDir.resolve("pom.xml"), "<project><modelVersion>4.0.0</modelVersion></project>");

            var info = analyzer.analyze(tempDir);
            assertTrue(info.languages().contains("Java"));
            assertTrue(info.buildSystems().contains("Maven/Gradle"));
            assertTrue(info.frameworks().contains("Maven"));
            assertEquals(2, info.fileCount());
        } finally {
            deleteDirectory(tempDir);
        }
    }

    @Test
    void testTypeScriptProject() throws Exception {
        var tempDir = Files.createTempDirectory("analyzer-test-ts");
        try {
            Files.writeString(tempDir.resolve("package.json"), "{\"name\":\"test\"}");
            Files.writeString(tempDir.resolve("tsconfig.json"), "{}");
            Files.writeString(tempDir.resolve("index.ts"), "const x = 1;");
            Files.createDirectories(tempDir.resolve("src/__tests__"));
            Files.writeString(tempDir.resolve("src/__tests__/index.test.ts"), "test('x', () => {});");

            var info = analyzer.analyze(tempDir);
            assertTrue(info.languages().contains("TypeScript"));
            assertTrue(info.frameworks().contains("Node.js"));
            assertTrue(info.frameworks().contains("TypeScript"));
            assertTrue(info.testFrameworks().contains("Jest"));
            assertTrue(info.buildSystems().contains("npm/yarn/pnpm"));
        } finally {
            deleteDirectory(tempDir);
        }
    }

    @Test
    void testGenerateAgentsMd() {
        var info = analyzer.analyze(Path.of(System.getProperty("user.dir")));
        var md = analyzer.generateAgentsMd(info);
        assertTrue(md.contains("## Languages"));
        assertTrue(md.contains("## Guidelines"));
    }

    @Test
    void testInitCommand() throws Exception {
        var tempDir = Files.createTempDirectory("analyzer-init-cmd");
        try {
            Files.writeString(tempDir.resolve("README.md"), "# Test Project");
            var info = analyzer.analyze(tempDir);
            var md = analyzer.generateAgentsMd(info);
            Files.writeString(tempDir.resolve("AGENTS.md"), md);

            assertTrue(Files.exists(tempDir.resolve("AGENTS.md")));
            var content = Files.readString(tempDir.resolve("AGENTS.md"));
            assertTrue(content.contains(tempDir.getFileName().toString()));
            assertTrue(content.contains("Guidelines"));
        } finally {
            deleteDirectory(tempDir);
        }
    }

    private void deleteDirectory(Path dir) {
        try (var stream = Files.walk(dir)) {
            stream.sorted(java.util.Comparator.reverseOrder())
                .forEach(p -> { try { Files.deleteIfExists(p); } catch (Exception e) {} });
        } catch (Exception e) {}
    }
}
