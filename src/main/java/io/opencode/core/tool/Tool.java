package io.opencode.core.tool;

import io.opencode.core.tool.util.JsonSchema;
import com.fasterxml.jackson.databind.JsonNode;

// 工具接口：所有工具必须实现此接口，定义工具的基本契约
public interface Tool<M extends Tool.Metadata> {
    // 返回工具的唯一标识符
    String id();
    // 返回工具的描述信息
    String description();
    // 返回工具的 JSON Schema 参数定义
    JsonSchema parameters();
    // 执行工具，接收 JSON 参数和上下文，返回执行结果
    ExecuteResult<M> execute(JsonNode args, ToolContext context);
    // 格式化验证错误的默认方法
    default String formatValidationError(Exception error) {
        return "Error executing tool " + id() + ": " + error.getMessage();
    }

    // 元数据接口：所有工具的元数据类型需实现此接口
    interface Metadata {
        Metadata EMPTY = new Metadata() {};
    }
}
