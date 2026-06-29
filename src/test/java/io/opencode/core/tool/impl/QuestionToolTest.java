package io.opencode.core.tool.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class QuestionToolTest {
    private final QuestionTool tool = new QuestionTool();
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void testId() {
        assertEquals("question", tool.id());
    }

    @Test
    void testDescription() {
        assertNotNull(tool.description());
    }

    @Test
    void testParameters() {
        assertNotNull(tool.parameters());
    }
}
