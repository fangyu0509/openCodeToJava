package io.opencode.core.provider;

import io.opencode.core.config.OpenCodeConfig;
import io.opencode.core.provider.impl.OpenAICompatibleProvider;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * ProviderConfigurer —— 提供商配置初始化器
 * 在 Spring 容器启动后，根据 opencode.json 中的 providers 配置
 * 动态配置已有 Provider 或注册新的 OpenAI 兼容提供商
 */
@Component
public class ProviderConfigurer {
    private static final Logger log = LoggerFactory.getLogger(ProviderConfigurer.class);

    // 已知的第三方 OpenAI 兼容服务商及其默认 API 端点
    private static final java.util.Map<String, String> KNOWN_PROVIDERS = java.util.Map.ofEntries(
        java.util.Map.entry("deepseek", "https://api.deepseek.com/v1"),
        java.util.Map.entry("openrouter", "https://openrouter.ai/api/v1"),
        java.util.Map.entry("groq", "https://api.groq.com/openai/v1"),
        java.util.Map.entry("together", "https://api.together.xyz/v1"),
        java.util.Map.entry("mistral", "https://api.mistral.ai/v1"),
        java.util.Map.entry("xai", "https://api.x.ai/v1"),
        java.util.Map.entry("github", "https://models.inference.ai.azure.com"),
        java.util.Map.entry("perplexity", "https://api.perplexity.ai"),
        java.util.Map.entry("cerebras", "https://api.cerebras.ai/v1"),
        java.util.Map.entry("fireworks", "https://api.fireworks.ai/inference/v1"),
        java.util.Map.entry("deepinfra", "https://api.deepinfra.com/v1/openai"),
        java.util.Map.entry("anyscale", "https://api.endpoints.anyscale.com/v1"),
        java.util.Map.entry("replicate", "https://api.replicate.com/v1"),
        java.util.Map.entry("huggingface", "https://api-inference.huggingface.co/v1"),
        java.util.Map.entry("nebius", "https://api.nebius.ai/v1")
    );

    private final OpenCodeConfig config;
    private final List<Provider> providers;
    private final ProviderRegistry registry;

    public ProviderConfigurer(OpenCodeConfig config, List<Provider> providers, ProviderRegistry registry) {
        this.config = config;
        this.providers = providers;
        this.registry = registry;
    }

    /**
     * 在 Bean 初始化后执行配置：
     * 1. 对已存在的内置 Provider 调用 configure() 注入凭证和 URL
     * 2. 对未注册的第三方服务商，动态创建 OpenAICompatibleProvider 并注册
     * 3. 缺少 apiKey 或 baseUrl 时跳过并记录警告
     */
    @PostConstruct
    public void configure() {
        for (var providerConfig : config.providers()) {
            var id = providerConfig.id();
            // API Key 优先读取配置值，若为空则从环境变量获取
            var apiKey = providerConfig.apiKey()
                .orElseGet(() -> System.getenv(providerConfig.apiKeyEnvVar()));
            // baseUrl 优先读取配置值，若为空则从已知服务商映射获取
            var baseUrl = providerConfig.baseUrl()
                .orElseGet(() -> KNOWN_PROVIDERS.get(id));

            // 检查是否已有同名的内置 Provider（如 openai、anthropic）
            var existing = providers.stream()
                .filter(p -> p.name().equals(id))
                .findFirst();

            if (existing.isPresent()) {
                // 对已存在的 ConfigurableProvider 注入配置
                var p = existing.get();
                if (p instanceof ConfigurableProvider cp) {
                    cp.configure(apiKey, baseUrl);
                    log.info("Configured provider '{}' (baseUrl={})", id,
                        baseUrl != null ? baseUrl : "(default)");
                }
            } else if (apiKey != null && !apiKey.isBlank() && baseUrl != null) {
                // 对 OpenAI 兼容的第三方服务商，动态创建并注册
                var compatible = new OpenAICompatibleProvider(id, apiKey, baseUrl);
                providerConfig.defaultModel().ifPresent(m -> compatible.setDefaultModel(m.modelId()));
                registry.register(compatible);
                log.info("Registered OpenAI-compatible provider '{}' (baseUrl={})", id, baseUrl);
            } else {
                log.warn("Provider '{}' configured in opencode.json but missing apiKey or baseUrl", id);
            }
        }
    }
}
