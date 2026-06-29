package io.opencode.core.provider;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UsageTrackerTest {
    private final UsageTracker tracker = new UsageTracker();

    @Test
    void startsEmpty() {
        var total = tracker.getTotal();
        assertEquals(0, total.promptTokens());
        assertEquals(0, total.completionTokens());
        assertEquals(0, total.totalTokens());
    }

    @Test
    void tracksSingleUsage() {
        tracker.track("gpt-4o", new ChatResponse.Usage(100, 20, 120));
        var total = tracker.getTotal();
        assertEquals(100, total.promptTokens());
        assertEquals(20, total.completionTokens());
        assertEquals(120, total.totalTokens());
    }

    @Test
    void accumulatesAcrossCalls() {
        tracker.track("gpt-4o", new ChatResponse.Usage(100, 20, 120));
        tracker.track("gpt-4o", new ChatResponse.Usage(50, 10, 60));
        var total = tracker.getTotal();
        assertEquals(150, total.promptTokens());
        assertEquals(30, total.completionTokens());
        assertEquals(180, total.totalTokens());
    }

    @Test
    void tracksByModelSeparately() {
        tracker.track("gpt-4o", new ChatResponse.Usage(100, 20, 120));
        tracker.track("claude-3", new ChatResponse.Usage(200, 50, 250));
        var byModel = tracker.byModel();
        assertEquals(2, byModel.size());
        assertEquals(100, byModel.get("gpt-4o").promptTokens());
        assertEquals(200, byModel.get("claude-3").promptTokens());
    }

    @Test
    void resetClearsAll() {
        tracker.track("gpt-4o", new ChatResponse.Usage(100, 20, 120));
        tracker.reset();
        assertEquals(0, tracker.getTotal().totalTokens());
        assertEquals(0, tracker.byModel().size());
    }
}
