package io.opencode.core.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * 重试工具类，提供带指数退避的重试机制，用于处理临时性故障（如网络超时、限流）
 */
public class Retry {
    private static final Logger log = LoggerFactory.getLogger(Retry.class);

    /**
     * 带指数退避的重试，使用默认基础延迟 1 秒
     *
     * @param call       要执行的操作（Supplier 方式）
     * @param maxRetries 最大重试次数
     * @param <T>        返回值类型
     * @return 操作结果
     */
    public static <T> T withBackoff(Supplier<T> call, int maxRetries) {
        return withBackoff(call, maxRetries, Duration.ofSeconds(1));
    }

    /**
     * 带指数退避的重试核心实现
     * 每次重试的延迟为 baseDelay * 2^attempt（指数增长）
     * 仅对可重试的异常进行重试，不可重试的异常直接抛出
     *
     * @param call       要执行的操作
     * @param maxRetries 最大重试次数
     * @param baseDelay  基础延迟时间
     */
    public static <T> T withBackoff(Supplier<T> call, int maxRetries, Duration baseDelay) {
        var lastEx = (RuntimeException) null;
        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
                return call.get();
            } catch (RuntimeException e) {
                lastEx = e;
                // 如果还有重试次数且异常可重试，则等待后继续
                if (attempt < maxRetries && isRetryable(e)) {
                    var delay = baseDelay.multipliedBy(1L << attempt);
                    log.warn("Attempt {} failed, retrying in {}ms: {}", attempt + 1, delay.toMillis(), e.getMessage());
                    try { Thread.sleep(delay.toMillis()); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); break; }
                }
            }
        }
        throw lastEx; // 重试耗尽后抛出最后一个异常
    }

    /**
     * 判断异常是否可重试
     * 根据异常消息中的关键词匹配：HTTP 429（限流）、503（服务不可用）、timeout 等
     */
    private static boolean isRetryable(Throwable e) {
        var msg = e.getMessage();
        if (msg == null) return true;
        var lower = msg.toLowerCase();
        return lower.contains("429") || lower.contains("503") || lower.contains("timeout")
            || lower.contains("too many requests") || lower.contains("rate limit")
            || lower.contains("service unavailable") || lower.contains("5");
    }
}
