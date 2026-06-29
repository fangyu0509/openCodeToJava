package io.opencode.core.formatter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * 代码格式化服务，根据文件扩展名自动选择合适的格式化工具并执行格式化
 */
@Service
public class FormatterService {
    private static final Logger log = LoggerFactory.getLogger(FormatterService.class);

    // 文件扩展名与对应格式化命令的映射表，支持 Java、Python、Rust、Go、TypeScript 等多种语言
    private final List<FormatterDef> formatters = List.of(
        new FormatterDef(".java", List.of("google-java-format", "--replace")),
        new FormatterDef(".py", List.of("ruff", "check", "--fix", "--silent")),
        new FormatterDef(".rs", List.of("rustfmt")),
        new FormatterDef(".go", List.of("gofmt", "-w")),
        new FormatterDef(".ts", List.of("prettier", "--write")),
        new FormatterDef(".tsx", List.of("prettier", "--write")),
        new FormatterDef(".js", List.of("prettier", "--write")),
        new FormatterDef(".jsx", List.of("prettier", "--write")),
        new FormatterDef(".css", List.of("prettier", "--write")),
        new FormatterDef(".scss", List.of("prettier", "--write")),
        new FormatterDef(".json", List.of("prettier", "--write")),
        new FormatterDef(".yaml", List.of("prettier", "--write")),
        new FormatterDef(".yml", List.of("prettier", "--write")),
        new FormatterDef(".md", List.of("prettier", "--write")),
        new FormatterDef(".kt", List.of("ktlint", "-F")),
        new FormatterDef(".kts", List.of("ktlint", "-F")),
        new FormatterDef(".rb", List.of("rubocop", "-a", "--silent")),
        new FormatterDef(".php", List.of("pint")),
        new FormatterDef(".c", List.of("clang-format", "-i")),
        new FormatterDef(".cpp", List.of("clang-format", "-i")),
        new FormatterDef(".h", List.of("clang-format", "-i")),
        new FormatterDef(".hpp", List.of("clang-format", "-i")),
        new FormatterDef(".dart", List.of("dart", "format")),
        new FormatterDef(".sh", List.of("shfmt", "-w"))
    );

    /**
     * 对指定文件执行格式化
     * 根据文件扩展名查找对应的格式化命令，通过 ProcessBuilder 启动外部进程执行
     * 格式化工具不存在或执行失败时会静默处理，不中断流程
     */
    public void format(Path filePath) {
        var ext = extension(filePath);
        var def = find(ext);
        if (def == null) return;
        try {
            var cmd = new java.util.ArrayList<>(def.command);
            cmd.add(filePath.toString());
            var pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            var process = pb.start();
            process.waitFor(10, TimeUnit.SECONDS);
            log.debug("Formatted {} with {}", filePath, def.command.get(0));
        } catch (IOException e) {
            log.debug("Formatter {} not available for {}: {}", def.command.get(0), filePath, e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** 根据扩展名查找对应的格式化定义，未找到返回 null */
    private FormatterDef find(String ext) {
        for (var f : formatters) {
            if (f.extension.equals(ext)) return f;
        }
        return null;
    }

    /**
     * 根据文件路径推断其编程语言
     * 返回 Optional，对于不支持的文件扩展名返回 Optional.empty()
     */
    public Optional<String> languageFor(Path filePath) {
        var ext = extension(filePath);
        return switch (ext) {
            case ".java" -> Optional.of("java");
            case ".py" -> Optional.of("python");
            case ".rs" -> Optional.of("rust");
            case ".go" -> Optional.of("go");
            case ".ts", ".tsx" -> Optional.of("typescript");
            case ".js", ".jsx" -> Optional.of("javascript");
            case ".kt", ".kts" -> Optional.of("kotlin");
            case ".rb" -> Optional.of("ruby");
            case ".php" -> Optional.of("php");
            case ".c", ".cpp", ".h", ".hpp" -> Optional.of("cpp");
            case ".cs" -> Optional.of("csharp");
            case ".swift" -> Optional.of("swift");
            case ".scala" -> Optional.of("scala");
            default -> Optional.empty();
        };
    }

    /** 从文件路径中提取小写扩展名（如 ".java"），无扩展名返回空字符串 */
    private static String extension(Path p) {
        var name = p.getFileName().toString();
        var idx = name.lastIndexOf('.');
        return idx >= 0 ? name.substring(idx).toLowerCase() : "";
    }

    /** 格式化定义内部记录，保存文件扩展名与对应命令 */
    private record FormatterDef(String extension, List<String> command) {}
}
