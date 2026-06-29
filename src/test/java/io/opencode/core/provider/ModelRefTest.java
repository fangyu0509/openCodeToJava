package io.opencode.core.provider;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ModelRefTest {

    @Test
    void create() {
        var ref = ModelRef.of("openai", "gpt-4");
        assertEquals("openai", ref.providerId());
        assertEquals("gpt-4", ref.modelId());
    }

    @Test
    void blankProviderIdThrows() {
        assertThrows(IllegalArgumentException.class, () -> ModelRef.of("", "gpt-4"));
        assertThrows(IllegalArgumentException.class, () -> ModelRef.of(null, "gpt-4"));
    }

    @Test
    void blankModelIdThrows() {
        assertThrows(IllegalArgumentException.class, () -> ModelRef.of("openai", ""));
        assertThrows(IllegalArgumentException.class, () -> ModelRef.of("openai", null));
    }

    @Test
    void toStringFormat() {
        var ref = ModelRef.of("openai", "gpt-4");
        assertEquals("openai:gpt-4", ref.toString());
    }

    @Test
    void equality() {
        var a = ModelRef.of("openai", "gpt-4");
        var b = ModelRef.of("openai", "gpt-4");
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }
}
