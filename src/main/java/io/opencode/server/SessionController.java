package io.opencode.server;

import io.opencode.core.agent.AgentConfig;
import io.opencode.core.agent.AgentLoop;
import io.opencode.core.agent.AgentMode;
import io.opencode.core.agent.BuiltinAgent;
import io.opencode.core.event.Disposable;
import io.opencode.core.event.EventBus;
import io.opencode.core.config.OpenCodeConfig;
import io.opencode.core.model.SessionId;
import io.opencode.core.plugin.PluginManager;
import io.opencode.core.skill.SkillService;
import io.opencode.core.provider.ModelRef;
import io.opencode.core.provider.ProviderRegistry;
import io.opencode.core.provider.UsageTracker;
import io.opencode.core.session.FileSession;
import io.opencode.core.session.InMemorySession;
import io.opencode.core.session.Message;
import io.opencode.core.session.SessionManager;
import io.opencode.core.session.SharedSessionService;
import io.opencode.core.tool.util.FileSearchService;
import io.opencode.core.util.ImageUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

// 会话管理 REST 控制器，提供健康检查、会话 CRUD、消息发送、文件上传、搜索等功能
@RestController
@RequestMapping("/api")
@CrossOrigin(originPatterns = "*", allowedHeaders = "*")
public class SessionController {
    private final SessionManager sessionManager;
    private final AgentLoop agentLoop;
    private final ProviderRegistry providerRegistry;
    private final EventBus eventBus;
    // 事件流 Sink 映射，用于 SSE 推送
    private final Map<SessionId, Sinks.Many<String>> sinks = new ConcurrentHashMap<>();
    private final Map<SessionId, Disposable> subscriptions = new ConcurrentHashMap<>();
    private final UsageTracker usageTracker;
    private final OpenCodeConfig config;
    private final PluginManager pluginManager;
    private final FileSearchService fileSearchService;
    private final SharedSessionService sharedSessionService;
    private final SkillService skillService;

    public SessionController(SessionManager sessionManager, AgentLoop agentLoop,
                             ProviderRegistry providerRegistry, EventBus eventBus,
                             UsageTracker usageTracker,
                             OpenCodeConfig config,
                             PluginManager pluginManager,
                             FileSearchService fileSearchService,
                             SharedSessionService sharedSessionService,
                             SkillService skillService) {
        this.sessionManager = sessionManager;
        this.agentLoop = agentLoop;
        this.providerRegistry = providerRegistry;
        this.eventBus = eventBus;
        this.usageTracker = usageTracker;
        this.config = config;
        this.pluginManager = pluginManager;
        this.fileSearchService = fileSearchService;
        this.sharedSessionService = sharedSessionService;
        this.skillService = skillService;
    }

    // 健康检查端点：返回服务器状态、会话数、提供商及插件数量
    @GetMapping("/health")
    public Map<String, Object> health() {
        var m = new LinkedHashMap<String, Object>();
        m.put("status", "UP");
        m.put("sessions", sessionManager.count());
        m.put("providers", providerRegistry.allProviders().stream().map(p -> p.name()).toList());
        m.put("plugins", pluginManager.listPlugins().size());
        return m;
    }

    // 全文搜索会话消息内容，返回匹配的消息摘要
    @GetMapping("/search/sessions")
    public List<SessionHit> searchSessions(@RequestParam("q") String query,
                                           @RequestParam(value = "max", defaultValue = "20") int max) {
        var q = query.toLowerCase();
        return sessionManager.all().stream()
            .flatMap(s -> s.messages().stream()
                .filter(m -> m instanceof Message.TextMessage t && t.text().toLowerCase().contains(q))
                .map(m -> {
                    var t = (Message.TextMessage) m;
                    var snippet = t.text();
                    if (snippet.length() > 100) snippet = snippet.substring(0, 97) + "...";
                    return new SessionHit(s.id().value(), s.title(), t.role(), snippet, t.timestamp());
                }))
            .limit(max)
            .toList();
    }

