package io.opencode.core.provider;

/**
 * ModelRef —— 模型引用记录
 * 组合了提供商 ID 和模型 ID，用于唯一标识一个模型
 * 例如 ModelRef("openai", "gpt-4o") 表示 openai 提供商的 gpt-4o 模型
 */
public record ModelRef(String providerId, String modelId) {
    // 紧凑构造器：校验参数，不允许空值或空白字符串
    public ModelRef {
        if (providerId == null || providerId.isBlank()) {
            throw new IllegalArgumentException("providerId must not be blank");
        }
        if (modelId == null || modelId.isBlank()) {
            throw new IllegalArgumentException("modelId must not be blank");
        }
    }

    /** 快捷工厂方法，等价于 new ModelRef(providerId, modelId) */
    public static ModelRef of(String providerId, String modelId) {
        return new ModelRef(providerId, modelId);
    }

    /** 格式化为 "providerId:modelId" 的字符串形式 */
    @Override
    public String toString() {
        return providerId + ":" + modelId;
    }
}
