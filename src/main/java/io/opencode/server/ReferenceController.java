package io.opencode.server;

import io.opencode.core.tool.util.ReferenceService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Path;
import java.util.Map;

// 引用（References）管理 REST 控制器，提供列出、解析单个和全部引用功能
@RestController
@RequestMapping("/api/references")
public class ReferenceController {
    private final ReferenceService referenceService;

    public ReferenceController(ReferenceService referenceService) {
        this.referenceService = referenceService;
    }

    // 列出所有已注册的引用
    @GetMapping
    public Map<String, Object> listReferences() {
        var refs = referenceService.allRefs();
        return Map.of("references", refs);
    }

    // 解析指定名称的引用内容（如读取文件或克隆 Git 仓库）
    @GetMapping(value = "/{name}/resolve", produces = MediaType.TEXT_PLAIN_VALUE)
    public String resolveReference(@PathVariable String name) {
        var resolved = referenceService.resolveReference(name, Path.of(System.getProperty("user.dir")));
        if (resolved == null) return "Reference '" + name + "' not found";
        return resolved;
    }

    // 解析所有引用并将结果合并为一段文本
    @GetMapping(value = "/resolve-all", produces = MediaType.TEXT_PLAIN_VALUE)
    public String resolveAll() {
        return referenceService.resolveAll(Path.of(System.getProperty("user.dir")));
    }
}
