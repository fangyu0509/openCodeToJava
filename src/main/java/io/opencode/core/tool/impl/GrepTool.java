package io.opencode.core.tool.impl;

import io.opencode.core.tool.ExecuteResult;
import io.opencode.core.tool.Tool;
import io.opencode.core.tool.ToolContext;
import io.opencode.core.tool.util.ToolUtils;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.regex.Pattern;
import java.util.stream.Stream;

// 内容搜索工具（Grep）：使用正则表达式在文件中搜索匹配行
@Component
public class GrepTool implements Tool<Tool.Metadata> {
    private static final int MAX_RESULTS = 100;           // 最大返回结果行数
    private static final int MAX_LINES_PER_FILE = 50;     // 每个文件最多返回的匹配行数

    @Override
    public String id() { return "grep"; }

    @Override
    public String description() {
        return "Search file contents for lines matching a regex pattern. Returns file paths with line numbers and matching lines.";
    }

    @Override
    public io.opencode.core.tool.util.JsonSchema parameters() {
        return ToolUtils.schema()
            .string("pattern", "The regex pattern to search for", true)
            .string("include", "File glob pattern to filter (e.g. *.java, *.{ts,tsx})")
            .string("path", "The directory to search in (defaults to workspace root)")
            .build();
    }

    @Override
    // 执行搜索：编译正则表达式，遍历文件，根据 include 和二进制检测过滤，收集匹配行
    public ExecuteResult<Tool.Metadata> execute(JsonNode args, ToolContext ctx) {
        var patternStr = args.get("pattern").asText();
        var include = args.has("include") ? args.get("include").asText() : null;
        var root = args.has("path") ? Path.of(args.get("path").asText()) : Path.of(System.getProperty("user.dir"));

        if (!Files.isDirectory(root)) {
            return ExecuteResult.of("Error", Tool.Metadata.EMPTY, "Directory not found: " + root);
        }

        try {
            var pattern = Pattern.compile(patternStr);
            var results = new ArrayList<String>();
            var fileCount = 0;
            var totalMatches = 0;

            try (Stream<Path> stream = Files.walk(root)) {
                var iter = stream.filter(Files::isRegularFile).iterator();
                while (iter.hasNext()) {
                    var file = iter.next();
                    // 文件名后缀过滤
                    if (include != null && !matchesGlob(file.getFileName().toString(), include)) continue;
                    // 跳过二进制文件
                    if (isBinary(file)) continue;

                    var matches = new ArrayList<String>();
                    // 逐行读取文件并匹配正则
                    try (var lines = Files.lines(file)) {
                        var lineIter = lines.iterator();
                        int lineNum = 0;
                        while (lineIter.hasNext() && matches.size() < MAX_LINES_PER_FILE) {
                            lineNum++;
                            var line = lineIter.next();
                            if (pattern.matcher(line).find()) {
                                matches.add("  " + lineNum + ": " + line.strip());
                            }
                        }
                    }

                    if (!matches.isEmpty()) {
                        fileCount++;
                        totalMatches += matches.size();
                        results.add(root.relativize(file) + " (" + matches.size() + " matches):");
                        results.addAll(matches);
                    }

                    if (results.size() > MAX_RESULTS) break;
                }
            }

            var truncated = results.size() > MAX_RESULTS;
            if (truncated) results = new ArrayList<>(results.subList(0, MAX_RESULTS));

            var sb = new StringBuilder();
            sb.append(totalMatches).append(" matches in ").append(fileCount).append(" file(s)");
            if (truncated) sb.append(" (showing first ").append(MAX_RESULTS).append(" lines)");
            if (!results.isEmpty()) {
                sb.append(":\n");
                results.forEach(r -> sb.append(r).append("\n"));
            }

            return ExecuteResult.of("Grep: " + patternStr, new Tool.Metadata() {}, sb.toString().strip());
        } catch (Exception e) {
            return ExecuteResult.of("Error", Tool.Metadata.EMPTY, "Failed to search: " + e.getMessage());
        }
    }

    // 通过 MIME 类型检测文件是否为二进制
    private boolean isBinary(Path path) {
        try {
            var contentType = Files.probeContentType(path);
            return contentType != null && !contentType.startsWith("text");
        } catch (Exception e) {
            return false;
        }
    }

    // 使用 glob 模式匹配文件名
    private boolean matchesGlob(String name, String glob) {
        var regex = globToRegex(glob);
        return Pattern.matches(regex, name);
    }

    // 将 glob 模式转换为正则表达式
    private String globToRegex(String glob) {
        var sb = new StringBuilder("^");
        for (int i = 0; i < glob.length(); i++) {
            char c = glob.charAt(i);
            switch (c) {
                case '*' -> sb.append(".*");
                case '?' -> sb.append(".");
                case '.' -> sb.append("\\.");
                // 处理 {a,b,c} 语法
                case '{' -> {
                    int end = glob.indexOf('}', i);
                    if (end == -1) { sb.append(c); break; }
                    sb.append("(?:");
                    sb.append(glob.substring(i + 1, end).replace(",", "|"));
                    sb.append(")");
                    i = end;
                }
                default -> sb.append(c);
            }
        }
        sb.append("$");
        return sb.toString();
    }
}
