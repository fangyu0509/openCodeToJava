package io.opencode.core.model;

/**
 * 会话 ID 值对象，封装一个会话的唯一标识符
 */
public record SessionId(String value) {
    /**
     * 紧凑构造函数，校验会话 ID 不能为空或空白
     */
    public SessionId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("SessionId must not be blank");
        }
    }

    /**
     * 生成一个随机会话 ID（基于 UUID）
     */
    public static SessionId random() {
        return new SessionId(java.util.UUID.randomUUID().toString());
    }

    @Override
    public String toString() {
        return value;
    }
}
