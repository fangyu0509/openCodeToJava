package io.opencode.core.skill;

import io.opencode.core.config.OpenCodeConfig;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 技能服务，管理技能的发现、加载和查询
 * 在应用启动时扫描多个目录加载技能，缓存到内存中供后续使用
 */
@Service
public class SkillService {
    private static final Logger log = LoggerFactory.getLogger(SkillService.class);

    private final OpenCodeConfig config;
    private final Map<String, Skill> skills = new ConcurrentHashMap<>(); // 技能名称 -> 技能对象的缓存

    public SkillService(OpenCodeConfig config) {
        this.config = config;
    }

    /**
     * 初始化方法：加载所有可用技能
     * 扫描三个位置的 skills 目录（优先级从高到低）：
     * 1. 数据目录下的 skills 子目录
     * 2. 用户主目录下的 ~/.config/opencode/skills
     * 3. 工作空间下的 .opencode/skills
     */
    @PostConstruct
    public void loadAllSkills() {
        skills.clear();

        var seen = new java.util.HashSet<Path>();
        var dirs = new ArrayList<Path>();
        config.dataDir().ifPresent(d -> {
            var p = d.resolve("skills");
            if (seen.add(p)) dirs.add(p);
        });
        var homeSkills = Path.of(System.getProperty("user.home"), ".config", "opencode", "skills");
        if (Files.isDirectory(homeSkills) && seen.add(homeSkills)) dirs.add(homeSkills);
        var workspaceSkills = config.workspaceDir().resolve(".opencode").resolve("skills");
        if (Files.isDirectory(workspaceSkills) && seen.add(workspaceSkills)) dirs.add(workspaceSkills);

        for (var dir : dirs) {
            loadFromDirectory(dir);
        }

        log.info("Loaded {} skill(s)", skills.size());
    }

    /**
     * 从指定目录递归加载技能
     * 支持两种格式：SKILL.md 文件 和 .skill 包文件（ZIP 格式）
     * 遍历深度最多 2 层
     */
    private void loadFromDirectory(Path dir) {
        try {
            Files.walkFileTree(dir, Set.of(), 2, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    var name = file.getFileName().toString();
                    if (name.equals("SKILL.md")) {
                        SkillLoader.load(file).ifPresent(s -> skills.put(s.name(), s));
                    } else if (name.endsWith(".skill")) {
                        loadSkillPackage(file);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            log.debug("Failed to scan skills directory {}: {}", dir, e.getMessage());
        }
    }

    /**
     * 加载 .skill 包文件（ZIP 格式）
     * 遍历 ZIP 中的所有条目，找到 SKILL.md 并解析
     */
    private void loadSkillPackage(Path pkg) {
        try (var zis = new ZipInputStream(Files.newInputStream(pkg))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory() || !entry.getName().endsWith("SKILL.md")) continue;
                var content = new String(zis.readAllBytes(), StandardCharsets.UTF_8);
                SkillLoader.loadContent(content, pkg).ifPresent(s -> {
                    skills.put(s.name(), new Skill(s.name(), s.description(), pkg, s.body()));
                });
            }
        } catch (IOException e) {
            log.debug("Failed to load skill package {}: {}", pkg, e.getMessage());
        }
    }

    /** 返回所有已加载技能的不可变列表 */
    public List<Skill> listSkills() {
        return List.copyOf(skills.values());
    }

    /** 根据名称查找技能 */
    public Optional<Skill> getSkill(String name) {
        return Optional.ofNullable(skills.get(name));
    }

    /** 根据名称获取技能的正文内容 */
    public Optional<String> getSkillBody(String name) {
        return getSkill(name).map(Skill::body);
    }
}
