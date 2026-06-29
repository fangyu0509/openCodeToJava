package io.opencode.core.event;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class SimpleEventBusTest {

    @Test
    void publishWithNoSubscribersDoesNothing() {
        var bus = new SimpleEventBus();
        // Should not throw
        bus.publish("test", "hello");
    }

    @Test
    void unsubscribeStopsEvents() {
        var bus = new SimpleEventBus();
        var received = new AtomicInteger();
        var sub = bus.subscribe("test", String.class, e -> received.incrementAndGet());
        sub.dispose();
        bus.publish("test", "hello");
        assertEquals(0, received.get());
    }

    @Test
    void differentTopicNotReceived() {
        var bus = new SimpleEventBus();
        var received = new AtomicInteger();
        bus.subscribe("topic-a", String.class, e -> received.incrementAndGet());
        bus.publish("topic-b", "hello");
        assertEquals(0, received.get());
    }

    @Test
    void multipleSubscribersSameTopic() {
        var bus = new SimpleEventBus();
        var count = new AtomicInteger();
        bus.subscribe("test", String.class, e -> count.incrementAndGet());
        bus.subscribe("test", String.class, e -> count.incrementAndGet());
        bus.publish("test", "hello");
        assertEquals(2, count.get());
    }

}
