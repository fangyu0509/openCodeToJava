package io.opencode.core.config;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

// 引用配置记录，表示一个可从工作空间引用外部代码/文档的源
public record ReferenceConfig(
    String name,              // 引用名称，用于在提示中引用
    String description,       // 人类可读的描述
    Optional<Path> path,      // 本地路径
    Optional<String> gitRepo, // Git 仓库 URL
    List<String> patterns     // 包含的文件 glob 模式列表
) {
    // 紧凑构造器：确保 patterns 不为 null，防御性复制为不可变列表
    public ReferenceConfig {
        patterns = patterns != null ? List.copyOf(patterns) : List.of();
    }
}