    record SessionHit(String sessionId, String title, String role, String snippet, long timestamp) {}

    // 搜索工作空间文件
    @GetMapping("/search/files")
    public List<FileSearchService.FileEntry> searchFiles(@RequestParam("query") String query,
                                                         @RequestParam(value = "max", defaultValue = "20") int max) {
        return fileSearchService.search(query, max);
    }

    // 上传文件到会话：支持文本和二进制文件，图片会自动缩小尺寸
    @PostMapping(value = "/session/{id}/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<Map<String, Object>> uploadFile(@PathVariable String id, @RequestPart("file") FilePart filePart) {
        var sessionOpt = sessionManager.get(id);
        if (sessionOpt.isEmpty()) {
            return Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found: " + id));
        }
        var session = sessionOpt.get();
        return filePart.content()
            .reduce(new byte[0], (acc, buf) -> {
                var bytes = new byte[buf.readableByteCount()];
                buf.read(bytes);
                var combined = new byte[acc.length + bytes.length];
                System.arraycopy(acc, 0, combined, 0, acc.length);
                System.arraycopy(bytes, 0, combined, acc.length, bytes.length);
                return combined;
            })
            .map(bytes -> {
                var ext = filePart.filename().contains(".")
                    ? filePart.filename().substring(filePart.filename().lastIndexOf('.') + 1).toLowerCase()
                    : "";
                // 判断是否为纯文本文件（基于扩展名）
                var isText = List.of("txt", "md", "java", "kt", "py", "js", "ts", "jsx", "tsx",
                    "json", "xml", "yaml", "yml", "toml", "properties", "css", "html", "sql",
                    "sh", "bat", "gradle", "xml", "cfg", "conf", "ini").contains(ext);
                var contentStr = isText ? new String(bytes, java.nio.charset.StandardCharsets.UTF_8) : Base64.getEncoder().encodeToString(bytes);
                var mediaType = filePart.headers().getContentType() != null
                    ? filePart.headers().getContentType().toString()
                    : (isText ? "text/plain" : "application/octet-stream");
                // 图片文件自动调整到最大尺寸
                var finalBytes = (isText || !ImageUtils.isImage(mediaType))
                    ? bytes
                    : ImageUtils.resizeToMaxDimension(bytes, ImageUtils.detectFormat(filePart.filename()));
                var finalContent = Base64.getEncoder().encodeToString(finalBytes);
                var part = new io.opencode.core.session.FilePart(
                    filePart.filename(),
                    Optional.of(finalContent),
                    Optional.of(mediaType),
                    Optional.empty()
                );
                var fileMsg = new Message.FileMessage(
                    io.opencode.core.model.MessageId.random(), "user", part, System.currentTimeMillis()
                );
                session.append(fileMsg);
                var result = new LinkedHashMap<String, Object>();
                result.put("name", filePart.filename());
                result.put("size", bytes.length);
                result.put("type", isText ? "text" : "binary");
                return result;
            });
    }

    // 列出所有可用技能
    @GetMapping("/skills")
    public List<SkillInfo> listSkills() {
        return skillService.listSkills().stream()
            .map(s -> new SkillInfo(s.name(), s.description()))
            .toList();
    }

    record SkillInfo(String name, String description) {}

    // 列出所有内置代理
    @GetMapping("/agents")
    public List<AgentInfo> listAgents() {
        return Arrays.stream(BuiltinAgent.values())
            .map(a -> new AgentInfo(a.id(), a.mode().name(), a.description()))
            .toList();
    }

    record AgentInfo(String name, String mode, String description) {}

    // 列出所有 AI 提供商及其默认模型
    @GetMapping("/providers")
    public List<ProviderInfo> listProviders() {
        return providerRegistry.allProviders().stream()
            .map(p -> new ProviderInfo(p.name(), p.defaultModel().map(m -> m.modelId()).orElse("default")))
            .toList();
    }

    record ProviderInfo(String name, String defaultModel) {}

