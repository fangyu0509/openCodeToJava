package io.opencode.core.formatter;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;

class FormatterServiceTest {
    private final FormatterService service = new FormatterService();

    @Test
    void testFormatNoOpForUnknownExtension() {
        service.format(Path.of("test.unknown"));
    }

    @Test
    void testLanguageForJava() {
        assertTrue(service.languageFor(Path.of("Foo.java")).isPresent());
        assertEquals("java", service.languageFor(Path.of("Foo.java")).get());
    }

    @Test
    void testLanguageForPython() {
        assertTrue(service.languageFor(Path.of("main.py")).isPresent());
        assertEquals("python", service.languageFor(Path.of("main.py")).get());
    }

    @Test
    void testLanguageForUnknown() {
        assertTrue(service.languageFor(Path.of("file.xyz")).isEmpty());
    }

    @Test
    void testLanguageForNoExtension() {
        assertTrue(service.languageFor(Path.of("Makefile")).isEmpty());
    }

    @Test
    void testLanguageForMultipleExtensions() {
        assertTrue(service.languageFor(Path.of("component.tsx")).isPresent());
        assertEquals("typescript", service.languageFor(Path.of("component.tsx")).get());
        assertTrue(service.languageFor(Path.of("app.jsx")).isPresent());
        assertEquals("javascript", service.languageFor(Path.of("app.jsx")).get());
    }
}
