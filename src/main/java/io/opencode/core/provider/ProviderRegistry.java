package io.opencode.core.provider;

import java.util.List;
import java.util.Optional;

/**
 * ProviderRegistry 接口 —— 提供商注册中心
 * 负责管理所有已注册的 Provider 实例，提供注册、查找和枚举能力
 */
public interface ProviderRegistry {
    /** 注册一个 Provider 到注册中心 */
    void register(Provider provider);

    /** 根据提供商 ID 查找对应的 Provider */
    Optional<Provider> getProvider(String id);

    /** 返回所有已注册的 Provider 列表（不可修改视图） */
    List<Provider> allProviders();

    /** 在所有已注册 Provider 中查找第一个可用的默认模型 */
    Optional<ModelRef> defaultModel();
}
