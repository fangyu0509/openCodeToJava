package io.opencode.core.tool.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

// 文件搜索服务：根据查询字符串对工作区文件进行模糊搜索和评分
@Service
public class FileSearchService {
    private static final Logger log = LoggerFactory.getLogger(FileSearchService.class);

    // 需要忽略的目录
    private static final Set<String> IGNORE_DIRS = Set.of(
        "node_modules", ".git", "target", "build", ".gradle",
        "__pycache__", ".opencode", "dist", ".idea", ".vscode",
        ".mvn", ".sass-cache", ".next", ".nuxt"
    );

    // 需要忽略的文件扩展名（二进制、压缩包、图片等）
    private static final Set<String> IGNORE_EXTENSIONS = Set.of(
        ".class", ".jar", ".war", ".zip", ".tar", ".gz",
        ".png", ".jpg", ".jpeg", ".gif", ".ico", ".svg",
        ".woff", ".woff2", ".ttf", ".eot",
        ".o", ".so", ".dylib", ".dll", ".exe",
        ".min.js", ".min.css", ".map"
    );

    // 文件搜索结果条目：包含路径、文件名、所在目录和匹配分数
    public record FileEntry(String path, String name, String directory, int score) {}

    // 搜索文件：返回按匹配分降序排列的最多 maxResults 条结果
    public List<FileEntry> search(String query, int maxResults) {
        var root = Path.of(System.getProperty("user.dir"));
        var results = new ArrayList<FileEntry>();

        if (!Files.isDirectory(root)) return results;

        try (var stream = Files.walk(root, 50)) {
            stream.filter(Files::isRegularFile)
                .filter(p -> !isIgnored(p))
                .forEach(p -> {
                    var relPath = root.relativize(p).toString();
                    var fileName = p.getFileName().toString();
                    var score = scoreMatch(query, relPath, fileName);
                    if (score > 0) {
                        var dir = p.getParent() != null ? root.relativize(p.getParent()).toString() : "";
                        results.add(new FileEntry(relPath, fileName, dir, score));
                    }
                });
        } catch (IOException e) {
            log.warn("Failed to search files: {}", e.getMessage());
        }

        results.sort(Comparator.<FileEntry, Integer>comparing(e -> e.score).reversed()
            .thenComparing(e -> e.path));
        return results.subList(0, Math.min(results.size(), maxResults));
    }

    // 检查路径是否应被忽略（目录黑名单 + 扩展名黑名单）
    private boolean isIgnored(Path path) {
        for (var i = 0; i < path.getNameCount(); i++) {
            if (IGNORE_DIRS.contains(path.getName(i).toString())) return true;
        }
        var name = path.getFileName().toString().toLowerCase();
        for (var ext : IGNORE_EXTENSIONS) {
            if (name.endsWith(ext)) return true;
        }
        return false;
    }

    // 多级评分算法：精确匹配 > 前缀匹配 > 包含匹配 > 路径匹配 > 模糊匹配
    private int scoreMatch(String query, String relPath, String fileName) {
        if (query == null || query.isEmpty()) return 0;
        var q = query.toLowerCase();
        var rp = relPath.toLowerCase();
        var fn = fileName.toLowerCase();

        // Exact file name match → highest score
        if (fn.equals(q)) return 1000;
        // File name starts with query
        if (fn.startsWith(q)) return 900;
        // File name contains query
        if (fn.contains(q)) return 700;
        // Path contains query as word boundary
        if (rp.contains("/" + q) || rp.contains("\\" + q)) return 500;
        // Path contains query
        if (rp.contains(q)) return 300;
        // Fuzzy match: all characters in query appear in order in file name
        if (fuzzyMatch(fn, q)) return 100;
        if (fuzzyMatch(rp, q)) return 50;

        return 0;
    }

    // 模糊匹配：检查 query 中的每个字符是否按顺序出现在 text 中
    private boolean fuzzyMatch(String text, String query) {
        int qi = 0;
        for (var i = 0; i < text.length() && qi < query.length(); i++) {
            if (text.charAt(i) == query.charAt(qi)) {
                qi++;
            }
        }
        return qi == query.length();
    }
}
