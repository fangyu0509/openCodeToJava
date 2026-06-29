package io.opencode.core.tool.util;

import io.opencode.core.model.ProjectInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

// 项目分析器：扫描工作区目录，识别编程语言、构建系统、框架和测试框架
@Service
public class ProjectAnalyzer {
    private static final Logger log = LoggerFactory.getLogger(ProjectAnalyzer.class);

    // 构建/配置文件列表
    private static final Set<String> BUILD_FILES = Set.of(
        "pom.xml", "build.gradle", "build.gradle.kts", "build.sbt",
        "Cargo.toml", "Package.swift", "project.clj",
        "makefile", "Makefile", "CMakeLists.txt",
        "meson.build", "BUILD", "WORKSPACE",
        "package.json", "yarn.lock", "pnpm-lock.yaml", "go.mod"
    );

    // 文件扩展名到编程语言的映射
    private static final Map<String, String> LANG_EXTENSIONS = new HashMap<>() {{
        put("java", "Java"); put("kt", "Kotlin"); put("kts", "Kotlin");
        put("scala", "Scala"); put("clj", "Clojure");
        put("ts", "TypeScript"); put("tsx", "TypeScript React"); put("js", "JavaScript"); put("jsx", "JavaScript React"); put("mjs", "JavaScript");
        put("py", "Python"); put("rb", "Ruby"); put("go", "Go"); put("rs", "Rust");
        put("c", "C"); put("h", "C"); put("cpp", "C++"); put("hpp", "C++"); put("cc", "C++");
        put("cs", "C#"); put("fs", "F#"); put("swift", "Swift");
        put("php", "PHP"); put("r", "R"); put("m", "Objective-C");
        put("sh", "Shell"); put("bash", "Shell"); put("zsh", "Shell");
        put("yaml", "YAML"); put("yml", "YAML"); put("json", "JSON");
        put("xml", "XML"); put("toml", "TOML"); put("sql", "SQL");
        put("md", "Markdown"); put("html", "HTML"); put("css", "CSS"); put("scss", "SCSS");
    }};

    // 配置文件到框架名称的映射
    private static final Map<String, String> FRAMEWORK_DETECTORS = new HashMap<>() {{
        put("package.json", "Node.js");
        put("tsconfig.json", "TypeScript");
        put("pom.xml", "Maven");
        put("build.gradle", "Gradle");
        put("build.gradle.kts", "Gradle Kotlin DSL");
        put("Cargo.toml", "Cargo");
        put("go.mod", "Go Modules");
        put("requirements.txt", "pip");
        put("pyproject.toml", "Python Poetry");
        put("Gemfile", "Ruby Bundler");
        put("project.clj", "Leiningen");
        put("composer.json", "Composer");
    }};

    // 路径模式到测试框架的映射
    private static final Map<String, String> TEST_FILE_PATTERNS = new HashMap<>() {{
        put("src/test", "JUnit");
        put("test/", "pytest");
        put("__tests__", "Jest");
        put("spec/", "RSpec");
        put("_test.go", "Go test");
        put(".test.ts", "Jest");
        put(".test.js", "Jest");
        put("_test.rs", "cargo test");
        put("Tests/", "XCTest");
    }};

