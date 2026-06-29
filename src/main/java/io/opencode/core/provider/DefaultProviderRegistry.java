package io.opencode.core.provider;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * DefaultProviderRegistry —— ProviderRegistry 的默认实现
 * 基于 ConcurrentHashMap 实现线程安全的提供商注册与查找
 * 通过 Spring 构造注入自动收集所有 Provider Bean
 */
@Service
public class DefaultProviderRegistry implements ProviderRegistry {
    // 线程安全的提供商映射，key = provider name，value = Provider 实例
    private final ConcurrentHashMap<String, Provider> providers = new ConcurrentHashMap<>();

    /**
     * 构造时自动将所有 Provider Bean 按名称注册到映射中
     * @param providerList Spring 注入的所有 Provider 实现
     */
    public DefaultProviderRegistry(List<Provider> providerList) {
        providerList.forEach(p -> providers.put(p.name(), p));
    }

    /** 注册或覆盖一个提供商实例 */
    @Override
    public void register(Provider provider) {
        providers.put(provider.name(), provider);
    }

    /** 根据 ID 查找提供商，未找到时返回 Optional.empty() */
    @Override
    public Optional<Provider> getProvider(String id) {
        return Optional.ofNullable(providers.get(id));
    }

    /** 返回所有已注册提供商的不可修改列表 */
    @Override
    public List<Provider> allProviders() {
        return List.copyOf(providers.values());
    }

    /**
     * 遍历所有提供商，返回第一个声明了默认模型的 ModelRef
     * 用于自动选择可用模型时的回退逻辑
     */
    @Override
    public Optional<ModelRef> defaultModel() {
        return providers.values().stream()
            .map(Provider::defaultModel)
            .filter(Optional::isPresent)
            .map(Optional::get)
            .findFirst();
    }
}
