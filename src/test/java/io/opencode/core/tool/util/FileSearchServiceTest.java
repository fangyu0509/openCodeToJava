package io.opencode.core.tool.util;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class FileSearchServiceTest {
    private final FileSearchService service = new FileSearchService();

    @Test
    void testEmptyQuery() {
        var results = service.search("", 10);
        assertTrue(results.isEmpty());
    }

    @Test
    void testSearchByFileName() throws Exception {
        var tempDir = Files.createTempDirectory("fss-test");
        var origUserDir = System.getProperty("user.dir");
        try {
            Files.writeString(tempDir.resolve("Main.java"), "class Main {}");
            Files.writeString(tempDir.resolve("Utils.java"), "class Utils {}");
            Files.createDirectories(tempDir.resolve("src"));
            Files.writeString(tempDir.resolve("src/Helper.java"), "class Helper {}");

            System.setProperty("user.dir", tempDir.toString());

            var results = service.search("Main", 10);
            assertFalse(results.isEmpty());
            assertTrue(results.stream().anyMatch(e -> e.name().equals("Main.java")));

            results = service.search("Helper", 10);
            assertFalse(results.isEmpty());
            assertTrue(results.stream().anyMatch(e -> e.name().equals("Helper.java")));
        } finally {
            System.setProperty("user.dir", origUserDir);
            deleteDirectory(tempDir);
        }
    }

    @Test
    void testFuzzySearch() throws Exception {
        var tempDir = Files.createTempDirectory("fss-fuzzy");
        var origUserDir = System.getProperty("user.dir");
        try {
            Files.writeString(tempDir.resolve("MyLongFileName.java"), "content");

            System.setProperty("user.dir", tempDir.toString());

            // Fuzzy: "MLFN" should match "MyLongFileName.java"
            var results = service.search("MLFN", 10);
            assertFalse(results.isEmpty());
        } finally {
            System.setProperty("user.dir", origUserDir);
            deleteDirectory(tempDir);
        }
    }

    @Test
    void testIgnoredDirectories() throws Exception {
        var tempDir = Files.createTempDirectory("fss-ignore");
        var origUserDir = System.getProperty("user.dir");
        try {
            Files.createDirectories(tempDir.resolve("node_modules"));
            Files.writeString(tempDir.resolve("node_modules/ignored.js"), "ignored");
            Files.writeString(tempDir.resolve("keep.js"), "keep");

            System.setProperty("user.dir", tempDir.toString());

            var results = service.search("keep", 10);
            assertFalse(results.isEmpty());

            results = service.search("ignored", 10);
            assertTrue(results.isEmpty());
        } finally {
            System.setProperty("user.dir", origUserDir);
            deleteDirectory(tempDir);
        }
    }

    @Test
    void testMaxResults() throws Exception {
        var tempDir = Files.createTempDirectory("fss-max");
        var origUserDir = System.getProperty("user.dir");
        try {
            for (var i = 0; i < 10; i++) {
                Files.writeString(tempDir.resolve("file" + i + ".txt"), "content");
            }

            System.setProperty("user.dir", tempDir.toString());

            var results = service.search("file", 3);
            assertEquals(3, results.size());
        } finally {
            System.setProperty("user.dir", origUserDir);
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
