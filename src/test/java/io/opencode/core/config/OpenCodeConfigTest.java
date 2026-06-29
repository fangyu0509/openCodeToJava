package io.opencode.core.config;

import io.opencode.core.agent.AgentConfig;
import io.opencode.core.provider.ModelRef;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class OpenCodeConfigTest {

    @Test
    void defaults() {
        var config = OpenCodeConfig.defaults(Path.of("/workspace"));
        assertEquals("0.1.0", config.version());
        assertEquals(Path.of("/workspace"), config.workspaceDir());
        assertTrue(config.dataDir().isPresent());
        assertEquals(Path.of("/workspace/.opencode"), config.dataDir().get());
        assertTrue(config.defaultModel().isEmpty());
        assertTrue(config.providers().isEmpty());
        assertFalse(config.telemetry());
    }

    @Test
    void serverConfigDefaults() {
        var server = OpenCodeConfig.ServerConfig.defaultConfig();
        assertEquals(4096, server.port());
        assertEquals("127.0.0.1", server.host());
    }

    @Test
    void serverConfigCustom() {
        var server = new OpenCodeConfig.ServerConfig(8080, "0.0.0.0");
        assertEquals(8080, server.port());
        assertEquals("0.0.0.0", server.host());
    }

    @Test
    void providerConfig() {
        var model = ModelRef.of("openai", "gpt-4");
        var pc = new OpenCodeConfig.ProviderConfig("test", "API_KEY",
            Optional.of("sk-123"), Optional.of("https://api.test.com"), Optional.of(model));
        assertEquals("test", pc.id());
        assertEquals("API_KEY", pc.apiKeyEnvVar());
        assertEquals("sk-123", pc.apiKey().get());
        assertEquals("https://api.test.com", pc.baseUrl().get());
        assertEquals(model, pc.defaultModel().get());
    }

    @Test
    void providerConfigEmptyOptionals() {
        var pc = new OpenCodeConfig.ProviderConfig("test", "API_KEY",
            Optional.empty(), Optional.empty(), Optional.empty());
        assertTrue(pc.apiKey().isEmpty());
        assertTrue(pc.baseUrl().isEmpty());
        assertTrue(pc.defaultModel().isEmpty());
    }

    @Test
    void fullConfigConstructor() {
        var server = new OpenCodeConfig.ServerConfig(8080, "localhost");
        var agent = AgentConfig.builder("build").build();
        var config = new OpenCodeConfig("1.0.0", Path.of("/ws"),
            Optional.of(Path.of("/data")), Optional.of(ModelRef.of("test", "m")),
            List.of(), List.of(agent), server, List.of(), true, 100_000, 5, List.of());
        assertEquals("1.0.0", config.version());
        assertEquals("/ws", config.workspaceDir().toString());
        assertEquals("/data", config.dataDir().get().toString());
        assertEquals("test:m", config.defaultModel().get().toString());
        assertEquals(1, config.agents().size());
        assertEquals(8080, config.server().port());
        assertTrue(config.telemetry());
    }
}
