package io.opencode.core.tool.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ToolUtilsTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void truncateNullReturnsEmpty() {
        assertEquals("", ToolUtils.truncate(null));
    }

    @Test
    void truncateShortStringReturnsAsIs() {
        var s = "hello";
        assertSame(s, ToolUtils.truncate(s));
    }

    @Test
    void truncateLongStringAppendsMessage() {
        var sb = new StringBuilder();
        for (int i = 0; i < 200_100; i++) sb.append('x');
        var result = ToolUtils.truncate(sb.toString());
        assertTrue(result.startsWith("x".repeat(200_000)));
        assertTrue(result.contains("truncated"));
        assertTrue(result.contains("100 chars omitted"));
    }

    @Test
    void mapperReturnsSingleton() {
        assertSame(ToolUtils.mapper(), ToolUtils.mapper());
    }

    @Test
    void schemaBuilderProducesValidSchema() {
        var schema = ToolUtils.schema()
            .string("name", "The name", true)
            .number("count", "The count", false)
            .build();
        assertNotNull(schema.schema());
        assertEquals("object", schema.schema().get("type").asText());
        assertTrue(schema.schema().has("properties"));
        assertTrue(schema.schema().has("required"));
        assertEquals("name", schema.schema().get("required").get(0).asText());
        assertEquals("string", schema.schema().get("properties").get("name").get("type").asText());
        assertEquals("number", schema.schema().get("properties").get("count").get("type").asText());
    }

    @Test
    void schemaBuilderWithArray() {
        var items = MAPPER.createObjectNode().put("type", "string");
        var schema = ToolUtils.schema()
            .array("tags", "list of tags", items, true)
            .build();
        assertEquals("array", schema.schema().get("properties").get("tags").get("type").asText());
        assertTrue(schema.schema().get("properties").get("tags").has("items"));
    }

    @Test
    void schemaBuilderDefaults() {
        var schema = ToolUtils.schema().build();
        assertTrue(schema.schema().get("properties").isEmpty());
        assertTrue(schema.schema().get("required").isEmpty());
    }
}
