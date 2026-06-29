package io.opencode.core.tool.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.List;

// 工具工具类：提供 JSON Schema 构建器、输出截断等公用方法
public final class ToolUtils {
    private static final int MAX_OUTPUT_LENGTH = 200_000;  // 输出最大字符数
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ToolUtils() {}  // 工具类禁止实例化

    // 返回共享的 ObjectMapper 实例
    public static ObjectMapper mapper() {
        return MAPPER;
    }

    // 截断过长的输出字符串，并附加省略信息
    public static String truncate(String output) {
        if (output == null) return "";
        if (output.length() <= MAX_OUTPUT_LENGTH) return output;
        return output.substring(0, MAX_OUTPUT_LENGTH)
            + "\n\n...(truncated, " + (output.length() - MAX_OUTPUT_LENGTH) + " chars omitted)";
    }

    // 创建一个新的 JSON Schema 构建器
    public static JsonSchemaBuilder schema() {
        return new JsonSchemaBuilder();
    }

    // JSON Schema 构建器：链式调用添加 string/number/array 类型参数
    public static class JsonSchemaBuilder {
        private final ObjectNode root = MAPPER.createObjectNode();

        JsonSchemaBuilder() {
            root.put("type", "object");
            root.set("properties", MAPPER.createObjectNode());
            root.set("required", MAPPER.createArrayNode());
        }

        // 添加字符串类型参数（可选）
        public JsonSchemaBuilder string(String name, String description) {
            return string(name, description, false);
        }

        // 添加字符串类型参数，可指定是否必填
        public JsonSchemaBuilder string(String name, String description, boolean required) {
            var p = root.with("properties").putObject(name);
            p.put("type", "string");
            p.put("description", description != null ? description : "");
            if (required) ((ArrayNode) root.get("required")).add(name);
            return this;
        }

        // 添加数字类型参数（可选）
        public JsonSchemaBuilder number(String name, String description) {
            return number(name, description, false);
        }

        // 添加数字类型参数，可指定是否必填
        public JsonSchemaBuilder number(String name, String description, boolean required) {
            var p = root.with("properties").putObject(name);
            p.put("type", "number");
            p.put("description", description != null ? description : "");
            if (required) ((ArrayNode) root.get("required")).add(name);
            return this;
        }

        // 添加数组类型参数（可选）
        public JsonSchemaBuilder array(String name, String description, JsonNode items) {
            return array(name, description, items, false);
        }

        // 添加数组类型参数，可指定是否必填和元素定义
        public JsonSchemaBuilder array(String name, String description, JsonNode items, boolean required) {
            var p = root.with("properties").putObject(name);
            p.put("type", "array");
            p.set("items", items);
            p.put("description", description != null ? description : "");
            if (required) ((ArrayNode) root.get("required")).add(name);
            return this;
        }

        // 构建并返回 JsonSchema 对象
        public JsonSchema build() {
            return JsonSchema.fromNode(root);
        }
    }
}
