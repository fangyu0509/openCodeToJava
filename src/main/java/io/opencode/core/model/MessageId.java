package io.opencode.core.model;

/**
 * 消息 ID 值对象，封装一条消息的唯一标识符
 */
public record MessageId(String value) {
    /**
     * 紧凑构造函数，校验 ID 不能为空或空白
     */
    public MessageId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("MessageId must not be blank");
        }
    }

    /**
     * 生成一个随机的消息 ID（基于 UUID）
     */
    public static MessageId random() {
        return new MessageId(java.util.UUID.randomUUID().toString());
    }

    @Override
    public String toString() {
        return value;
    }
}
