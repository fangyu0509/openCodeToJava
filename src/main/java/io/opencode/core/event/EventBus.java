package io.opencode.core.event;

/**
 * 事件总线接口，提供发布-订阅模式的通用事件机制。
 * 用于在系统各组件之间解耦通信（如 SSE 事件广播）。
 */
public interface EventBus {
    // 向指定主题发布事件
    <T> void publish(String topic, T event);
    // 订阅指定主题的事件，返回可取消订阅的 Disposable 对象
    <T> Disposable subscribe(String topic, Class<T> eventType, EventHandler<T> handler);
    // 取消订阅
    void unsubscribe(String topic, Disposable subscription);
}
