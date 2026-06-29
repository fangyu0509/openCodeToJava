package io.opencode.core.prompt;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 提示词（Prompt）加载器，从类路径下的 prompts/ 目录加载 AI 代理的提示词模板
 * 提示词文件命名约定：{name}-agent.txt
 * 使用内存缓存以避免重复读取
 */
@Component
public class PromptLoader {
    private static final String PROMPT_DIR = "prompts/"; // 提示词文件存放目录
    private final Map<String, String> cache = new ConcurrentHashMap<>(); // 提示词缓存（名称 -> 内容）

    /**
     * 根据名称加载提示词模板
     * 首次加载时会读取文件并缓存结果；文件不存在或读取失败返回空字符串
     *
     * @param name 提示词名称（对应文件名为 {name}-agent.txt）
     * @return 提示词内容，未找到时返回 ""
     */
    public String load(String name) {
        return cache.computeIfAbsent(name, n -> {
            try {
                var path = PROMPT_DIR + n + "-agent.txt";
                var resource = new ClassPathResource(path);
                if (!resource.exists()) {
                    return "";
                }
                return new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8).strip();
            } catch (IOException e) {
                return "";
            }
        });
    }
}
