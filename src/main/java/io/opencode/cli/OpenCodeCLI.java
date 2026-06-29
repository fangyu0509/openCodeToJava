package io.opencode.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.opencode.core.agent.AgentConfig;
import io.opencode.core.agent.AgentLoop;
import io.opencode.core.agent.AgentMode;
import io.opencode.core.agent.AgentResponse;
import io.opencode.core.config.OpenCodeConfig;
import io.opencode.core.mcp.McpService;
import io.opencode.core.session.InMemorySession;
import io.opencode.core.session.Session;
import io.opencode.core.tool.ToolRegistry;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.regex.Pattern;

/**
 * OpenCode 交互式命令行界面（CLI）
 * 仅在 "cli" Spring profile 激活时生效，提供用户与 AI 代理交互的终端界面
 * 支持多行输入、SSE 事件格式化输出、MCP 服务器管理等
 */
@Component
@Profile("cli")
public class OpenCodeCLI implements CommandLineRunner {

    // JSON 解析器，用于解析 SSE 事件中的 JSON 数据
    private static final ObjectMapper MAPPER = new ObjectMapper();
    // SSE 事件格式匹配：event: xxx 和 data: xxx
    private static final Pattern SSE_EVENT = Pattern.compile("^event: (.+)$", Pattern.MULTILINE);
    private static final Pattern SSE_DATA = Pattern.compile("^data: (.+)$", Pattern.MULTILINE);

    // ANSI 颜色/样式转义码，用于终端输出美化
    private static final String RESET = "\u001B[0m";
    private static final String CYAN = "\u001B[36m";
    private static final String GREEN = "\u001B[32m";
    private static final String YELLOW = "\u001B[33m";
    private static final String RED = "\u001B[31m";
    private static final String BLUE = "\u001B[34m";
    private static final String BOLD = "\u001B[1m";
    private static final String DIM = "\u001B[2m";

    // UI 图标字符
    private static final String TOOL_START = "\u2699";   // ⚙ 工具调用开始
    private static final String TOOL_END = "\u2714";     // ✔ 工具调用完成
    private static final String ERROR_ICON = "\u2716";   // ✖ 错误
    private static final String ROBOT = "\uD83E\uDD16";  // 🤖 AI 回复

    private final OpenCodeConfig config;
    private final ToolRegistry toolRegistry;
    private final AgentLoop agentLoop;
    private final McpService mcpService;

    public OpenCodeCLI(OpenCodeConfig config, ToolRegistry toolRegistry, AgentLoop agentLoop, McpService mcpService) {
        this.config = config;
        this.toolRegistry = toolRegistry;
        this.agentLoop = agentLoop;
        this.mcpService = mcpService;
    }

    /**
     * CLI 入口：启动 REPL（读取-执行-输出循环）
     * 处理用户输入，调用 AgentLoop 处理，并格式化输出 AI 的响应
     */
    @Override
    public void run(String... args) {
        printBanner();
        var agentConfig = AgentConfig.builder("build").mode(AgentMode.PRIMARY).build();
        var session = new InMemorySession(agentConfig);

        try (var scanner = new Scanner(System.in)) {
            while (true) {
                System.out.print(CYAN + "opencode> " + RESET);
                if (!scanner.hasNextLine()) break;
                var input = readMultiLine(scanner).strip();
                if (input.isEmpty()) continue;
                if ("exit".equalsIgnoreCase(input) || "quit".equalsIgnoreCase(input)) break;
                if ("/help".equalsIgnoreCase(input)) { printHelp(); continue; }

                if (input.startsWith("/mcp ")) { handleMcpCommand(input); continue; }

                var sb = new StringBuilder();

                try {
                    // 向 AgentLoop 提交用户输入，返回 AgentResponse
                    // 通过事件回调实时打印 SSE 格式的中间结果
                    var response = agentLoop.process(session, input, agentConfig, event -> {
                        var formatted = formatEvent(event);
                        if (!formatted.isEmpty()) {
                            System.out.println(formatted);
                            sb.append(formatted).append("\n");
                        }
                    }).get();

                    // 如果最终文本与之前打印的不同，额外输出一次
                    var text = response.text().orElse("").strip();
                    if (!text.isEmpty() && !text.equals(sb.toString().strip())) {
                        System.out.println(GREEN + ROBOT + " " + text + RESET);
                    }
                    // 错误响应时用红色输出错误信息
                    if (response.type() == AgentResponse.Type.ERROR) {
                        System.out.println(RED + ERROR_ICON + " Error: " + RESET + text);
                    }
                    System.out.println();
                } catch (Exception e) {
                    System.out.println(RED + ERROR_ICON + " " + e.getMessage() + RESET);
                }
            }
        }
        System.out.println(DIM + "\nGoodbye!" + RESET);
    }

    /**
     * 读取多行输入：如果当前行以反斜杠结尾，则继续读取下一行
     * 支持将多行内容拼接为单字符串
     */
    private String readMultiLine(Scanner scanner) {
        var input = new StringBuilder();
        while (true) {
            var line = scanner.nextLine();
            if (line.endsWith("\\")) {
                input.append(line, 0, line.length() - 1).append("\n");
                System.out.print(CYAN + "       > " + RESET);
            } else {
                input.append(line);
                break;
            }
        }
        return input.toString();
    }

