package io.opencode.core.prompt;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PromptLoaderTest {

    @Test
    void unknownAgentReturnsEmpty() {
        var loader = new PromptLoader();
        assertEquals("", loader.load("nonexistent_agent"));
    }

    @Test
    void cachesResult() {
        var loader = new PromptLoader();
        var first = loader.load("nonexistent");
        var second = loader.load("nonexistent");
        assertSame(first, second);
    }
}
