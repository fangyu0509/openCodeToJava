package io.opencode.core.event;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 简单的事件总线实现，基于 ConcurrentHashMap 和 CopyOnWriteArrayList。
 * 每个主题维护一个订阅列表，发布事件时遍历调用所有处理器。
 * 使用 CopyOnWriteArrayList 保证遍历时的线程安全性。
 */
@Component
public class SimpleEventBus implements EventBus {
    // 主题 -> 订阅列表，ConcurrentHashMap 保证线程安全
    private final Map<String, CopyOnWriteArrayList<Subscription<?>>> topics = new ConcurrentHashMap<>();

    // 发布事件到指定主题，遍历所有订阅者并调用其处理器
    @Override
    public <T> void publish(String topic, T event) {
        var subs = topics.get(topic);
        if (subs != null) {
            for (var sub : subs) {
                @SuppressWarnings("unchecked")
                var handler = (EventHandler<T>) sub.handler;
                handler.handle(event);
            }
        }
    }

    // 订阅主题：如果主题不存在则创建新列表，添加订阅并返回可取消订阅的对象
    @Override
    public <T> Disposable subscribe(String topic, Class<T> eventType, EventHandler<T> handler) {
        var subs = topics.computeIfAbsent(topic, k -> new CopyOnWriteArrayList<>());
        var sub = new Subscription<>(handler);
        subs.add(sub);
        return () -> {
            subs.remove(sub);
            if (subs.isEmpty()) topics.remove(topic);
        };
    }

    // 取消订阅：调用 subscription 的 dispose 方法移除处理器
    @Override
    public void unsubscribe(String topic, Disposable subscription) {
        subscription.dispose();
    }

    // 内部记录：持有事件处理器引用
    private record Subscription<T>(EventHandler<T> handler) {}
}
