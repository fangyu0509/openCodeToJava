package io.opencode.core.tool.util;

import io.opencode.core.config.ReferenceConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

// 引用解析服务：管理 @引用 的配置，解析文件路径和 Git 仓库引用
@Service
public class ReferenceService {
    private static final Logger log = LoggerFactory.getLogger(ReferenceService.class);

    // 引用名称到配置的映射（线程安全）
    private final Map<String, ReferenceConfig> references = new ConcurrentHashMap<>();

    // 配置所有引用
    public void configure(Map<String, ReferenceConfig> refs) {
        references.clear();
        if (refs != null) references.putAll(refs);
    }

    // 根据名称获取引用配置
    public Optional<ReferenceConfig> getRef(String name) {
        return Optional.ofNullable(references.get(name));
    }

    // 返回所有引用的不可修改视图
    public Map<String, ReferenceConfig> allRefs() {
        return Map.copyOf(references);
    }

    // 解析指定引用：读取文件内容或列出目录文件，内容超过 2000 字符则截断
    public String resolveReference(String name, Path workspaceDir) {
        var ref = references.get(name);
        if (ref == null) return null;
        var sb = new StringBuilder();
        sb.append("=== @").append(name).append(" ===\n");
        if (ref.description() != null && !ref.description().isBlank()) {
            sb.append("Description: ").append(ref.description()).append("\n");
        }
        var resolvedPaths = resolvePaths(ref, workspaceDir);
        for (var p : resolvedPaths) {
            if (Files.isRegularFile(p)) {
                try {
                    sb.append("File: ").append(p).append("\n");
                    var content = Files.readString(p);
                    if (content.length() > 2000) {
                        sb.append(content, 0, 2000).append("\n... [truncated]\n");
                    } else {
                        sb.append(content).append("\n");
                    }
                } catch (IOException e) {
                    sb.append("(unreadable: ").append(e.getMessage()).append(")\n");
                }
            } else if (Files.isDirectory(p)) {
                sb.append("Directory: ").append(p).append("\n");
                try (var files = Files.walk(p, 3)) {
                    var fileList = files
                        .filter(f -> Files.isRegularFile(f))
                        .limit(100)
                        .map(f -> p.relativize(f).toString())
                        .collect(Collectors.joining("\n  "));
                    sb.append("  Files:\n  ").append(fileList).append("\n");
                } catch (IOException e) {
                    sb.append("  (unreadable: ").append(e.getMessage()).append(")\n");
                }
            }
        }
        return sb.toString();
    }

    // 解析所有引用并拼接结果
    public String resolveAll(Path workspaceDir) {
        var sb = new StringBuilder();
        for (var entry : references.entrySet()) {
            var resolved = resolveReference(entry.getKey(), workspaceDir);
            if (resolved != null) {
                sb.append(resolved).append("\n");
            }
        }
        return sb.toString();
    }

    // 解析引用的路径（支持本地路径和 Git 仓库路径，可配合文件模式过滤）
    private List<Path> resolvePaths(ReferenceConfig ref, Path workspaceDir) {
        var paths = new ArrayList<Path>();
        if (ref.path().isPresent()) {
            var p = ref.path().get();
            if (!p.isAbsolute()) p = workspaceDir.resolve(p);
            if (ref.patterns().isEmpty()) {
                paths.add(p);
            } else {
                // 根据文件模式过滤
                try (var stream = Files.walk(p, 10)) {
                    stream.filter(Files::isRegularFile)
                        .filter(f -> matchesAny(f, ref.patterns()))
                        .limit(200)
                        .forEach(paths::add);
                } catch (IOException e) {
                    log.debug("Failed to walk ref path {}: {}", p, e.getMessage());
                }
            }
        }
        if (ref.gitRepo().isPresent()) {
            var repoUrl = ref.gitRepo().get();
            var cloneDir = workspaceDir.resolve(".opencode").resolve("refs").resolve(ref.name());
            if (Files.isDirectory(cloneDir)) {
                if (ref.patterns().isEmpty()) {
                    paths.add(cloneDir);
                } else {
                    try (var stream = Files.walk(cloneDir, 10)) {
                        stream.filter(Files::isRegularFile)
                            .filter(f -> matchesAny(f, ref.patterns()))
                            .limit(200)
                            .forEach(paths::add);
                    } catch (IOException e) {
                        log.debug("Failed to walk git ref {}: {}", cloneDir, e.getMessage());
                    }
                }
            } else {
                // Git 仓库尚未克隆，提示用户手动操作
                log.info("Git repo '{}' not yet cloned. Run 'git clone {} {}' manually.",
                    ref.name(), repoUrl, cloneDir);
            }
        }
        return paths;
    }

    // 检查文件是否匹配任意一个模式（支持通配符 *）
    private static boolean matchesAny(Path file, List<String> patterns) {
        var name = file.getFileName().toString();
        for (var p : patterns) {
            if (p.contains("*")) {
                var regex = "\\Q" + p.replace("*", "\\E.*\\Q") + "\\E";
                if (name.matches(regex)) return true;
            } else if (name.equals(p)) {
                return true;
            } else if (name.endsWith(p)) {
                return true;
            }
        }
        return false;
    }
}
