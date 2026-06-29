package io.opencode.core.agent;

import io.opencode.core.permission.PermissionRules;
import io.opencode.core.provider.ModelRef;
import io.opencode.core.tool.util.JsonSchema;

import java.util.Map;
import java.util.Optional;

/**
 * 代理配置记录，定义代理的名称、模式、权限、模型、温度、最大步数、提示词等参数。
 */
public record AgentConfig(
    String name,                 // 代理名称
    String description,          // 代理描述
    AgentMode mode,              // 代理模式（SUBAGENT/PRIMARY/ALL/PLAN）
    PermissionRules permission,  // 权限规则
    Optional<ModelRef> model,    // 模型引用
    Optional<Double> temperature,// 模型温度参数
    Optional<Integer> maxSteps,  // 最大执行步数
    Optional<String> prompt,     // 自定义提示词
    Map<String, Object> options, // 额外选项
    JsonSchema jsonSchema        // JSON Schema 定义
) {
    // 紧凑构造器，对空值设置默认值
    public AgentConfig {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Agent name must not be blank");
        }
        permission = permission != null ? permission : PermissionRules.defaultRules();
        options = options != null ? Map.copyOf(options) : Map.of();
        model = model != null ? model : Optional.empty();
        temperature = temperature != null ? temperature : Optional.empty();
        maxSteps = maxSteps != null ? maxSteps : Optional.empty();
        prompt = prompt != null ? prompt : Optional.empty();
        jsonSchema = jsonSchema != null ? jsonSchema : JsonSchema.empty();
    }

    // 创建 Builder 实例
    public static Builder builder(String name) {
        return new Builder(name);
    }

    // Builder 类，用于构建 AgentConfig
    public static class Builder {
        private final String name;
        private String description;
        private AgentMode mode = AgentMode.SUBAGENT;
        private PermissionRules permission;
        private Optional<ModelRef> model = Optional.empty();
        private Optional<Double> temperature = Optional.empty();
        private Optional<Integer> maxSteps = Optional.empty();
        private Optional<String> prompt = Optional.empty();
        private Map<String, Object> options = Map.of();
        private JsonSchema jsonSchema;

        Builder(String name) { this.name = name; }

        public Builder description(String v) { this.description = v; return this; }
        public Builder mode(AgentMode v) { this.mode = v; return this; }
        public Builder permission(PermissionRules v) { this.permission = v; return this; }
        public Builder model(ModelRef v) { this.model = Optional.ofNullable(v); return this; }
        public Builder temperature(Double v) { this.temperature = Optional.ofNullable(v); return this; }
        public Builder maxSteps(Integer v) { this.maxSteps = Optional.ofNullable(v); return this; }
        public Builder prompt(String v) { this.prompt = Optional.ofNullable(v); return this; }
        public Builder options(Map<String, Object> v) { this.options = v; return this; }
        public Builder jsonSchema(JsonSchema v) { this.jsonSchema = v; return this; }
        // 构建最终的 AgentConfig 对象
        public AgentConfig build() {
            return new AgentConfig(name, description, mode, permission, model, temperature, maxSteps, prompt, options, jsonSchema);
        }
    }
}