    /**
     * 格式化 SSE（Server-Sent Events）事件为终端可读的文本
     * 支持以下几种事件类型，每种用不同的颜色和图标展示：
     * - token: AI 生成的文本片段（灰色）
     * - tool_call: 工具调用开始（显示工具名称）
     * - tool_result: 工具调用完成
     * - message: AI 完整回复
     * - ask_permission: 向用户询问许可
     * - error: 错误信息
     */
    private String formatEvent(String sse) {
        try {
            var eventMatcher = SSE_EVENT.matcher(sse);
            var dataMatcher = SSE_DATA.matcher(sse);
            if (!eventMatcher.find() || !dataMatcher.find()) {
                return sse.strip();
            }
            var event = eventMatcher.group(1);
            var data = dataMatcher.group(1);
            var root = MAPPER.readTree(data);

            return switch (event) {
                case "token" -> {
                    var text = root.has("text") ? root.get("text").asText() : "";
                    yield DIM + text + RESET;
                }
                case "tool_call" -> {
                    var tool = root.has("tool") ? root.get("tool").asText() : "?";
                    yield DIM + TOOL_START + " Calling " + BOLD + tool + RESET + DIM + "..." + RESET;
                }
                case "tool_result" -> {
                    var tool = root.has("tool") ? root.get("tool").asText() : "?";
                    yield DIM + "  " + TOOL_END + " " + tool + " completed" + RESET;
                }
                case "message" -> {
                    var role = root.has("role") ? root.get("role").asText() : "";
                    var text = root.has("text") ? root.get("text").asText() : "";
                    if ("assistant".equals(role)) {
                        yield GREEN + ROBOT + " " + text + RESET;
                    }
                    yield "";
                }
                case "ask_permission" -> {
                    var question = root.has("question") ? root.get("question").asText() : "";
                    yield YELLOW + "? " + question + RESET;
                }
                case "error" -> {
                    var msg = root.has("message") ? root.get("message").asText() : "";
                    yield RED + ERROR_ICON + " " + msg + RESET;
                }
                // 以下事件类型在 CLI 中忽略不输出
                case "done", "plan", "compaction", "compaction_error" -> "";
                default -> "";
            };
        } catch (Exception e) {
            return "";
        }
    }

    /** 打印 CLI 启动欢迎横幅，显示版本号、工作空间和可用工具数量 */
    private void printBanner() {
        System.out.println();
        System.out.println(BOLD + "  OpenCode CLI v" + config.version() + RESET);
        System.out.println(DIM + "  Workspace: " + config.workspaceDir() + RESET);
        System.out.println(DIM + "  Tools: " + toolRegistry.ids().size() + " | Type /help for commands" + RESET);
        System.out.println();
    }

    /** 打印帮助信息，列出所有可用的 CLI 命令 */
    private void printHelp() {
        System.out.println();
        System.out.println(BOLD + "  Commands:" + RESET);
        System.out.println("    /help       Show this help");
        System.out.println("    /agent <n>  Switch agent (build/plan/general/explore/architect/ask)");
        System.out.println("    /skills     List available skills");
        System.out.println("    /load <n>   Load a skill's instructions");
        System.out.println("    /compact    Compact conversation context");
        System.out.println("    /stats      Show token usage statistics");
        System.out.println("    /models     List available models");
        System.out.println("    /export     Export conversation as markdown");
        System.out.println("    /undo       Undo last change");
        System.out.println("    /redo       Redo last undone change");
        System.out.println("    /share      Share this session");
        System.out.println("    /unshare    Stop sharing this session");
        System.out.println("    /mcp list   List MCP servers");
        System.out.println("    /mcp start <name> <command> [args]  Start MCP server");
        System.out.println("    /mcp stop <name>   Stop MCP server");
        System.out.println("    exit/quit   Exit the CLI");
        System.out.println();
        System.out.println(DIM + "  Use \\ at end of line for multi-line input." + RESET);
        System.out.println();
    }

    /**
     * 处理 /mcp 命令（MCP 服务器管理）
     * 支持：list（列出所有服务器）、start（启动新服务器）、stop（停止服务器）
     */
    private void handleMcpCommand(String input) {
        var parts = input.substring(5).strip().split("\\s+", 3);
        var sub = parts.length > 0 ? parts[0] : "";
        switch (sub) {
            case "list" -> {
                var servers = mcpService.listServers();
                if (servers.isEmpty()) {
                    System.out.println(DIM + "  No MCP servers running." + RESET);
                } else {
                    System.out.println(BOLD + "  MCP Servers:" + RESET);
                    for (var s : servers) {
                        var alive = mcpService.isRunning(s);
                        System.out.println("    " + (alive ? GREEN + "\u25CF" : RED + "\u25CB") + " " + s + RESET);
                    }
                }
            }
            case "start" -> {
                if (parts.length < 3) {
                    System.out.println(RED + "  Usage: /mcp start <name> <command> [args]" + RESET);
                    return;
                }
                var name = parts[1];
                var cmdParts = parts[2].split("\\s+");
                var command = cmdParts[0];
                var args = Arrays.copyOfRange(cmdParts, 1, cmdParts.length);
                var cfg = new McpService.ServerConfig(name, command, List.of(args), Map.of());
                mcpService.startServer(cfg);
                System.out.println(GREEN + "  Started MCP server: " + name + RESET);
            }
            case "stop" -> {
                if (parts.length < 2) {
                    System.out.println(RED + "  Usage: /mcp stop <name>" + RESET);
                    return;
                }
                mcpService.stopServer(parts[1]);
                System.out.println(GREEN + "  Stopped MCP server: " + parts[1] + RESET);
            }
            default -> System.out.println(RED + "  Unknown MCP command. Try: /mcp list, /mcp start, /mcp stop" + RESET);
        }
    }
}
