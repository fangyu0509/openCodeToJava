package io.opencode.core.tool.util;

import io.opencode.core.config.ReferenceConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class ReferenceServiceTest {
    private final ReferenceService service = new ReferenceService();

    @Test
    void returnsEmptyForNoReferences() {
        assertTrue(service.allRefs().isEmpty());
        assertNull(service.resolveReference("nonexistent", Path.of(".")));
    }

    @Test
    void resolvesDirectoryReference(@TempDir Path tempDir) throws Exception {
        var subDir = tempDir.resolve("mydocs");
        Files.createDirectories(subDir);
        Files.writeString(subDir.resolve("readme.md"), "# Hello");
        Files.writeString(subDir.resolve("notes.txt"), "Some notes");

        var ref = new ReferenceConfig("docs", "Documentation files",
            Optional.of(subDir), Optional.empty(), List.of());
        service.configure(Map.of("docs", ref));

        var result = service.resolveReference("docs", tempDir);
        assertNotNull(result);
        assertTrue(result.contains("@docs"));
        assertTrue(result.contains("readme.md"));
        assertTrue(result.contains("notes.txt"));
    }

    @Test
    void resolvesFileReference(@TempDir Path tempDir) throws Exception {
        var file = tempDir.resolve("config.yaml");
        Files.writeString(file, "key: value\nnested:\n  deep: true\n");

        var ref = new ReferenceConfig("config", "Config file",
            Optional.of(file), Optional.empty(), List.of());
        service.configure(Map.of("config", ref));

        var result = service.resolveReference("config", tempDir);
        assertNotNull(result);
        assertTrue(result.contains("key: value"));
        assertTrue(result.contains("@config"));
    }

    @Test
    void resolveAllReturnsAll(@TempDir Path tempDir) throws Exception {
        var subDir = tempDir.resolve("src");
        Files.createDirectories(subDir);

        service.configure(Map.of(
            "src", new ReferenceConfig("src", "Source code",
                Optional.of(subDir), Optional.empty(), List.of())
        ));

        var result = service.resolveAll(tempDir);
        assertTrue(result.contains("@src"));
    }

    @Test
    void resolvesWithPatternFilter(@TempDir Path tempDir) throws Exception {
        var subDir = tempDir.resolve("lib");
        Files.createDirectories(subDir);
        Files.writeString(subDir.resolve("main.py"), "print('hello')");
        Files.writeString(subDir.resolve("main.java"), "class Main {}");
        Files.writeString(subDir.resolve("main.js"), "console.log('hello')");

        var ref = new ReferenceConfig("lib", "Library",
            Optional.of(subDir), Optional.empty(), List.of(".py", ".java"));
        service.configure(Map.of("lib", ref));

        var result = service.resolveReference("lib", tempDir);
        assertNotNull(result);
        assertTrue(result.contains("main.py"));
        assertTrue(result.contains("main.java"));
        assertFalse(result.contains("main.js"));
    }

    @Test
    void handlesNonexistentReference() {
        service.configure(Map.of("missing", new ReferenceConfig("missing", "Dir",
            Optional.of(Path.of("/nonexistent/path")), Optional.empty(), List.of())));
        var result = service.resolveReference("missing", Path.of("."));
        assertNotNull(result);
        assertTrue(result.contains("@missing"));
    }
}
