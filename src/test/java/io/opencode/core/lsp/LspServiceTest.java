package io.opencode.core.lsp;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class LspServiceTest {

    private final LspService service = new LspService();

    @Test
    void hasDefaultConfigs() {
        assertTrue(service.listConfigs().size() >= 10);
    }

    @Test
    void findsConfigByExtension() {
        var found = service.findConfigFor(Path.of("test.java"));
        assertTrue(found.isPresent());
        assertEquals("java", found.get().name());
    }

    @Test
    void findsConfigForPython() {
        var found = service.findConfigFor(Path.of("test.py"));
        assertTrue(found.isPresent());
        assertEquals("python", found.get().name());
    }

    @Test
    void findsConfigForTypeScript() {
        var found = service.findConfigFor(Path.of("test.tsx"));
        assertTrue(found.isPresent());
        assertEquals("typescript", found.get().name());
    }

    @Test
    void returnsEmptyForUnknownExtension() {
        var found = service.findConfigFor(Path.of("test.xyz"));
        assertTrue(found.isEmpty());
    }

    @Test
    void registerCustomConfig() {
        var cfg = new LspService.ServerConfig("custom", "my-ls", java.util.List.of(), java.util.List.of(".xyz"));
        service.register(cfg);
        var found = service.findConfigFor(Path.of("file.xyz"));
        assertTrue(found.isPresent());
        assertEquals("custom", found.get().name());
    }

    @Test
    void activeCountStartsZero() {
        assertEquals(0, service.activeCount());
    }
}
