package io.opencode.core.tool.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JsonSchemaTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void fromNodeExtractsTitleAndDescription() {
        var node = MAPPER.createObjectNode();
        node.put("title", "my schema");
        node.put("description", "a test schema");
        node.put("type", "object");
        var schema = JsonSchema.fromNode(node);
        assertEquals("my schema", schema.title());
        assertEquals("a test schema", schema.description());
        assertSame(node, schema.schema());
    }

    @Test
    void fromNodeMissingFields() {
        var node = MAPPER.createObjectNode();
        var schema = JsonSchema.fromNode(node);
        assertNull(schema.title());
        assertNull(schema.description());
    }

    @Test
    void emptyProducesSchema() {
        var schema = JsonSchema.empty();
        assertNull(schema.title());
        assertNull(schema.description());
        assertEquals("object", schema.schema().get("type").asText());
        assertTrue(schema.schema().has("properties"));
    }
}
