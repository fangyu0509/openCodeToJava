package io.opencode.core.model;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class AbortSignalTest {

    @Test
    void initiallyNotAborted() {
        var signal = new AbortSignal();
        assertFalse(signal.isAborted());
    }

    @Test
    void abortSetsFlag() {
        var signal = new AbortSignal();
        signal.abort();
        assertTrue(signal.isAborted());
    }

    @Test
    void abortFiresListeners() {
        var signal = new AbortSignal();
        var fired = new AtomicBoolean();
        signal.onAbort(() -> fired.set(true));
        signal.abort();
        assertTrue(fired.get());
    }

    @Test
    void onAbortReturnsDisposable() {
        var signal = new AbortSignal();
        var fired = new AtomicBoolean();
        var disposable = signal.onAbort(() -> fired.set(true));
        disposable.run();
        signal.abort();
        assertFalse(fired.get());
    }

    @Test
    void multipleListeners() {
        var signal = new AbortSignal();
        var count = new java.util.concurrent.atomic.AtomicInteger();
        signal.onAbort(count::incrementAndGet);
        signal.onAbort(count::incrementAndGet);
        signal.abort();
        assertEquals(2, count.get());
    }

    @Test
    void checkThrowsWhenAborted() {
        var signal = new AbortSignal();
        signal.abort();
        assertThrows(AbortSignal.AbortException.class, signal::check);
    }

    @Test
    void checkDoesNotThrowWhenNotAborted() {
        var signal = new AbortSignal();
        assertDoesNotThrow(signal::check);
    }

    @Test
    void abortIsIdempotent() {
        var signal = new AbortSignal();
        var count = new java.util.concurrent.atomic.AtomicInteger();
        signal.onAbort(count::incrementAndGet);
        signal.abort();
        signal.abort();
        assertEquals(1, count.get());
    }
}
