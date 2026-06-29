package io.opencode.core.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ConfigLoaderTest {
    private final ConfigLoader loader = new ConfigLoader();

    @Test
    void returnsDefaultsWhenNoConfigFile(@TempDir Path tempDir) {
        var config = loader.load(tempDir);
        assertNotNull(config);
        assertEquals(tempDir, config.workspaceDir());
        assertTrue(config.dataDir().isPresent());
    }

    @Test
    void loadsModelFromConfigFile(@TempDir Path tempDir) throws Exception {
        var json = """
            {"model": "openai/gpt-4o"}
            """;
        Files.writeString(tempDir.resolve("opencode.json"), json);
        var config = loader.load(tempDir);
        assertTrue(config.defaultModel().isPresent());
        assertEquals("openai", config.defaultModel().get().providerId());
        assertEquals("gpt-4o", config.defaultModel().get().modelId());
    }

    @Test
    void loadsServerConfig(@TempDir Path tempDir) throws Exception {
        var json = """
            {"server": {"port": 8080, "hostname": "0.0.0.0"}}
            """;
        Files.writeString(tempDir.resolve("opencode.json"), json);
        var config = loader.load(tempDir);
        assertEquals(8080, config.server().port());
        assertEquals("0.0.0.0", config.server().host());
    }

    @Test
    void prefersJsonOverJsonc(@TempDir Path tempDir) throws Exception {
        Files.writeString(tempDir.resolve("opencode.jsonc"), "{\"model\": \"ollama/llama3\"}");
        Files.writeString(tempDir.resolve("opencode.json"), "{\"model\": \"openai/gpt-4\"}");
        var config = loader.load(tempDir);
        assertEquals("gpt-4", config.defaultModel().get().modelId());
    }

    @Test
    void handlesInvalidJsonGracefully(@TempDir Path tempDir) throws Exception {
        Files.writeString(tempDir.resolve("opencode.json"), "not valid json{{{");
        var config = loader.load(tempDir);
        assertNotNull(config);
    }
}
