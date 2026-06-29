package io.opencode.core.event;

/**
 * 可销毁接口，用于管理事件订阅的生命周期。
 * 调用 dispose() 可取消订阅或释放资源。
 */
@FunctionalInterface
public interface Disposable {
    void dispose();
}
