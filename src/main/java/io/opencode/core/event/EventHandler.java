package io.opencode.core.event;

/**
 * 事件处理器函数式接口，用于处理事件总线中的事件。
 * @param <T> 事件类型
 */
@FunctionalInterface
public interface EventHandler<T> {
    void handle(T event);
}