    // 创建新会话，可选指定代理和模型
    @PostMapping("/session")
    public Mono<SessionResponse> createSession(@RequestBody(required = false) CreateSessionRequest request) {
        var builder = request != null && request.agent() != null
            ? AgentConfig.builder(request.agent())
            : AgentConfig.builder("build");
        builder.mode(AgentMode.PRIMARY);
        if (request != null && request.model() != null) {
            var parts = request.model().split(":", 2);
            var providerId = parts[0];
            var modelId = parts.length > 1 ? parts[1] : "default";
            builder.model(ModelRef.of(providerId, modelId));
        }
        if (request != null && "plan".equals(request.agent())) {
            builder.mode(AgentMode.PLAN);
        }
        var agentConfig = builder.build();
        // 根据是否配置了 dataDir 选择持久化或内存会话
        var session = config.dataDir().isPresent()
            ? new FileSession(agentConfig, config.dataDir().get())
            : new InMemorySession(agentConfig);
        sessionManager.create(session);
        return Mono.just(new SessionResponse(session.id().value(), session.agentConfig().name(), session.title(), session.messages().size()));
    }

    // 列出所有会话
    @GetMapping("/sessions")
    public List<SessionResponse> listSessions() {
        return sessionManager.all().stream()
            .map(s -> new SessionResponse(s.id().value(), s.agentConfig().name(), s.title(), s.messages().size()))
            .toList();
    }

    // 删除指定会话并清理相关资源
    @DeleteMapping("/session/{id}")
    public Mono<Void> deleteSession(@PathVariable String id) {
        var sessionOpt = sessionManager.get(id);
        if (sessionOpt.isEmpty()) {
            return Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found: " + id));
        }
        sessionManager.remove(sessionOpt.get().id());
        var sub = subscriptions.remove(sessionOpt.get().id());
        if (sub != null) sub.dispose();
        sinks.remove(sessionOpt.get().id());
        return Mono.empty();
    }

    // 列出所有已安装的插件
    @GetMapping("/plugins")
    public List<PluginManager.PluginInfo> listPlugins() {
        return pluginManager.listPlugins();
    }

