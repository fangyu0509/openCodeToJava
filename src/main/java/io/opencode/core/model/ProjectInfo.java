package io.opencode.core.model;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * 项目信息记录，描述一个代码项目的基本元数据
 */
public record ProjectInfo(
    Path rootPath,                // 项目根目录路径
    String projectName,           // 项目名称
    List<String> languages,       // 使用的编程语言列表
    List<String> buildSystems,    // 使用的构建系统列表（如 Maven、Gradle）
    List<String> frameworks,      // 使用的框架列表
    List<String> testFrameworks,  // 使用的测试框架列表
    Map<String, String> configFiles, // 关键配置文件映射（文件名 -> 内容摘要）
    int fileCount,                // 项目文件总数
    int directoryCount,           // 项目目录总数
    String projectDescription     // 项目描述
) {
    /**
     * 创建一个空的项目信息对象，只包含根路径和从根路径提取的项目名称
     */
    public static ProjectInfo empty(Path rootPath) {
        return new ProjectInfo(rootPath, rootPath.getFileName().toString(),
            List.of(), List.of(), List.of(), List.of(), Map.of(), 0, 0, "");
    }
}
