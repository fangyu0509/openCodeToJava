package io.opencode.core.lsp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

// LSP 服务管理器：管理 LSP 服务器配置、生命周期和操作执行
@Service
public class LspService {
    private static final Logger log = LoggerFactory.getLogger(LspService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Map<String, LspServer> servers = new ConcurrentHashMap<>();   // 运行中的服务器实例
    private final Map<String, ServerConfig> configs = new LinkedHashMap<>();    // 已注册的服务器配置

    // LSP 服务器配置记录
    public record ServerConfig(String name, String command, List<String> args, List<String> extensions) {}

    public LspService() {
        registerDefaults();
    }

    // 注册默认的 LSP 服务器配置（覆盖主流语言）
    private void registerDefaults() {
        register(new ServerConfig("typescript", "npx", List.of("typescript-language-server", "--stdio"), List.of(".ts", ".tsx", ".js", ".jsx")));
        register(new ServerConfig("eslint", "npx", List.of("eslint_d", "start"), List.of(".ts", ".tsx", ".js", ".jsx")));
        register(new ServerConfig("java", "java", List.of("-jar", "~/.opencode/lsp/jdtls/plugins/org.eclipse.equinox.launcher_*.jar"), List.of(".java")));
        register(new ServerConfig("python", "npx", List.of("pyright", "--stdio"), List.of(".py")));
        register(new ServerConfig("rust", "npx", List.of("rust-analyzer"), List.of(".rs")));
        register(new ServerConfig("go", "npx", List.of("gopls"), List.of(".go")));
        register(new ServerConfig("kotlin", "npx", List.of("kotlin-language-server"), List.of(".kt", ".kts")));
        register(new ServerConfig("csharp", "npx", List.of("csharp-ls"), List.of(".cs")));
        register(new ServerConfig("php", "npx", List.of("intelephense", "--stdio"), List.of(".php")));
        register(new ServerConfig("ruby", "npx", List.of("solargraph", "socket", "--port", "7658"), List.of(".rb")));
        register(new ServerConfig("cpp", "npx", List.of("clangd"), List.of(".c", ".h", ".cpp", ".hpp", ".cc")));
        register(new ServerConfig("dart", "npx", List.of("dart", "language-server"), List.of(".dart")));
        register(new ServerConfig("css", "npx", List.of("vscode-css-language-server", "--stdio"), List.of(".css", ".scss", ".less")));
        register(new ServerConfig("html", "npx", List.of("vscode-html-language-server", "--stdio"), List.of(".html")));
        register(new ServerConfig("json", "npx", List.of("vscode-json-language-server", "--stdio"), List.of(".json", ".jsonc")));
        register(new ServerConfig("yaml", "npx", List.of("yaml-language-server", "--stdio"), List.of(".yaml", ".yml")));
        register(new ServerConfig("docker", "npx", List.of("docker-langserver", "--stdio"), List.of("Dockerfile")));
        register(new ServerConfig("bash", "npx", List.of("bash-language-server", "start"), List.of(".sh", ".bash")));
        register(new ServerConfig("terraform", "npx", List.of("terraform-ls", "serve"), List.of(".tf")));
        register(new ServerConfig("sql", "npx", List.of("sql-language-server"), List.of(".sql")));
        register(new ServerConfig("vue", "npx", List.of("vue-language-server", "--stdio"), List.of(".vue")));
        register(new ServerConfig("svelte", "npx", List.of("svelte-language-server", "--stdio"), List.of(".svelte")));
        register(new ServerConfig("astro", "npx", List.of("astro-ls", "--stdio"), List.of(".astro")));
        register(new ServerConfig("markdown", "npx", List.of("remark-language-server"), List.of(".md")));
        register(new ServerConfig("graphql", "npx", List.of("graphql-language-service-server"), List.of(".graphql", ".gql")));
    }

    // 注册（或覆盖）一个 LSP 服务器配置
    public void register(ServerConfig config) {
        configs.put(config.name(), config);
    }

    // 列出所有已注册的服务器配置
    public List<ServerConfig> listConfigs() {
        return List.copyOf(configs.values());
    }

    // 根据文件扩展名找到匹配的服务器配置
    public Optional<ServerConfig> findConfigFor(Path filePath) {
        var name = filePath.toString().toLowerCase();
        for (var cfg : configs.values()) {
            for (var ext : cfg.extensions()) {
                if (name.endsWith(ext)) return Optional.of(cfg);
            }
        }
        return Optional.empty();
    }

    // 获取或创建 LSP 服务器实例（使用"配置名@根路径"作为缓存键）
    public LspServer getOrCreate(Path rootPath, ServerConfig config) throws IOException {
        var key = config.name() + "@" + rootPath;
        var existing = servers.get(key);
        if (existing != null && existing.isAlive()) return existing;

        var command = config.command();
        var args = config.args().toArray(String[]::new);
        var server = new LspServer(config.name(), rootPath, command, args);
        server.initialize().thenAccept(caps -> log.info("LSP server '{}' initialized: {}", config.name(), caps));
        servers.put(key, server);
        return server;
    }

    // 根据名称和根路径查找已运行的服务器
    public Optional<LspServer> getServer(String name, Path rootPath) {
        return Optional.ofNullable(servers.get(name + "@" + rootPath));
    }

    // 执行 LSP 操作（definition/references/hover/diagnostics）
    public CompletableFuture<JsonNode> execute(String action, Path filePath, Path rootPath, int line, int character) {
        try {
            var config = findConfigFor(filePath);
            if (config.isEmpty()) {
                return CompletableFuture.failedFuture(new RuntimeException("No LSP server for " + filePath));
            }
            var server = getOrCreate(rootPath, config.get());
            if (!server.isInitialized()) {
                return CompletableFuture.failedFuture(new RuntimeException("LSP server not initialized"));
            }
            return switch (action) {
                case "definition" -> server.goToDefinition(filePath, line, character);
                case "references" -> server.findReferences(filePath, line, character);
                case "hover" -> server.hover(filePath, line, character);
                case "diagnostics" -> {
                    // 诊断需要先打开文档
                    server.openDocument(filePath);
                    yield server.getDiagnostics(filePath);
                }
                default -> CompletableFuture.failedFuture(new RuntimeException("Unknown LSP action: " + action));
            };
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    // 关闭所有 LSP 服务器并清空缓存
    public void closeAll() {
        servers.values().forEach(s -> { try { s.close(); } catch (Exception e) {} });
        servers.clear();
    }

    // 返回处于活动状态的服务器数量
    public int activeCount() { return (int) servers.values().stream().filter(LspServer::isAlive).count(); }
}
