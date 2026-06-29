package io.opencode.core.config;

import io.opencode.core.agent.AgentConfig;
import io.opencode.core.provider.ModelRef;
import io.opencode.core.session.Session;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

// 核心配置记录，映射 opencode.json 中的顶层配置
public record OpenCodeConfig(
    String version,                               // 配置版本号
    Path workspaceDir,                            // 工作空间根目录
    Optional<Path> dataDir,                       // 数据目录（会话持久化等），默认 .opencode
    Optional<ModelRef> defaultModel,              // 默认模型引用，例如 openai/gpt-4
    List<ProviderConfig> providers,               // 已配置的 AI 提供商列表
    List<AgentConfig> agents,                     // 已配置的代理列表
    ServerConfig server,                          // HTTP 服务器配置（端口/主机）
    List<McpServerConfig> mcpServers,             // MCP 服务器配置列表
    boolean telemetry,                            // 是否启用遥测
    int compactThreshold,                         // 会话压缩触发阈值（消息数）
    int compactReserved,                          // 压缩后保留的最新消息数
    List<ReferenceConfig> references              // 引用配置列表
) {
    // 全局单例，供无依赖注入的模块访问配置
    private static final AtomicReference<OpenCodeConfig> INSTANCE = new AtomicReference<>();

    // 设置全局配置实例
    public static void setInstance(OpenCodeConfig config) {
        INSTANCE.set(config);
    }

    // 获取全局配置实例
    public static OpenCodeConfig instance() {
        var c = INSTANCE.get();
        return c != null ? c : null;
    }

    // 获取压缩阈值，未配置时默认 100000 条消息
    public static int getCompactThreshold() {
        var c = INSTANCE.get();
        return c != null ? c.compactThreshold : 100_000;
    }

    // 获取压缩后保留数，未配置时默认保留 5 条
    public static int getCompactReserved() {
        var c = INSTANCE.get();
        return c != null ? c.compactReserved : 5;
    }

    // 提供商配置：ID、API 密钥环境变量名、可选密钥/基础 URL/默认模型
    public record ProviderConfig(
        String id,
        String apiKeyEnvVar,
        Optional<String> apiKey,
        Optional<String> baseUrl,
        Optional<ModelRef> defaultModel
    ) {}

    // MCP（Model Context Protocol）服务器配置：名称、启动命令、参数、环境变量
    public record McpServerConfig(
        String name,
        String command,
        List<String> args,
        Map<String, String> env
    ) {}

    // HTTP 服务器配置
    public record ServerConfig(
        int port,
        String host
    ) {
        public static final int DEFAULT_PORT = 4096;
        public static final String DEFAULT_HOST = "127.0.0.1";

        // 返回默认服务器配置（127.0.0.1:4096）
        public static ServerConfig defaultConfig() {
            return new ServerConfig(DEFAULT_PORT, DEFAULT_HOST);
        }
    }

    // 生成带合理默认值的配置，仅需工作目录
    public static OpenCodeConfig defaults(Path workspaceDir) {
        return new OpenCodeConfig(
            "0.1.0",
            workspaceDir,
            Optional.of(workspaceDir.resolve(".opencode")),
            Optional.empty(),
            List.of(),
            List.of(),
            ServerConfig.defaultConfig(),
            List.of(),
            false,
            100_000,
            5,
            List.of()
        );
    }
}
