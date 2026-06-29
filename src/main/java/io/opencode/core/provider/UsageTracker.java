package io.opencode.core.provider;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * UsageTracker —— Token 用量追踪器（Spring 组件）
 * 按模型维度累计 Token 消耗，支持查询总用量和分模型用量
 */
@Component
public class UsageTracker {
    // 按模型 ID 分别累计用量，ConcurrentHashMap 保证线程安全
    private final Map<String, ModelUsage> usageByModel = new ConcurrentHashMap<>();

    /** 记录一次调用的用量，合并到对应模型的累计值中 */
    public void track(String modelId, ChatResponse.Usage usage) {
        usageByModel.merge(modelId, new ModelUsage(usage), ModelUsage::add);
    }

    /** 获取所有模型的 Token 用量总和 */
    public ModelUsage getTotal() {
        return usageByModel.values().stream().reduce(new ModelUsage(0, 0, 0), ModelUsage::add);
    }

    /** 返回按模型区分的用量 Map 的不可修改副本 */
    public Map<String, ModelUsage> byModel() {
        return Map.copyOf(usageByModel);
    }

    /** 重置所有用量统计 */
    public void reset() {
        usageByModel.clear();
    }

    /**
     * ModelUsage —— 单个模型的 Token 用量记录
     * 记录提示词 Token、补全 Token 和总 Token 的累计值
     */
    public record ModelUsage(long promptTokens, long completionTokens, long totalTokens) {
        /** 从 ChatResponse.Usage 转换构造 */
        public ModelUsage(ChatResponse.Usage u) {
            this(u.promptTokens(), u.completionTokens(), u.totalTokens());
        }

        /** 将两个用量记录累加，用于合并操作 */
        public ModelUsage add(ModelUsage other) {
            return new ModelUsage(
                promptTokens + other.promptTokens,
                completionTokens + other.completionTokens,
                totalTokens + other.totalTokens
            );
        }
    }
}
