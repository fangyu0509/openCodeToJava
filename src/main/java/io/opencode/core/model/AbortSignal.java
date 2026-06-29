package io.opencode.core.model;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * 中止信号，用于协调操作的中止请求，支持监听器模式
 * 线程安全，可在多线程环境下使用
 */
public class AbortSignal {
    private volatile boolean aborted;                                          // 是否已被中止（volatile 确保跨线程可见性）
    private final CopyOnWriteArrayList<Runnable> listeners = new CopyOnWriteArrayList<>(); // 中止事件监听器列表

    /** 检查操作是否已被要求中止 */
    public boolean isAborted() {
        return aborted;
    }

    /**
     * 触发中止操作：设置中止标志，并依次通知所有注册的监听器
     * 幂等操作，多次调用只有第一次生效
     */
    public void abort() {
        if (aborted) return;
        aborted = true;
        listeners.forEach(Runnable::run);
    }

    /**
     * 注册一个在中止时触发的回调监听器，返回一个可取消注册的句柄
     */
    public Runnable onAbort(Runnable listener) {
        listeners.add(listener);
        return () -> listeners.remove(listener);
    }

    /**
     * 检查中止状态，如果已被中止则抛出 AbortException
     * 供长时间运行的操作在关键节点调用，以便及时响应中止请求
     */
    public void check() throws AbortException {
        if (aborted) {
            throw new AbortException("Operation was aborted");
        }
    }

    /**
     * 中止异常，继承 RuntimeException，表示操作因中止请求而中断
     */
    public static class AbortException extends RuntimeException {
        public AbortException(String message) {
            super(message);
        }
    }
}
