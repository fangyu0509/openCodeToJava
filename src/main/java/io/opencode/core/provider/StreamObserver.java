package io.opencode.core.provider;

/**
 * StreamObserver —— 流式数据观察者接口（函数式接口）
 * 类似 Reactive 编程中的 Observer 模式，用于接收流式响应中的各个事件
 * @param <T> 流中数据块的类型，通常为 ChatChunk
 */
@FunctionalInterface
public interface StreamObserver<T> {
    /** 接收到下一个数据块时回调 */
    void onNext(T chunk);

    /** 流处理过程中发生异常时回调 */
    default void onError(Throwable error) {}

    /** 流处理正常结束时回调 */
    default void onComplete() {}
}
