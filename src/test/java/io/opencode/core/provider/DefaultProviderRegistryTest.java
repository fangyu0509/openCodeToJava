package io.opencode.core.provider;

import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

class DefaultProviderRegistryTest {

    @Test
    void registerAndLookup() {
        var reg = new DefaultProviderRegistry(java.util.List.of());
        var p = new StubProvider("test");
        reg.register(p);
        assertTrue(reg.getProvider("test").isPresent());
        assertEquals("test", reg.getProvider("test").get().name());
    }

    @Test
    void unknownProviderReturnsEmpty() {
        var reg = new DefaultProviderRegistry(java.util.List.of());
        assertTrue(reg.getProvider("nonexistent").isEmpty());
    }

    @Test
    void listAllProviders() {
        var reg = new DefaultProviderRegistry(java.util.List.of());
        reg.register(new StubProvider("a"));
        reg.register(new StubProvider("b"));
        assertEquals(2, reg.allProviders().size());
    }

    @Test
    void defaultModelFromFirstProvider() {
        var reg = new DefaultProviderRegistry(java.util.List.of());
        reg.register(new StubProvider("a"));
        reg.register(new StubProvider("b"));
        assertTrue(reg.defaultModel().isPresent());
    }

    @Test
    void defaultModelEmptyWhenNoProviders() {
        var reg = new DefaultProviderRegistry(java.util.List.of());
        assertTrue(reg.defaultModel().isEmpty());
    }

    static class StubProvider implements Provider {
        private final String name;
        StubProvider(String name) { this.name = name; }
        @Override public String name() { return name; }
        @Override public CompletableFuture<ChatResponse> chat(ChatRequest r) { return CompletableFuture.completedFuture(ChatResponse.text("", ChatResponse.Usage.EMPTY)); }
        @Override public CompletableFuture<Void> chatStream(ChatRequest r, StreamObserver<ChatChunk> o) { return CompletableFuture.completedFuture(null); }
        @Override public Optional<ModelRef> defaultModel() { return Optional.of(ModelRef.of(name, "test")); }
        @Override public boolean supportsModel(String m) { return true; }
    }
}
