package io.opencode.core.util;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class RetryTest {

    @Test
    void succeedsOnFirstAttempt() {
        var result = Retry.withBackoff(() -> "ok", 2);
        assertEquals("ok", result);
    }

    @Test
    void retriesOnFailure() {
        var attempts = new AtomicInteger();
        var result = Retry.withBackoff(() -> {
            if (attempts.incrementAndGet() < 3) throw new RuntimeException("429 Too Many Requests");
            return "ok";
        }, 3);
        assertEquals("ok", result);
        assertEquals(3, attempts.get());
    }

    @Test
    void throwsAfterMaxRetries() {
        assertThrows(RuntimeException.class, () ->
            Retry.withBackoff(() -> { throw new RuntimeException("429 retry me"); }, 2));
    }

    @Test
    void nonRetryableErrorThrowsImmediately() {
        assertThrows(RuntimeException.class, () ->
            Retry.withBackoff(() -> { throw new RuntimeException("bad request"); }, 2));
    }
}
