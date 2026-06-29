package io.opencode.core.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SessionIdTest {

    @Test
    void createWithValue() {
        var id = new SessionId("abc-123");
        assertEquals("abc-123", id.value());
    }

    @Test
    void randomGeneratesNonBlank() {
        var id = SessionId.random();
        assertNotNull(id.value());
        assertFalse(id.value().isBlank());
    }

    @Test
    void blankValueThrows() {
        assertThrows(IllegalArgumentException.class, () -> new SessionId(""));
        assertThrows(IllegalArgumentException.class, () -> new SessionId(null));
        assertThrows(IllegalArgumentException.class, () -> new SessionId("   "));
    }

    @Test
    void equality() {
        var a = new SessionId("same");
        var b = new SessionId("same");
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void toStringReturnsValue() {
        var id = new SessionId("test-id");
        assertEquals("test-id", id.toString());
    }

    @Test
    void randomGeneratesUniqueIds() {
        var a = SessionId.random();
        var b = SessionId.random();
        assertNotEquals(a, b);
    }
}
