package io.opencode.core.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MessageIdTest {

    @Test
    void createWithValue() {
        var id = new MessageId("msg-1");
        assertEquals("msg-1", id.value());
    }

    @Test
    void blankValueThrows() {
        assertThrows(IllegalArgumentException.class, () -> new MessageId(""));
        assertThrows(IllegalArgumentException.class, () -> new MessageId(null));
    }

    @Test
    void randomGeneratesNonBlank() {
        var id = MessageId.random();
        assertNotNull(id.value());
        assertFalse(id.value().isBlank());
    }

    @Test
    void equality() {
        var a = new MessageId("same");
        var b = new MessageId("same");
        assertEquals(a, b);
    }

    @Test
    void toStringReturnsValue() {
        var id = new MessageId("test");
        assertEquals("test", id.toString());
    }

    @Test
    void randomGeneratesUniqueIds() {
        var a = MessageId.random();
        var b = MessageId.random();
        assertNotEquals(a, b);
    }
}
