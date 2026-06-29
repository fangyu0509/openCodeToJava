package io.opencode.core.provider;

/**
 * ConfigurableProvider 接口 —— 支持动态配置的提供商
 * 允许在运行时通过 apiKey 和 baseUrl 对 Provider 进行配置，
 * 常用于从配置文件或环境变量读取凭证后注入
 */
public interface ConfigurableProvider {
    /** 配置 API 密钥和基础 URL，实现类可选择性地忽略空值 */
    void configure(String apiKey, String baseUrl);
}
