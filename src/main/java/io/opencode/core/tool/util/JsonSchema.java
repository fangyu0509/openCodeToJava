package io.opencode.core.tool.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

// JSON Schema 记录：封装工具的输入参数 Schema，包含标题、描述和原始节点
public record JsonSchema(String title, String description, JsonNode schema) {

    // 从 JsonNode 构造 JsonSchema，自动提取 title 和 description
    public static JsonSchema fromNode(JsonNode schema) {
        var title = schema.has("title") ? schema.get("title").asText() : null;
        var desc = schema.has("description") ? schema.get("description").asText() : null;
        return new JsonSchema(title, desc, schema);
    }

    // 返回一个空的 JSON Schema（type: object, properties: {}）
    public static JsonSchema empty() {
        var mapper = new ObjectMapper();
        var node = mapper.createObjectNode();
        node.put("type", "object");
        node.set("properties", mapper.createObjectNode());
        return new JsonSchema(null, null, node);
    }
}
