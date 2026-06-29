package io.opencode.core.tool.impl;

import io.opencode.core.tool.ExecuteResult;
import io.opencode.core.tool.Tool;
import io.opencode.core.tool.ToolContext;
import io.opencode.core.tool.util.ToolUtils;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.stream.Stream;

// 文件搜索工具（Glob）：根据 glob 模式匹配文件路径，最多返回 200 条结果
@Component
public class GlobTool implements Tool<Tool.Metadata> {
    private static final int MAX_RESULTS = 200;

    @Override
    public String id() { return "glob"; }

    @Override
    public String description() {
        return "Search for files matching a glob pattern. Returns up to 200 matching file paths.";
    }

    @Override
    public io.opencode.core.tool.util.JsonSchema parameters() {
        return ToolUtils.schema()
            .string("pattern", "The glob pattern to match (e.g. **/*.java, src/**/*.ts)", true)
            .string("path", "The directory to search in (defaults to workspace root)")
            .build();
    }

    @Override
    // 执行搜索：递归遍历目录，使用 PathMatcher 匹配 glob 模式，限制结果数并提示截断
    public ExecuteResult<Tool.Metadata> execute(JsonNode args, ToolContext ctx) {
        var pattern = args.get("pattern").asText();
        var root = args.has("path") ? Path.of(args.get("path").asText()) : Path.of(System.getProperty("user.dir"));

        if (!Files.isDirectory(root)) {
            return ExecuteResult.of("Error", Tool.Metadata.EMPTY, "Directory not found: " + root);
        }

        try {
            // 确保 pattern 有 glob: 前缀
            var glob = pattern.startsWith("glob:") ? pattern : "glob:" + pattern;
            var matcher = FileSystems.getDefault().getPathMatcher(glob);
            var results = new ArrayList<String>();

            // 遍历文件，相对于根目录进行匹配
            try (Stream<Path> stream = Files.walk(root)) {
                stream.filter(Files::isRegularFile)
                    .filter(p -> matcher.matches(root.relativize(p)))
                    .limit(MAX_RESULTS + 1)
                    .forEach(p -> results.add(p.toString()));
            }

            var truncated = results.size() > MAX_RESULTS;
            var display = truncated ? results.subList(0, MAX_RESULTS) : results;

            var sb = new StringBuilder();
            sb.append("Found ").append(results.size()).append(" file(s)");
            if (truncated) sb.append(" (showing first ").append(MAX_RESULTS).append(")");
            sb.append(" matching pattern '").append(pattern).append("'");
            if (!display.isEmpty()) {
                sb.append(":\n");
                display.forEach(f -> sb.append("  ").append(f).append("\n"));
            }

            return ExecuteResult.of("Glob: " + pattern, new Tool.Metadata() {}, sb.toString().strip());
        } catch (Exception e) {
            return ExecuteResult.of("Error", Tool.Metadata.EMPTY, "Failed to search: " + e.getMessage());
        }
    }
}
