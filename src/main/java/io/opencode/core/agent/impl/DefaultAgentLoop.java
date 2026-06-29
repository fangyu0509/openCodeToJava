package io.opencode.core.agent.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.opencode.core.agent.AgentConfig;
import io.opencode.core.agent.AgentLoop;
import io.opencode.core.agent.AgentMode;
import io.opencode.core.agent.AgentResponse;
import io.opencode.core.agent.BuiltinAgent;
import io.opencode.core.config.OpenCodeConfig;
import io.opencode.core.event.EventBus;
import io.opencode.core.formatter.FormatterService;
import io.opencode.core.lsp.LspService;
import io.opencode.core.model.AbortSignal;
import io.opencode.core.model.MessageId;
import io.opencode.core.permission.PermissionAction;
import io.opencode.core.permission.PermissionChecker;
import io.opencode.core.plugin.PluginManager;
import io.opencode.core.prompt.PromptLoader;
import io.opencode.core.skill.SkillService;
import io.opencode.core.tool.util.ReferenceService;
import io.opencode.core.provider.ChatChunk;
import io.opencode.core.session.SharedSessionService;
import io.opencode.core.session.SnapshotManager;
import io.opencode.core.tool.util.FileSearchService;
import io.opencode.core.tool.util.ProjectAnalyzer;
import io.opencode.core.provider.ChatRequest;
import io.opencode.core.provider.ChatResponse;
import io.opencode.core.provider.ModelRef;
import io.opencode.core.provider.Provider;
import io.opencode.core.provider.ProviderRegistry;
import io.opencode.core.provider.StreamObserver;
import io.opencode.core.provider.UsageTracker;
import io.opencode.core.session.Message;
import io.opencode.core.session.Session;
import io.opencode.core.tool.ExecuteResult;
import io.opencode.core.tool.Tool;
import io.opencode.core.tool.ToolContext;
import io.opencode.core.tool.ToolRegistry;
import io.opencode.core.tool.util.ToolUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Map;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * 默认代理循环实现，核心类。
 * 负责：流式LLM调用、工具执行、会话压缩、计划模式、命令处理（/undo /redo /compact等）、
 * 权限检查、LSP诊断、插件事件触发、@引用解析等完整处理流程。
 */
@Service
public class DefaultAgentLoop implements AgentLoop {
    private static final Logger log = LoggerFactory.getLogger(DefaultAgentLoop.class);
    private static final int DEFAULT_MAX_STEPS = 50; // 默认最大执行步数
    // 代理线程池，守护线程模式，用于异步执行工具调用
    private static final ExecutorService AGENT_EXECUTOR = Executors.newFixedThreadPool(
        Runtime.getRuntime().availableProcessors(),
        r -> { var t = new Thread(r, "agent-loop"); t.setDaemon(true); return t; });

