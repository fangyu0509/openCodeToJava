package io.opencode.core.session;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FilePartTest {

    @Test
    void ofCreatesWithContent() {
        var fp = FilePart.of("/tmp/test.txt", "hello");
        assertEquals("/tmp/test.txt", fp.path());
        assertEquals("hello", fp.content().get());
        assertTrue(fp.mediaType().isEmpty());
        assertTrue(fp.checksum().isEmpty());
    }

    @Test
    void referenceCreatesWithoutContent() {
        var fp = FilePart.reference("/tmp/test.txt");
        assertEquals("/tmp/test.txt", fp.path());
        assertTrue(fp.content().isEmpty());
    }

    @Test
    void equality() {
        var a = FilePart.of("a.txt", "hello");
        var b = FilePart.of("a.txt", "hello");
        assertEquals(a, b);
    }
}
