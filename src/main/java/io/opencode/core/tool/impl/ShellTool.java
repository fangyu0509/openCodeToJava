package io.opencode.core.tool.impl;

import io.opencode.core.tool.ExecuteResult;
import io.opencode.core.tool.Tool;
import io.opencode.core.tool.ToolContext;
import io.opencode.core.tool.util.ToolUtils;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

// Shell 命令执行工具：在工作目录中执行 shell 命令并返回输出
@Component
public class ShellTool implements Tool<Tool.Metadata> {

    private static final long DEFAULT_TIMEOUT_MS = 120_000;  // 默认超时 120 秒

    @Override
    public String id() { return "shell"; }

    @Override
    public String description() {
        return "Execute a shell command in the workspace directory. Returns stdout, stderr, and exit code.";
    }

    @Override
    public io.opencode.core.tool.util.JsonSchema parameters() {
        return ToolUtils.schema()
            .string("command", "The shell command to execute", true)
            .string("workdir", "Working directory (defaults to workspace root)")
            .number("timeout", "Timeout in milliseconds (default 120000)")
            .build();
    }

    @Override
    // 执行命令：根据操作系统选择 cmd 或 sh，合并标准错误和标准输出，带超时处理
    public ExecuteResult<Tool.Metadata> execute(JsonNode args, ToolContext ctx) {
        var command = args.get("command").asText();
        var workdir = args.has("workdir") ? args.get("workdir").asText() : System.getProperty("user.dir");
        var timeout = args.has("timeout") ? args.get("timeout").asLong() : DEFAULT_TIMEOUT_MS;

        try {
            var os = System.getProperty("os.name").toLowerCase();
            ProcessBuilder pb;
            // Windows 使用 cmd.exe，其他系统使用 sh
            if (os.contains("win")) {
                pb = new ProcessBuilder("cmd.exe", "/c", command);
            } else {
                pb = new ProcessBuilder("sh", "-c", command);
            }
            pb.directory(Path.of(workdir).toFile());
            pb.redirectErrorStream(true);  // 合并 stderr 和 stdout

            var start = System.currentTimeMillis();
            var process = pb.start();

            // 读取命令输出
            var out = new ByteArrayOutputStream();
            var inStream = process.getInputStream();
            inStream.transferTo(out);

            boolean finished = process.waitFor(timeout, TimeUnit.MILLISECONDS);
            var elapsed = System.currentTimeMillis() - start;

            // 超时处理：强制终止进程
            if (!finished) {
                process.destroyForcibly();
                return ExecuteResult.of("Command timed out", Tool.Metadata.EMPTY,
                    "Command timed out after " + timeout + "ms:\n$ " + command);
            }

            int exitCode = process.exitValue();
            var output = ToolUtils.truncate(out.toString(StandardCharsets.UTF_8));

            var sb = new StringBuilder();
            sb.append("Exit code: ").append(exitCode).append("\n");
            sb.append("Elapsed: ").append(elapsed).append("ms\n");
            sb.append("Command: $ ").append(command).append("\n\n");
            if (!output.isEmpty()) {
                sb.append(output);
            }

            return ExecuteResult.of("Shell command (" + (exitCode == 0 ? "ok" : "exit " + exitCode) + ")", new Tool.Metadata() {},
                sb.toString());
        } catch (Exception e) {
            return ExecuteResult.of("Error", Tool.Metadata.EMPTY,
                "Failed to execute command: " + e.getMessage());
        }
    }
}