    // JVM关闭时优雅关闭线程池
    static {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            AGENT_EXECUTOR.shutdown();
        }));
    }

    private final ObjectMapper mapper = new ObjectMapper(); // JSON对象映射器

    // 注入的各类服务依赖
    private final ProviderRegistry providerRegistry;     // LLM提供者注册表
    private final ToolRegistry toolRegistry;             // 工具注册表
    private final PromptLoader promptLoader;             // 提示词加载器
    private final PermissionChecker permissionChecker;   // 权限检查器
    private final EventBus eventBus;                     // 事件总线
    private final UsageTracker usageTracker;             // Token用量跟踪器
    private final SnapshotManager snapshotManager;       // 文件快照管理器（支持undo/redo）
    private final ProjectAnalyzer projectAnalyzer;       // 项目分析器
    private final FileSearchService fileSearchService;   // 文件搜索服务
    private final SharedSessionService sharedSessionService; // 会话共享服务
    private final SkillService skillService;             // 技能服务
    private final PluginManager pluginManager;           // 插件管理器
    private final LspService lspService;                 // LSP语言服务
    private final FormatterService formatterService;     // 代码格式化服务
    private final ReferenceService referenceService;     // @引用解析服务

    public DefaultAgentLoop(ProviderRegistry providerRegistry, ToolRegistry toolRegistry,
                            PromptLoader promptLoader, PermissionChecker permissionChecker,
                            EventBus eventBus, UsageTracker usageTracker,
                            SnapshotManager snapshotManager,
                            ProjectAnalyzer projectAnalyzer,
                            FileSearchService fileSearchService,
                            SharedSessionService sharedSessionService,
                            SkillService skillService,
                            PluginManager pluginManager,
                            LspService lspService,
                            FormatterService formatterService,
                            ReferenceService referenceService) {
        this.providerRegistry = providerRegistry;
        this.toolRegistry = toolRegistry;
        this.promptLoader = promptLoader;
        this.permissionChecker = permissionChecker;
        this.eventBus = eventBus;
        this.usageTracker = usageTracker;
        this.snapshotManager = snapshotManager;
        this.projectAnalyzer = projectAnalyzer;
        this.fileSearchService = fileSearchService;
        this.sharedSessionService = sharedSessionService;
        this.skillService = skillService;
        this.pluginManager = pluginManager;
        this.lspService = lspService;
        this.formatterService = formatterService;
        this.referenceService = referenceService;
    }

    // 处理用户输入（无事件回调版本），委托给有回调的重载方法
    @Override
    public CompletableFuture<AgentResponse> process(Session session, String userInput, AgentConfig config) {
        return process(session, userInput, config, null);
    }

    /**
     * 核心处理方法，在 AGENT_EXECUTOR 线程池中异步执行。
     * 流程：
     * 1. 触发插件 sessionStart 事件
     * 2. 检查是否是以 / 开头的命令，如果是则走命令处理
     * 3. 解析 @引用
     * 4. 追加用户消息到会话
     * 5. 如果是 PLAN 模式，先生成计划
     * 6. 进入循环：构建请求 -> 流式调用LLM -> 处理文本/工具响应 -> 压缩上下文
     * 7. 返回最终结果或错误
     */
    @Override
    public CompletableFuture<AgentResponse> process(Session session, String userInput, AgentConfig config, Consumer<String> onEvent) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                var topic = "session:" + session.id().value();

                // 触发会话开始事件
                pluginManager.fireSessionStart(session);

                // 检查是否以 / 开头，如果是则进入命令处理
                var cmdResult = handleCommand(session, userInput, config, topic, onEvent);
                if (cmdResult != null) return cmdResult;

                // 解析 @引用（如 @file.java），将文件内容嵌入上下文
                var resolvedInput = resolveReferences(userInput);
                var provider = resolveProvider(config);

                // 将用户消息追加到会话并触发事件
                var userMsg = Message.userText(resolvedInput);
                session.append(userMsg);
                pluginManager.fireMessage(session, userMsg);
                emitAndPublish(onEvent, topic, "message", "{\"role\":\"user\",\"id\":\"" + userMsg.id().value() + "\",\"text\":\"" + escapeJson(resolvedInput) + "\"}");

                // PLAN 模式下先生成执行计划（只生成文本，不执行工具）
                if (config.mode() == AgentMode.PLAN) {
                    var plan = executePlanMode(session, provider, config, topic, onEvent);
                    if (plan != null) return plan;
                }

                var maxSteps = config.maxSteps().orElse(DEFAULT_MAX_STEPS);
                var steps = 0;

                // 主循环：每次迭代调用LLM，处理文本或工具调用
                while (steps < maxSteps) {
                    steps++;
                    // 获取模型引用
                    var modelRef = config.model()
                        .orElse(provider.defaultModel()
                            .orElseThrow(() -> new RuntimeException("No default model configured")));

                    // 构建工具定义列表（根据权限过滤）
                    var toolDefs = buildToolDefinitions(config);
                    // 构建聊天请求，开启流式输出
                    var request = ChatRequest.builder(buildSystemPrompt(config), session.messages(), modelRef)
                        .temperature(config.temperature().orElse(null))
                        .tools(toolDefs)
                        .stream(true)
                        .build();

                    // 流式调用LLM，收集响应
                    var response = streamChat(provider, request, modelRef, topic, onEvent);
                    usageTracker.track(modelRef.modelId(), response.usage());

                    // 如果消息体积超过压缩阈值（且不是最后一步），执行上下文压缩
                    var messagesSize = session.messages().stream()
                        .mapToInt(m -> m.toString().length()).sum();
                    if (messagesSize > OpenCodeConfig.getCompactThreshold() && steps < maxSteps - 1) {
                        compactSession(session, provider, modelRef, config, topic, onEvent);
                    }

                    // 文本响应：追加助手消息并返回
                    if (response.type() == ChatResponse.Type.TEXT) {
                        var text = response.text().orElse("");
                        var msg = Message.assistantText(text);
                        session.append(msg);
                        emitAndPublish(onEvent, topic, "message", "{\"role\":\"assistant\",\"id\":\"" + msg.id().value() + "\",\"text\":\"" + escapeJson(text) + "\"}");
                        emitAndPublish(onEvent, topic, "done", "{\"type\":\"text\",\"steps\":" + steps + "}");
                        return AgentResponse.text(text, steps);
                    }

                    // 工具调用响应：依次执行每个工具
                    if (response.type() == ChatResponse.Type.TOOL_CALL) {
                        var calls = response.toolCalls();
                        // 先将所有工具调用追加到会话
                        for (var call : calls) {
                            session.append(Message.toolCall(call.toolId(), call.id(), call.args()));

                            emitAndPublish(onEvent, topic, "tool_call",
                                "{\"tool\":\"" + escapeJson(call.toolId()) + "\",\"callId\":\"" + escapeJson(call.id()) + "\",\"args\":" + call.args().toString() + "}");
                        }

                        // 依次执行每个工具调用
                        for (var call : calls) {
                            var toolOpt = toolRegistry.get(call.toolId());
                            // 工具不存在时追加错误结果
                            if (toolOpt.isEmpty()) {
                                session.append(Message.toolResult(call.toolId(), call.id(),
                                    "Unknown tool: " + call.toolId()));
                                emitAndPublish(onEvent, topic, "tool_result",
                                    "{\"tool\":\"" + escapeJson(call.toolId()) + "\",\"error\":\"Unknown tool\"}");
                                continue;
                            }

                            var tool = toolOpt.get();
                            // 提取资源路径用于权限检查
                            var resource = extractResource(call.toolId(), call.args());
                            var permAction = permissionChecker.check(call.toolId(), resource, config.permission());

                            // 权限拒绝时跳过
                            if (permAction == PermissionAction.DENY) {
                                session.append(Message.toolResult(call.toolId(), call.id(),
                                    "Permission denied: " + call.toolId() + " on " + resource));
                                emitAndPublish(onEvent, topic, "tool_result",
                                    "{\"tool\":\"" + escapeJson(call.toolId()) + "\",\"error\":\"Permission denied\",\"resource\":\"" + escapeJson(resource) + "\"}");
                                continue;
                            }

                            // 创建工具执行上下文
                            var ctx = new ToolContext(
                                session.id(), MessageId.random(), config.name(),
                                new AbortSignal(), call.id(), session.messages(),
                                r -> {}, q -> log.info("[Agent asks] {}", q)
                            );

                            // 权限为 ASK 时发送询问事件（前端可弹窗确认）
                            if (permAction == PermissionAction.ASK) {
                                emitAndPublish(onEvent, topic, "ask_permission",
                                    "{\"tool\":\"" + escapeJson(call.toolId()) + "\",\"resource\":\"" + escapeJson(resource) + "\"}");
                            }

                            // 检查是否被中止
                            try {
                                ctx.abort().check();
                            } catch (AbortSignal.AbortException ae) {
                                session.append(Message.toolResult(call.toolId(), call.id(), "Aborted: " + ae.getMessage()));
                                emitAndPublish(onEvent, topic, "tool_result",
                                    "{\"tool\":\"" + escapeJson(call.toolId()) + "\",\"error\":\"Aborted\",\"message\":\"" + escapeJson(ae.getMessage()) + "\"}");
                                continue;
                            }

                            // 执行工具（异步，带120秒超时）
                            ExecuteResult<?> result;
                            try {
                                pluginManager.fireToolExecute(call.toolId(),
                                    Map.of("sessionId", session.id().value(), "messageId", call.id()));
                                var future = CompletableFuture.supplyAsync(() -> tool.execute(call.args(), ctx), AGENT_EXECUTOR);
                                result = future.orTimeout(120, TimeUnit.SECONDS).join();
                            } catch (CompletionException ce) {
                                var cause = ce.getCause();
                                if (cause instanceof TimeoutException) {
                                    result = ExecuteResult.of("Error", Tool.Metadata.EMPTY,
                                        "Tool execution timed out after 120s: " + call.toolId());
                                } else {
                                    var errMsg = (cause != null ? cause : ce).getMessage();
                                    if (errMsg == null) errMsg = "Tool execution failed: " + call.toolId();
                                    result = ExecuteResult.of("Error", Tool.Metadata.EMPTY, errMsg);
                                }
                            } catch (Exception e) {
                                result = ExecuteResult.of("Error", Tool.Metadata.EMPTY,
                                    tool.formatValidationError(e));
                            }

                        // 截断过长的工具输出
                        var truncated = ToolUtils.truncate(result.output());
                        pluginManager.fireToolResult(call.toolId(), truncated);
                        session.append(Message.toolResult(call.toolId(), call.id(), truncated));
                        emitAndPublish(onEvent, topic, "tool_result",
                            "{\"tool\":\"" + escapeJson(call.toolId()) + "\",\"output\":\"" + escapeJson(truncated) + "\",\"truncated\":" + (truncated.length() < result.output().length()) + "}");

                        // 如果是 write 或 edit 工具修改了文件，执行格式化并检查 LSP 诊断
                        if (("write".equals(call.toolId()) || "edit".equals(call.toolId()))
                            && call.args() != null && call.args().has("filePath")) {
                            var fp = call.args().get("filePath").asText();
                            formatterService.format(java.nio.file.Path.of(fp));
                            checkDiagnostics(fp, session, topic, onEvent);
                        }
                        }
                    }
                }

                // 步数用尽，返回最大步数响应
                emitAndPublish(onEvent, topic, "done", "{\"type\":\"max_steps\",\"steps\":" + maxSteps + "}");
                return AgentResponse.maxSteps(maxSteps);
            } catch (Exception e) {
                // 全局异常处理，触发插件 error 事件
                var topic = "session:" + session.id().value();
                pluginManager.fireError(session, e.getMessage());
                emitAndPublish(onEvent, topic, "error", "{\"message\":\"" + escapeJson(e.getMessage()) + "\"}");
                return AgentResponse.error(e.getMessage());
            }
        }, AGENT_EXECUTOR);
    }

    /**
     * 执行计划模式。
     * 构建只包含系统提示词（不含工具定义）的请求，让LLM生成执行计划。
     * 计划只作为文本追加到会话，不执行任何工具。
     */
    private AgentResponse executePlanMode(Session session, Provider provider,
                                           AgentConfig config, String topic, Consumer<String> onEvent) throws Exception {
        var modelRef = config.model()
            .orElse(provider.defaultModel()
                .orElseThrow(() -> new RuntimeException("No default model configured")));
        var planRequest = ChatRequest.builder(buildPlanPrompt(config), session.messages(), modelRef)
            .stream(true).build();

        var planResponse = streamChat(provider, planRequest, modelRef, topic, onEvent);
        var planText = planResponse.text().orElse("");

        var planMsg = Message.assistantText(planText);
        session.append(planMsg);
        emitAndPublish(onEvent, topic, "plan",
            "{\"text\":\"" + escapeJson(planText) + "\"}");
        return null;
    }

    /**
     * 流式调用LLM。
     * 使用 StreamObserver 逐块处理响应，支持文本增量和工具调用增量两种模式。
     * 文本增量通过 onEvent 实时推送 "token" 事件。
     * 工具调用增量先累积工具调用，最终在 onComplete 时构建完整的 ChatResponse。
     */
    private ChatResponse streamChat(Provider provider, ChatRequest request, ModelRef modelRef,
                                     String topic, Consumer<String> onEvent) throws Exception {
        var textBuilder = new StringBuilder();
        var toolCalls = new ArrayList<ChatResponse.ToolCall>();
        var usageRef = new ChatResponse.Usage[] { ChatResponse.Usage.EMPTY };
        var future = new CompletableFuture<ChatResponse>();

        provider.chatStream(request, new StreamObserver<ChatChunk>() {
            @Override
            public void onNext(ChatChunk chunk) {
                // 处理文本增量，实时推送 token 事件
                chunk.textDelta().ifPresent(delta -> {
                    textBuilder.append(delta);
                    emitAndPublish(onEvent, topic, "token",
                        "{\"delta\":\"" + escapeJson(delta) + "\"}");
                });
                // 处理工具调用增量，累计构建工具调用列表
                chunk.toolCallDelta().ifPresent(tc -> {
                    try {
                        var args = mapper.readTree(tc.argsJsonDelta());
                        toolCalls.add(new ChatResponse.ToolCall(tc.id(), tc.toolId(), args));
                    } catch (Exception e) {
                        toolCalls.add(new ChatResponse.ToolCall(tc.id(), tc.toolId(), null));
                    }
                });
                chunk.usage().ifPresent(u -> usageRef[0] = u);
            }

            @Override
            public void onError(Throwable error) {
                future.completeExceptionally(error);
            }

            @Override
            public void onComplete() {
                // 根据是否有工具调用，返回不同类型响应
                if (!toolCalls.isEmpty()) {
                    future.complete(ChatResponse.toolCalls(toolCalls, usageRef[0]));
                } else {
                    future.complete(ChatResponse.text(textBuilder.toString(), usageRef[0]));
                }
            }
        });

        return future.get(120, TimeUnit.SECONDS);
    }

    // 发送 SSE 格式事件：先通过 onEvent 回调推送给调用方，再通过 EventBus 广播
    private void emitAndPublish(Consumer<String> onEvent, String topic, String event, String data) {
        var sse = "event: " + event + "\ndata: " + data + "\n\n";
        if (onEvent != null) {
            onEvent.accept(sse);
        }
        eventBus.publish(topic, sse);
    }

    /**
     * 解析 @引用。
     * 匹配输入中的 @filename 模式，尝试精确匹配或模糊搜索文件，
     * 将文件内容嵌入上下文。使用 LinkedHashSet 避免重复引用。
     */
    private String resolveReferences(String input) {
        var root = java.nio.file.Path.of(System.getProperty("user.dir"));
        var sb = new StringBuilder();
        var matcher = java.util.regex.Pattern.compile("@([\\w./\\\\-]+)").matcher(input);
        var resolved = new java.util.LinkedHashSet<String>();
        var result = new StringBuilder();
        var lastEnd = 0;

        while (matcher.find()) {
            var ref = matcher.group(1);
            result.append(input, lastEnd, matcher.start());
            // Try exact match first, then fuzzy search
            var filePath = root.resolve(ref);
            java.nio.file.Path foundPath = null;
            if (java.nio.file.Files.isRegularFile(filePath)) {
                foundPath = filePath;
            } else {
                var results = fileSearchService.search(ref, 5);
                if (!results.isEmpty()) {
                    foundPath = root.resolve(results.get(0).path());
                }
            }

            if (foundPath != null && resolved.add(foundPath.toString())) {
                try {
                    var content = java.nio.file.Files.readString(foundPath);
                    var relPath = root.relativize(foundPath).toString();
                    result.append("(").append(relPath).append(")");
                    sb.append("\n<file path=\"").append(escapeJson(relPath)).append("\">\n")
                        .append(content).append("\n</file>\n");
                } catch (Exception e) {
                    result.append("@").append(ref);
                }
            } else {
                result.append("@").append(ref);
            }
            lastEnd = matcher.end();
        }
        result.append(input.substring(lastEnd));

        if (!sb.isEmpty()) {
            return "Referenced files:\n" + sb + "\n---\n" + result;
        }
        return result.toString();
    }

    /**
     * 处理以 / 开头的命令。
     * 支持的命令：
     * /agent <name>    - 切换代理
     * /load <skill>    - 加载技能
     * /undo            - 撤销上次文件修改
     * /redo            - 重做上次撤销
     * /share           - 共享会话
     * /unshare         - 取消共享
     * /compact         - 手动压缩会话上下文
     * /models          - 列出可用模型
     * /stats           - 显示Token用量统计
     * /export          - 导出会话记录
     * /skills          - 列出可用技能
     * /init            - 初始化 AGENTS.md 项目配置文件
     * 如果输入不以 / 开头，返回 null 表示不是命令，走正常处理流程。
     */
    private AgentResponse handleCommand(Session session, String input, AgentConfig config, String topic, Consumer<String> onEvent) {
        var trimmed = input.trim();
        if (!trimmed.startsWith("/")) return null;

        var sid = session.id().value();

        // /agent 命令：切换内置代理
        if (trimmed.toLowerCase().startsWith("/agent ")) {
            var name = trimmed.substring(7).strip();
            try {
                var agent = BuiltinAgent.fromId(name);
                var cfg = AgentConfig.builder(name).mode(agent.mode()).build();
                session.updateAgentConfig(cfg);
                var msg = "Switched to agent: " + name + " (" + agent.description() + ")";
                session.append(Message.assistantText(msg));
                emitAndPublish(onEvent, topic, "message", "{\"role\":\"assistant\",\"text\":\"" + escapeJson(msg) + "\"}");
                emitAndPublish(onEvent, topic, "done", "{\"type\":\"agent\",\"agent\":\"" + name + "\"}");
                return AgentResponse.text(msg, 0);
            } catch (IllegalArgumentException e) {
                var available = java.util.Arrays.stream(BuiltinAgent.values()).map(BuiltinAgent::id).toList();
                var msg = "Unknown agent: " + name + ". Available: " + available;
                session.append(Message.assistantText(msg));
                emitAndPublish(onEvent, topic, "message", "{\"role\":\"assistant\",\"text\":\"" + escapeJson(msg) + "\"}");
                emitAndPublish(onEvent, topic, "done", "{\"type\":\"error\",\"message\":\"" + escapeJson(msg) + "\"}");
                return AgentResponse.text(msg, 0);
            }
        }

        // /load 命令：加载指定技能的指令到对话
        if (trimmed.toLowerCase().startsWith("/load ")) {
            var name = trimmed.substring(6).strip();
            var skillOpt = skillService.getSkill(name);
            String msg;
            if (skillOpt.isEmpty()) {
                msg = "Skill '" + name + "' not found. Use `/skills` to list available skills.";
            } else {
                var skill = skillOpt.get();
                msg = "## Loaded Skill: " + skill.name() + "\n\n" + skill.body();
            }
            session.append(Message.assistantText(msg));
            emitAndPublish(onEvent, topic, "message", "{\"role\":\"assistant\",\"text\":\"" + escapeJson(msg) + "\"}");
            emitAndPublish(onEvent, topic, "done", "{\"type\":\"load_skill\",\"skill\":\"" + escapeJson(name) + "\"}");
            return AgentResponse.text(msg, 0);
        }

        switch (trimmed.toLowerCase()) {
            case "/undo" -> {
                if (!snapshotManager.canUndo(sid)) {
                    var msg = "Nothing to undo.";
                    session.append(Message.assistantText(msg));
                    emitAndPublish(onEvent, topic, "message", "{\"role\":\"assistant\",\"text\":\"" + escapeJson(msg) + "\"}");
                    emitAndPublish(onEvent, topic, "done", "{\"type\":\"undo\",\"message\":\"" + escapeJson(msg) + "\"}");
                    return AgentResponse.text(msg, 0);
                }
                var success = snapshotManager.undo(sid);
                var remaining = snapshotManager.undoSize(sid);
                var msg = success
                    ? "Undid last change. " + remaining + " undo step(s) remaining."
                    : "Failed to undo.";
                session.append(Message.assistantText(msg));
                emitAndPublish(onEvent, topic, "message", "{\"role\":\"assistant\",\"text\":\"" + escapeJson(msg) + "\"}");
                emitAndPublish(onEvent, topic, "done", "{\"type\":\"undo\",\"remaining\":" + remaining + "}");
                return AgentResponse.text(msg, 0);
            }
            case "/redo" -> {
                if (!snapshotManager.canRedo(sid)) {
                    var msg = "Nothing to redo.";
                    session.append(Message.assistantText(msg));
                    emitAndPublish(onEvent, topic, "message", "{\"role\":\"assistant\",\"text\":\"" + escapeJson(msg) + "\"}");
                    emitAndPublish(onEvent, topic, "done", "{\"type\":\"redo\",\"message\":\"" + escapeJson(msg) + "\"}");
                    return AgentResponse.text(msg, 0);
                }
                var success = snapshotManager.redo(sid);
                var remaining = snapshotManager.redoSize(sid);
                var msg = success
                    ? "Redid last undone change. " + remaining + " redo step(s) remaining."
                    : "Failed to redo.";
                session.append(Message.assistantText(msg));
                emitAndPublish(onEvent, topic, "message", "{\"role\":\"assistant\",\"text\":\"" + escapeJson(msg) + "\"}");
                emitAndPublish(onEvent, topic, "done", "{\"type\":\"redo\",\"remaining\":" + remaining + "}");
                return AgentResponse.text(msg, 0);
            }
            case "/share" -> {
                var shareId = sharedSessionService.share(session);
                var msg = "Session shared! ID: " + shareId + "\nShare URL: /shared/" + shareId;
                session.append(Message.assistantText(msg));
                emitAndPublish(onEvent, topic, "message", "{\"role\":\"assistant\",\"text\":\"" + escapeJson(msg) + "\"}");
                emitAndPublish(onEvent, topic, "done", "{\"type\":\"share\",\"id\":\"" + shareId + "\"}");
                return AgentResponse.text(msg, 0);
            }
            case "/unshare" -> {
                var ok = sharedSessionService.unshare(session.id().value());
                var msg = ok ? "Session unshared." : "Session was not shared.";
                session.append(Message.assistantText(msg));
                emitAndPublish(onEvent, topic, "message", "{\"role\":\"assistant\",\"text\":\"" + escapeJson(msg) + "\"}");
                emitAndPublish(onEvent, topic, "done", "{\"type\":\"unshare\",\"ok\":" + ok + "}");
                return AgentResponse.text(msg, 0);
            }
            case "/compact" -> {
                var modelRef = resolveModelRef(config);
                var provider = resolveProvider(config);
                compactSession(session, provider, modelRef, config, topic, onEvent);
                var msg = "Session compacted.";
                emitAndPublish(onEvent, topic, "message", "{\"role\":\"assistant\",\"text\":\"" + escapeJson(msg) + "\"}");
                emitAndPublish(onEvent, topic, "done", "{\"type\":\"compact\"}");
                return AgentResponse.text(msg, 0);
            }
            case "/models" -> {
                var providers = providerRegistry.allProviders();
                var sb = new StringBuilder("Available models:\n");
                for (var p : providers) {
                    sb.append("- **").append(p.name()).append("**: ");
                    sb.append(p.defaultModel().map(m -> m.modelId()).orElse("(no default)"));
                    sb.append("\n");
                }
                if (providers.isEmpty()) sb.append("No providers configured.");
                var msg = sb.toString();
                session.append(Message.assistantText(msg));
                emitAndPublish(onEvent, topic, "message", "{\"role\":\"assistant\",\"text\":\"" + escapeJson(msg) + "\"}");
                emitAndPublish(onEvent, topic, "done", "{\"type\":\"models\"}");
                return AgentResponse.text(msg, 0);
            }
            case "/stats" -> {
                var total = usageTracker.getTotal();
                var byModel = usageTracker.byModel();
                var sb = new StringBuilder("Usage stats:\n");
                sb.append("- Total tokens: ").append(total.totalTokens()).append("\n");
                sb.append("  - Prompt: ").append(total.promptTokens()).append("\n");
                sb.append("  - Completion: ").append(total.completionTokens()).append("\n");
                sb.append("\nBy model:\n");
                for (var entry : byModel.entrySet()) {
                    var m = entry.getValue();
                    sb.append("- **").append(entry.getKey()).append("**: ")
                        .append(m.totalTokens()).append(" tokens (prompt: ").append(m.promptTokens())
                        .append(", completion: ").append(m.completionTokens()).append(")\n");
                }
                if (byModel.isEmpty()) sb.append("  No usage data yet.\n");
                var msg = sb.toString();
                session.append(Message.assistantText(msg));
                emitAndPublish(onEvent, topic, "message", "{\"role\":\"assistant\",\"text\":\"" + escapeJson(msg) + "\"}");
                emitAndPublish(onEvent, topic, "done", "{\"type\":\"stats\"}");
                return AgentResponse.text(msg, 0);
            }
            case "/export" -> {
                var sb = new StringBuilder();
                sb.append("# Session: ").append(session.title()).append("\n\n");
                for (var m : session.messages()) {
                    if (m instanceof Message.TextMessage t) {
                        sb.append("### ").append(t.role()).append("\n").append(t.text()).append("\n\n");
                    } else if (m instanceof Message.ToolCallMessage t) {
                        sb.append("### tool_call: ").append(t.toolId()).append("\n");
                        sb.append(t.args().toPrettyString()).append("\n\n");
                    } else if (m instanceof Message.ToolResultMessage t) {
                        sb.append("### tool_result: ").append(t.toolId()).append("\n");
                        sb.append(t.output()).append("\n\n");
                    }
                }
                var msg = "```markdown\n" + sb.toString().strip() + "\n```";
                session.append(Message.assistantText(msg));
                emitAndPublish(onEvent, topic, "message", "{\"role\":\"assistant\",\"text\":\"" + escapeJson(msg) + "\"}");
                emitAndPublish(onEvent, topic, "done", "{\"type\":\"export\"}");
                return AgentResponse.text(msg, 0);
            }
            case "/skills" -> {
                var list = skillService.listSkills();
                String msg;
                if (list.isEmpty()) {
                    msg = "No skills loaded. Place SKILL.md files in .opencode/skills/ or ~/.config/opencode/skills/.";
                } else {
                    var sb = new StringBuilder("Available skills:\n");
                    for (var s : list) {
                        sb.append("- **").append(s.name()).append("**: ").append(s.description()).append("\n");
                    }
                    sb.append("\nUse `/load <skill-name>` to load a skill's instructions.");
                    msg = sb.toString();
                }
                session.append(Message.assistantText(msg));
                emitAndPublish(onEvent, topic, "message", "{\"role\":\"assistant\",\"text\":\"" + escapeJson(msg) + "\"}");
                emitAndPublish(onEvent, topic, "done", "{\"type\":\"skills\"}");
                return AgentResponse.text(msg, 0);
            }
            case "/init" -> {
                var rootPath = java.nio.file.Path.of(System.getProperty("user.dir"));
                var info = projectAnalyzer.analyze(rootPath);
                var agentsMd = projectAnalyzer.generateAgentsMd(info);
                var agentsPath = rootPath.resolve("AGENTS.md");
                try {
                    java.nio.file.Files.writeString(agentsPath, agentsMd);
                    var msg = "Initialized AGENTS.md for project '" + info.projectName()
                        + "' with " + info.languages().size() + " language(s), "
                        + info.fileCount() + " file(s).";
                    session.append(Message.assistantText(msg));
                    emitAndPublish(onEvent, topic, "message", "{\"role\":\"assistant\",\"text\":\"" + escapeJson(msg) + "\"}");
                    emitAndPublish(onEvent, topic, "done", "{\"type\":\"init\",\"project\":\"" + escapeJson(info.projectName()) + "\",\"files\":" + info.fileCount() + "}");
                    return AgentResponse.text(msg, 0);
                } catch (Exception e) {
                    var msg = "Failed to write AGENTS.md: " + e.getMessage();
                    session.append(Message.assistantText(msg));
                    emitAndPublish(onEvent, topic, "error", "{\"message\":\"" + escapeJson(msg) + "\"}");
                    return AgentResponse.text(msg, 0);
                }
            }
            default -> {
                return null;
            }
        }
    }

    // 转义 JSON 字符串中的特殊字符
    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    /**
     * 压缩会话上下文。
     * 当会话消息体积超过配置阈值时，调用LLM生成摘要，
     * 然后清空历史消息，仅保留压缩后的摘要消息。
     */
    private void compactSession(Session session, Provider provider, ModelRef modelRef,
                                 AgentConfig config, String topic, Consumer<String> onEvent) {
        try {
            var sysPrompt = "Summarize the conversation so far, preserving key context, decisions, and file paths.";
            var compactRequest = ChatRequest.builder(sysPrompt, session.messages(), modelRef)
                .stream(true).build();
            var compactResponse = streamChat(provider, compactRequest, modelRef, topic, onEvent);
            var summary = compactResponse.text().orElse("");

            if (summary.length() > 20) {
                session.clear(); // 清空原会话
                var summaryMsg = Message.assistantText("[Compacted] " + summary);
                session.append(summaryMsg);
                emitAndPublish(onEvent, topic, "compaction",
                    "{\"summary\":\"" + escapeJson(summary) + "\"}");
            }
        } catch (Exception e) {
            emitAndPublish(onEvent, topic, "compaction_error",
                "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
        }
    }

    // 解析模型引用：优先使用配置中的模型，否则用提供者的默认模型
    private ModelRef resolveModelRef(AgentConfig config) {
        if (config.model().isPresent()) return config.model().get();
        var provider = resolveProvider(config);
        return provider.defaultModel()
            .orElseThrow(() -> new RuntimeException("No default model configured"));
    }

    /**
     * 解析 LLM 提供者。
     * 优先使用配置中指定的提供者；如果没有指定，则按环境变量自动检测：
     * OPENAI_API_KEY -> openai, ANTHROPIC_API_KEY -> anthropic, 否则默认 ollama。
     */
    private Provider resolveProvider(AgentConfig config) {
        if (config.model().isPresent()) {
            return providerRegistry.getProvider(config.model().get().providerId())
                .orElseThrow(() -> new RuntimeException("Provider not found: " + config.model().get().providerId()));
        }
        if (isEnvSet("OPENAI_API_KEY")) {
            return providerRegistry.getProvider("openai")
                .orElseThrow(() -> new RuntimeException("OpenAI provider not registered"));
        }
        if (isEnvSet("ANTHROPIC_API_KEY")) {
            return providerRegistry.getProvider("anthropic")
                .orElseThrow(() -> new RuntimeException("Anthropic provider not registered"));
        }
        return providerRegistry.getProvider("ollama")
            .orElseThrow(() -> new RuntimeException("No provider available"));
    }

    // 检查环境变量是否存在且非空
    private boolean isEnvSet(String name) {
        var val = System.getenv(name);
        return val != null && !val.isBlank();
    }

    /**
     * 从工具调用参数中提取资源路径，用于权限检查。
     * 不同工具从不同参数中提取资源标识（如 filePath, path, command, url, query 等）。
     */
    private String extractResource(String toolId, com.fasterxml.jackson.databind.JsonNode args) {
        return switch (toolId) {
            case "read", "write", "edit" -> args.has("filePath") ? args.get("filePath").asText() : toolId;
            case "glob" -> args.has("path") ? args.get("path").asText() + "/" + args.get("pattern").asText() : toolId;
            case "grep" -> args.has("path") ? args.get("path").asText() : System.getProperty("user.dir");
            case "shell" -> args.has("command") ? args.get("command").asText() : toolId;
            case "webfetch" -> args.has("url") ? args.get("url").asText() : toolId;
            case "websearch" -> args.has("query") ? args.get("query").asText() : toolId;
            default -> toolId;
        };
    }

    /**
     * 检查文件的 LSP 诊断信息。
     * 在 write/edit 工具执行后调用，查询 LSP 服务的诊断结果，
     * 如果有错误/警告，将其追加到会话中供模型参考。
     */
    private void checkDiagnostics(String filePath, Session session, String topic, Consumer<String> onEvent) {
        try {
            var path = java.nio.file.Path.of(filePath);
            if (!java.nio.file.Files.isRegularFile(path)) return;
            var config = lspService.findConfigFor(path);
            if (config.isEmpty()) return;
            var result = lspService.execute("diagnostics", path,
                java.nio.file.Path.of(System.getProperty("user.dir")), 0, 0).get();
            if (result != null && result.has("diagnostics")) {
                var diags = result.get("diagnostics");
                if (diags.isArray() && diags.size() > 0) {
                    var sb = new StringBuilder();
                    sb.append("LSP diagnostics for ").append(filePath).append(":\n");
                    for (var d : diags) {
                        var sev = d.has("severity") ? d.get("severity").asInt() : 0;
                        var sevName = sev <= 2 ? "ERROR" : "WARNING";
                        var msg = d.get("message").asText();
                        var line = d.has("range") ? d.get("range").get("start").get("line").asInt() : 0;
                        sb.append("  [").append(sevName).append("] Line ").append(line).append(": ").append(msg).append("\n");
                    }
                    var diagMsg = sb.toString();
                    session.append(Message.toolResult("lsp", "lsp-diag", diagMsg));
                    emitAndPublish(onEvent, topic, "tool_result",
                        "{\"tool\":\"lsp\",\"output\":\"" + escapeJson(diagMsg) + "\"}");
                }
            }
        } catch (Exception e) {
            // silently ignore LSP diagnostic failures
        }
    }

    // 构建规划模式的系统提示词（仅列出工具，不赋予执行能力）
    private String buildPlanPrompt(AgentConfig config) {
        var sb = new StringBuilder();
        sb.append("You are a planning agent. Create a step-by-step plan to accomplish the user's request.\n");
        sb.append("List each step clearly. Be specific about which tools will be used.\n\n");
        sb.append("## Available Tools\n\n");
        var filter = new ToolRegistry.ToolFilter(null, config);
        for (var tool : toolRegistry.tools(filter)) {
            sb.append("### ").append(tool.id()).append("\n");
            sb.append(tool.description()).append("\n\n");
        }
        appendSkills(sb);
        sb.append("Output only the plan, no extra commentary.");
        return sb.toString();
    }

    // 构建系统提示词：加载提示词文件 + 工具定义 + 可用技能 + @引用上下文
    private String buildSystemPrompt(AgentConfig config) {
        var sb = new StringBuilder();
        // 加载提示词文件（如 AGENTS.md）
        var promptFile = promptLoader.load(config.name());
        if (!promptFile.isEmpty()) {
            sb.append(promptFile);
        } else {
            sb.append("You are an AI assistant with access to tools.\n");
        }
        // 追加配置中的自定义提示词
        config.prompt().ifPresent(p -> sb.append("\n").append(p).append("\n"));
        sb.append("\n## Available Tools\n\n");
        var filter = new ToolRegistry.ToolFilter(null, config);
        for (var tool : toolRegistry.tools(filter)) {
            sb.append("### ").append(tool.id()).append("\n");
            sb.append(tool.description()).append("\n\n");
        }
        appendSkills(sb);
        // 追加 @引用解析后的参考文件内容
        sb.append("\n## References\n\n");
        var refContent = referenceService.resolveAll(java.nio.file.Path.of(System.getProperty("user.dir")));
        if (!refContent.isBlank()) {
            sb.append(refContent).append("\n");
        } else {
            sb.append("No references configured.\n\n");
        }
        sb.append("Use tools when needed. Wait for results before continuing. Be concise.");
        return sb.toString();
    }

    // 将可用技能列表追加到系统提示词中
    private void appendSkills(StringBuilder sb) {
        var skillList = skillService.listSkills();
        if (!skillList.isEmpty()) {
            sb.append("\n## Available Skills\n\n");
            sb.append("These skills contain specialized instructions for specific tasks. ");
            sb.append("When a user request matches a skill, say \"I'll load the [skill-name] skill\" ");
            sb.append("and the skill content will be loaded into the conversation.\n\n");
            for (var skill : skillList) {
                sb.append("- **").append(skill.name()).append("**: ");
                sb.append(skill.description()).append("\n");
            }
            sb.append("\n");
        }
    }

    // 构建工具定义列表（用于 LLM 函数调用），根据配置过滤工具
    private List<ChatRequest.ToolDefinition> buildToolDefinitions(AgentConfig config) {
        var filter = new ToolRegistry.ToolFilter(null, config);
        return toolRegistry.tools(filter).stream()
            .map(t -> new ChatRequest.ToolDefinition(t.id(), t.description(), t.parameters().schema()))
            .collect(Collectors.toList());
    }
}