    // 分享会话，返回分享 ID 和 URL
    @PostMapping("/session/{id}/share")
    public Mono<Map<String, String>> shareSession(@PathVariable String id) {
        return sessionManager.get(id)
            .map(s -> {
                var shareId = sharedSessionService.share(s);
                return Map.of("shareId", shareId, "url", "/shared/" + shareId);
            })
            .map(Mono::just)
            .orElse(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found: " + id)));
    }

    // 获取已分享的会话内容
    @GetMapping("/shared/{shareId}")
    public Mono<SharedSessionService.ShareData> getSharedSession(@PathVariable String shareId) {
        var shared = sharedSessionService.get(shareId);
        if (shared == null) {
            return Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Share not found: " + shareId));
        }
        return Mono.just(new SharedSessionService.ShareData(
            shared.messages().stream()
                .map(m -> {
                    String type;
                    String text;
                    if (m instanceof Message.TextMessage t) {
                        type = "text"; text = t.text();
                    } else if (m instanceof Message.ToolCallMessage t) {
                        type = "tool_call"; text = t.args().toString();
                    } else if (m instanceof Message.ToolResultMessage t) {
                        type = "tool_result"; text = t.output();
                    } else if (m instanceof Message.FileMessage t) {
                        type = "file"; text = t.file().path();
                    } else {
                        type = "unknown"; text = "";
                    }
                    return new SharedSessionService.MessageData(m.role(), type, text, m.timestamp());
                })
                .toList(),
            shared.agent(),
            shared.createdAt()
        ));
    }

    // 获取 API 用量统计
    @GetMapping("/usage")
    public UsageResponse getUsage() {
        var total = usageTracker.getTotal();
        return new UsageResponse(total.promptTokens(), total.completionTokens(), total.totalTokens(), usageTracker.byModel());
    }

    // 获取单个会话的概要信息
    @GetMapping("/session/{id}")
    public Mono<SessionResponse> getSession(@PathVariable String id) {
        return sessionManager.get(id)
            .map(s -> new SessionResponse(s.id().value(), s.agentConfig().name(), s.title(), s.messages().size()))
            .map(Mono::just)
            .orElse(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found: " + id)));
    }

    // 获取会话的所有消息（按时间戳排序）
    @GetMapping("/session/{id}/messages")
    public Mono<List<SessionMessageResponse>> getSessionMessages(@PathVariable String id) {
        return sessionManager.get(id)
            .map(s -> s.messages().stream()
                .sorted(java.util.Comparator.comparingLong(io.opencode.core.session.Message::timestamp))
                .map(m -> {
                    String type;
                    String text;
                    if (m instanceof Message.TextMessage t) {
                        type = "text"; text = t.text();
                    } else if (m instanceof Message.ToolCallMessage t) {
                        type = "tool_call"; text = t.args().toString();
                    } else if (m instanceof Message.ToolResultMessage t) {
                        type = "tool_result"; text = t.output();
                    } else if (m instanceof Message.FileMessage t) {
                        type = "file"; text = t.file().path();
                    } else {
                        type = "unknown"; text = "";
                    }
                    return new SessionMessageResponse(type, text, m.role(), m.timestamp());
                })
                .toList())
            .map(Mono::just)
            .orElse(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found: " + id)));
    }

    // Fork（复制）会话到指定的消息索引处
    @PostMapping("/session/{id}/fork")
    public Mono<Map<String, String>> forkSession(@PathVariable String id, @RequestBody ForkRequest request) {
        var sessionOpt = sessionManager.get(id);
        if (sessionOpt.isEmpty()) {
            return Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found: " + id));
        }
        var session = sessionOpt.get();
        var forked = session.fork(request.messageIndex());
        sessionManager.create(forked);
        return Mono.just(Map.of("id", forked.id().value()));
    }

    // 向会话发送消息并触发代理处理（非流式，等待完整响应）
    @PostMapping("/session/{id}/message")
    public Mono<MessageResponse> sendMessage(@PathVariable String id, @RequestBody MessageRequest request) {
        var sessionOpt = sessionManager.get(id);
        if (sessionOpt.isEmpty()) {
            return Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found: " + id));
        }
        var session = sessionOpt.get();
        var sink = sinks.get(session.id());

        return Mono.fromFuture(agentLoop.process(session, request.text(), session.agentConfig()))
            .map(response -> new MessageResponse(response.type().name(), response.text().orElse(""), response.stepsUsed()))
            .onErrorResume(e -> Mono.just(new MessageResponse("ERROR", e.getMessage(), 0)));
    }

    // SSE 流式获取会话更新（通过 EventBus 订阅实时推送）
    @GetMapping(value = "/session/{id}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> streamSession(@PathVariable String id) {
        var sessionOpt = sessionManager.get(id);
        if (sessionOpt.isEmpty()) {
            return Flux.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found: " + id));
        }
        var sessionId = sessionOpt.get().id();
        Sinks.Many<String> sink = Sinks.many().multicast().onBackpressureBuffer();
        sinks.put(sessionId, sink);

        var topic = "session:" + sessionId.value();
        var disposable = eventBus.subscribe(topic, String.class, event -> {
            sink.tryEmitNext(event);
        });
        subscriptions.put(sessionId, disposable);

        return sink.asFlux().doOnCancel(() -> {
            sinks.remove(sessionId);
            var sub = subscriptions.remove(sessionId);
            if (sub != null) sub.dispose();
        });
    }

    // ---- 内部请求/响应记录类型 ----
    record SessionResponse(String id, String agent, String title, int messageCount) {}
    record CreateSessionRequest(String agent, String model) {}
    record MessageRequest(String text) {}
    record ForkRequest(int messageIndex) {}
    record MessageResponse(String type, String text, int steps) {}
    record SessionMessageResponse(String type, String text, String role, long timestamp) {}
    record UsageResponse(long promptTokens, long completionTokens, long totalTokens, java.util.Map<String, UsageTracker.ModelUsage> byModel) {}
}
