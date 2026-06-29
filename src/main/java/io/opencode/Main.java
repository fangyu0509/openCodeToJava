package io.opencode;

import io.opencode.core.config.ConfigLoader;
import io.opencode.core.session.SessionManager;
import io.opencode.core.session.Message;
import org.springframework.boot.SpringApplication;

import java.nio.file.Path;

/**
 * OpenCode 程序的入口主类，解析命令行参数并分发到相应的执行模式
 * 支持：serve/web（启动 Web 服务）、cli（交互式命令行）、session（会话管理命令）、acp（ACP 服务）
 */
public class Main {
    public static void main(String[] args) {
        // 无参数时打印使用帮助
        if (args.length == 0) {
            System.out.println("OpenCode - AI coding assistant");
            System.out.println();
            System.out.println("Usage: opencode <subcommand> [options]");
            System.out.println();
            System.out.println("Subcommands:");
            System.out.println("  serve          Start the web server (default)");
            System.out.println("  web            Start the web server (alias for serve)");
            System.out.println("  cli            Start interactive CLI");
            System.out.println("  session list   List all sessions");
            System.out.println("  session show   Show session details");
            System.out.println("  session export Export session as markdown");
            System.out.println("  acp            Start ACP server");
            System.out.println("  help           Show this help");
            return;
        }

        var subcommand = args[0];

        switch (subcommand) {
            case "serve", "web" -> {
                // 启动 Spring Boot Web 服务，可选指定端口
                System.setProperty("server.port", args.length > 1 ? args[1] : "8080");
                SpringApplication.run(OpenCodeApplication.class);
            }
            case "cli" -> {
                // 以 CLI 交互模式启动，激活 cli profile
                System.setProperty("spring.profiles.active", "cli");
                SpringApplication.run(OpenCodeApplication.class);
            }
            case "session" -> runSessionCommand(args);
            case "acp" -> {
                // 启动 ACP（Agent Communication Protocol）服务
                System.setProperty("spring.profiles.active", "acp");
                SpringApplication.run(OpenCodeApplication.class);
            }
            case "help", "--help", "-h" -> main(new String[0]);
            default -> {
                System.out.println("Unknown subcommand: " + subcommand);
                System.out.println("Run 'opencode help' for usage.");
            }
        }
    }

    /**
     * 处理 session 子命令：list（列出所有会话）、show（查看会话详情）、export（导出为 Markdown）
     * 通过 SessionManager 读取会话数据
     */
    private static void runSessionCommand(String[] args) {
        if (args.length < 2) {
            System.out.println("Usage: opencode session <list|show|export> [id]");
            return;
        }
        var ws = Path.of(System.getProperty("user.dir"));
        var config = new ConfigLoader().load(ws);
        var mgr = new SessionManager(config);
        mgr.loadSessions();

        switch (args[1]) {
            case "list" -> {
                var sessions = mgr.all();
                if (sessions.isEmpty()) {
                    System.out.println("No sessions.");
                } else {
                    System.out.println("Sessions:");
                    for (var s : sessions) {
                        var title = s.title() != null && !s.title().isBlank() ? s.title() : "(untitled)";
                        System.out.println("  " + s.id().value() + " - " + title);
                    }
                }
            }
            case "show" -> {
                if (args.length < 3) {
                    System.out.println("Usage: opencode session show <id>");
                    return;
                }
                var s = mgr.get(args[2]);
                if (s.isEmpty()) {
                    System.out.println("Session not found: " + args[2]);
                } else {
                    var session = s.get();
                    System.out.println("ID: " + session.id().value());
                    System.out.println("Title: " + (session.title() != null ? session.title() : "(none)"));
                    System.out.println("Agent: " + session.agentConfig().name());
                    System.out.println("Messages: " + session.messages().size());
                }
            }
            case "export" -> {
                if (args.length < 3) {
                    System.out.println("Usage: opencode session export <id>");
                    return;
                }
                var s = mgr.get(args[2]);
                if (s.isEmpty()) {
                    System.out.println("Session not found: " + args[2]);
                } else {
                    var session = s.get();
                    System.out.println("# " + (session.title() != null ? session.title() : "Session " + session.id().value()));
                    System.out.println();
                    // 遍历消息并按照角色格式化为 Markdown 输出
                    for (var msg : session.messages()) {
                        var role = switch (msg.role()) {
                            case "user" -> "**User**";
                            case "assistant" -> "**Assistant**";
                            case "tool" -> "**Tool**";
                            default -> "**" + msg.role() + "**";
                        };
                        // 根据消息类型提取主要内容
                        var content = "";
                        if (msg instanceof Message.TextMessage t) {
                            content = t.text();
                        } else if (msg instanceof Message.ToolCallMessage t) {
                            content = "Tool: " + t.toolId();
                        } else if (msg instanceof Message.ToolResultMessage t) {
                            content = t.output() != null ? t.output() : "";
                        } else if (msg instanceof Message.FileMessage t) {
                            content = "File: " + (t.file() != null ? t.file().path() : "unknown");
                        }
                        System.out.println("### " + role);
                        System.out.println();
                        if (content != null && !content.isBlank()) {
                            System.out.println(content);
                            System.out.println();
                        }
                    }
                }
            }
            default -> System.out.println("Unknown session command: " + args[1]);
        }
    }
}
