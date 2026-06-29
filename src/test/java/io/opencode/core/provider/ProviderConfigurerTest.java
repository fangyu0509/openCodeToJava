package io.opencode.core.provider;

import io.opencode.core.config.OpenCodeConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProviderConfigurerTest {

    private OpenCodeConfig config;
    private ProviderRegistry registry;

    @BeforeEach
    void setUp() {
        config = new OpenCodeConfig("0.1.0", Path.of("."), Optional.empty(),
            Optional.empty(), List.of(), List.of(),
            OpenCodeConfig.ServerConfig.defaultConfig(), List.of(), false, 100_000, 5, List.of());
        registry = new DefaultProviderRegistry(List.of());
    }

    @Test
    void configuresMatchingProvider() {
        var providerConfig = new OpenCodeConfig.ProviderConfig("test", "API_KEY",
            Optional.of("sk-123"), Optional.empty(), Optional.empty());
        config = new OpenCodeConfig("0.1.0", Path.of("."), Optional.empty(),
            Optional.empty(), List.of(providerConfig), List.of(),
            OpenCodeConfig.ServerConfig.defaultConfig(), List.of(), false, 100_000, 5, List.of());

        var provider = mock(Provider.class, withSettings().extraInterfaces(ConfigurableProvider.class));
        when(provider.name()).thenReturn("test");
        var configurer = new ProviderConfigurer(config, List.of(provider), registry);
        configurer.configure();

        verify((ConfigurableProvider) provider).configure("sk-123", null);
    }

    @Test
    void skipsNonMatchingProvider() {
        var provider = mock(Provider.class, withSettings().extraInterfaces(ConfigurableProvider.class));
        when(provider.name()).thenReturn("other");
        var configurer = new ProviderConfigurer(config, List.of(provider), registry);
        configurer.configure();

        verify((ConfigurableProvider) provider, never()).configure(any(), any());
    }

    @Test
    void skipsNonConfigurableProvider() {
        var providerConfig = new OpenCodeConfig.ProviderConfig("test", "API_KEY",
            Optional.of("sk-123"), Optional.empty(), Optional.empty());
        config = new OpenCodeConfig("0.1.0", Path.of("."), Optional.empty(),
            Optional.empty(), List.of(providerConfig), List.of(),
            OpenCodeConfig.ServerConfig.defaultConfig(), List.of(), false, 100_000, 5, List.of());

        var provider = mock(Provider.class);
        when(provider.name()).thenReturn("test");
        var configurer = new ProviderConfigurer(config, List.of(provider), registry);
        assertDoesNotThrow(configurer::configure);
    }

    @Test
    void setsBaseUrl() {
        var providerConfig = new OpenCodeConfig.ProviderConfig("test", "API_KEY",
            Optional.of("sk-123"), Optional.of("https://custom.url"), Optional.empty());
        config = new OpenCodeConfig("0.1.0", Path.of("."), Optional.empty(),
            Optional.empty(), List.of(providerConfig), List.of(),
            OpenCodeConfig.ServerConfig.defaultConfig(), List.of(), false, 100_000, 5, List.of());

        var provider = mock(Provider.class, withSettings().extraInterfaces(ConfigurableProvider.class));
        when(provider.name()).thenReturn("test");
        var configurer = new ProviderConfigurer(config, List.of(provider), registry);
        configurer.configure();

        verify((ConfigurableProvider) provider).configure("sk-123", "https://custom.url");
    }

    @Test
    void noConfiguredProvidersDoesNothing() {
        var provider = mock(Provider.class, withSettings().extraInterfaces(ConfigurableProvider.class));
        when(provider.name()).thenReturn("test");
        var configurer = new ProviderConfigurer(config, List.of(provider), registry);
        assertDoesNotThrow(configurer::configure);
        verify((ConfigurableProvider) provider, never()).configure(any(), any());
    }

    @Test
    void usesEnvVarWhenApiKeyNotSet() {
        var providerConfig = new OpenCodeConfig.ProviderConfig("test", "TEST_API_KEY_ENV",
            Optional.empty(), Optional.empty(), Optional.empty());
        config = new OpenCodeConfig("0.1.0", Path.of("."), Optional.empty(),
            Optional.empty(), List.of(providerConfig), List.of(),
            OpenCodeConfig.ServerConfig.defaultConfig(), List.of(), false, 100_000, 5, List.of());

        var provider = mock(Provider.class, withSettings().extraInterfaces(ConfigurableProvider.class));
        when(provider.name()).thenReturn("test");
        var configurer = new ProviderConfigurer(config, List.of(provider), registry);
        configurer.configure();

        verify((ConfigurableProvider) provider).configure(any(), any());
    }

    @Test
    void registersUnknownProviderAsCompatible() {
        var providerConfig = new OpenCodeConfig.ProviderConfig("deepseek", "DEEPSEEK_API_KEY",
            Optional.of("sk-test"), Optional.of("https://api.deepseek.com/v1"), Optional.empty());
        config = new OpenCodeConfig("0.1.0", Path.of("."), Optional.empty(),
            Optional.empty(), List.of(providerConfig), List.of(),
            OpenCodeConfig.ServerConfig.defaultConfig(), List.of(), false, 100_000, 5, List.of());

        var configurer = new ProviderConfigurer(config, List.of(), registry);
        configurer.configure();

        var registered = registry.getProvider("deepseek");
        assertTrue(registered.isPresent());
        assertEquals("deepseek", registered.get().name());
    }

    @Test
    void skipsUnknownProviderWithoutApiKey() {
        var providerConfig = new OpenCodeConfig.ProviderConfig("deepseek", "DEEPSEEK_API_KEY",
            Optional.empty(), Optional.of("https://api.deepseek.com/v1"), Optional.empty());
        config = new OpenCodeConfig("0.1.0", Path.of("."), Optional.empty(),
            Optional.empty(), List.of(providerConfig), List.of(),
            OpenCodeConfig.ServerConfig.defaultConfig(), List.of(), false, 100_000, 5, List.of());

        var configurer = new ProviderConfigurer(config, List.of(), registry);
        configurer.configure();

        var registered = registry.getProvider("deepseek");
        assertTrue(registered.isEmpty());
    }

    @Test
    void usesKnownBaseUrlForCompatibleProvider() {
        var providerConfig = new OpenCodeConfig.ProviderConfig("groq", "GROQ_API_KEY",
            Optional.of("sk-test"), Optional.empty(), Optional.empty());
        config = new OpenCodeConfig("0.1.0", Path.of("."), Optional.empty(),
            Optional.empty(), List.of(providerConfig), List.of(),
            OpenCodeConfig.ServerConfig.defaultConfig(), List.of(), false, 100_000, 5, List.of());

        var configurer = new ProviderConfigurer(config, List.of(), registry);
        configurer.configure();

        var registered = registry.getProvider("groq");
        assertTrue(registered.isPresent());
        assertEquals("groq", registered.get().name());
    }

    private static org.mockito.MockSettings withSettings() {
        return org.mockito.Mockito.withSettings();
    }
}