    // 分析项目目录：遍历文件树，收集语言、构建系统、框架、测试框架和统计信息
    public ProjectInfo analyze(Path rootPath) {
        if (!Files.isDirectory(rootPath)) {
            return ProjectInfo.empty(rootPath);
        }

        var languages = new HashSet<String>();
        var buildSystems = new HashSet<String>();
        var frameworks = new HashSet<String>();
        var testFrameworks = new HashSet<String>();
        var configFiles = new HashMap<String, String>();
        int fileCount = 0;
        int dirCount = 0;

        try (var stream = Files.walk(rootPath, 20)) {
            var iterator = stream.iterator();
            while (iterator.hasNext()) {
                var path = iterator.next();
                if (Files.isDirectory(path)) {
                    dirCount++;
                    var name = path.getFileName().toString();
                    // Skip common non-project directories
                    if (name.equals("node_modules") || name.equals(".git") ||
                        name.equals("target") || name.equals("build") ||
                        name.equals(".gradle") || name.equals("__pycache__") ||
                        name.equals(".opencode") || name.equals("dist") ||
                        name.equals(".idea")) {
                        continue;
                    }
                } else {
                    fileCount++;
                    var fileName = path.getFileName().toString().toLowerCase();

                    // Detect config/build files
                    if (BUILD_FILES.contains(fileName)) {
                        configFiles.put(path.getFileName().toString(), readFirstLine(path));
                    }

                    // Detect language from extension
                    var ext = extension(fileName);
                    if (ext != null && LANG_EXTENSIONS.containsKey(ext)) {
                        languages.add(LANG_EXTENSIONS.get(ext));
                    }

                    // Detect frameworks
                    if (FRAMEWORK_DETECTORS.containsKey(fileName)) {
                        frameworks.add(FRAMEWORK_DETECTORS.get(fileName));
                    }

                    // Detect build systems
                    if (BUILD_FILES.contains(fileName)) {
                        if (fileName.equals("pom.xml") || fileName.startsWith("build.gradle")) {
                            buildSystems.add("Maven/Gradle");
                        } else if (fileName.equals("Cargo.toml")) {
                            buildSystems.add("Cargo");
                        } else if (fileName.equals("go.mod")) {
                            buildSystems.add("Go Modules");
                        } else if (fileName.equals("package.json")) {
                            buildSystems.add("npm/yarn/pnpm");
                        } else if (fileName.equals("Makefile") || fileName.equals("makefile")) {
                            buildSystems.add("Make");
                        } else if (fileName.equals("CMakeLists.txt")) {
                            buildSystems.add("CMake");
                        }
                    }

                    // Detect test frameworks from path patterns
                    var relative = rootPath.relativize(path).toString().toLowerCase();
                    for (var entry : TEST_FILE_PATTERNS.entrySet()) {
                        if (relative.contains(entry.getKey())) {
                            testFrameworks.add(entry.getValue());
                        }
                    }
                }
            }
        } catch (IOException e) {
            log.warn("Failed to analyze project at {}: {}", rootPath, e.getMessage());
            return ProjectInfo.empty(rootPath);
        }

        return new ProjectInfo(
            rootPath,
            rootPath.getFileName().toString(),
            sortedList(languages),
            sortedList(buildSystems),
            sortedList(frameworks),
            sortedList(testFrameworks),
            configFiles,
            fileCount,
            dirCount,
            buildDescription(languages, buildSystems, frameworks)
        );
    }

    // 根据分析结果生成 AGENTS.md 文件内容
    public String generateAgentsMd(ProjectInfo info) {
        var sb = new StringBuilder();
        sb.append("# ").append(info.projectName()).append("\n\n");

        if (!info.languages().isEmpty()) {
            sb.append("## Languages\n\n");
            for (var lang : info.languages()) {
                sb.append("- ").append(lang).append("\n");
            }
            sb.append("\n");
        }

        if (!info.buildSystems().isEmpty()) {
            sb.append("## Build System\n\n");
            for (var bs : info.buildSystems()) {
                sb.append("- ").append(bs).append("\n");
            }
            sb.append("\n");
        }

        if (!info.frameworks().isEmpty()) {
            sb.append("## Frameworks\n\n");
            for (var fw : info.frameworks()) {
                sb.append("- ").append(fw).append("\n");
            }
            sb.append("\n");
        }

        if (!info.testFrameworks().isEmpty()) {
            sb.append("## Test Framework\n\n");
            for (var tf : info.testFrameworks()) {
                sb.append("- ").append(tf).append("\n");
            }
            sb.append("\n");
        }

        sb.append("## Project Stats\n\n");
        sb.append("- Files: ").append(info.fileCount()).append("\n");
        sb.append("- Directories: ").append(info.directoryCount()).append("\n");
        sb.append("\n");

        sb.append("## Description\n\n");
        sb.append(info.projectDescription()).append("\n\n");

        sb.append("## Guidelines\n\n");
        sb.append("- Follow existing code style and conventions\n");
        sb.append("- Write tests for new features\n");
        sb.append("- Use the project's build system for compilation and testing\n");
        sb.append("- Keep changes focused and minimal\n");
        sb.append("- Run existing tests before committing\n");

        return sb.toString();
    }

    // 构建项目描述字符串
    private String buildDescription(Set<String> languages, Set<String> buildSystems, Set<String> frameworks) {
        var parts = new ArrayList<String>();
        if (!languages.isEmpty()) {
            parts.add(String.join(", ", languages));
        }
        if (!buildSystems.isEmpty()) {
            parts.add("built with " + String.join(", ", buildSystems));
        }
        if (!frameworks.isEmpty()) {
            parts.add("using " + String.join(", ", frameworks));
        }
        return parts.isEmpty() ? "A software project." : String.join(" ", parts) + ".";
    }

    // 获取文件扩展名
    private String extension(String fileName) {
        var dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) return null;
        return fileName.substring(dot + 1);
    }

    // 读取文件第一行内容
    private String readFirstLine(Path path) {
        try (var lines = Files.lines(path)) {
            return lines.findFirst().orElse("").trim();
        } catch (IOException e) {
            return "";
        }
    }

    // 对集合进行排序并转为不可变列表
    private <T extends Comparable<? super T>> List<T> sortedList(Set<T> set) {
        return set.stream().sorted().toList();
    }
}
