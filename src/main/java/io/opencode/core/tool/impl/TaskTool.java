package io.opencode.core.tool.impl;

import com.fasterxml.jackson.databind.JsonNode;
import io.opencode.core.agent.AgentConfig;
import io.opencode.core.agent.AgentLoop;
import io.opencode.core.agent.AgentMode;
import io.opencode.core.session.InMemorySession;
import io.opencode.core.tool.ExecuteResult;
import io.opencode.core.tool.Tool;
import io.opencode.core.tool.ToolContext;
import io.opencode.core.tool.util.ToolUtils;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

// 子代理任务工具：将任务委派给子代理，创建子会话并等待返回结果
@Component
public class TaskTool implements Tool<Tool.Metadata> {
    private static final long TIMEOUT_SECONDS = 300;  // 子代理超时时间 5 分钟

    private final AgentLoop agentLoop;

    // @Lazy 避免循环依赖
    public TaskTool(@Lazy AgentLoop agentLoop) {
        this.agentLoop = agentLoop;
    }

    @Override
    public String id() { return "task"; }

    @Override
    public String description() {
        return "Delegate a task to a sub-agent. Creates a child session and returns the sub-agent's response.";
    }

    @Override
    public io.opencode.core.tool.util.JsonSchema parameters() {
        return ToolUtils.schema()
            .string("agent", "The sub-agent type (general, explore, architect)", true)
            .string("prompt", "The task description to delegate", true)
            .string("model", "Optional model override (e.g. openai:gpt-4o)")
            .build();
    }

    @Override
    // 执行委派：根据参数配置子代理，创建内存会话，通过 AgentLoop 处理并等待结果
    public ExecuteResult<Tool.Metadata> execute(JsonNode args, ToolContext ctx) {
        var agentName = args.get("agent").asText();
        var prompt = args.get("prompt").asText();

        try {
            var builder = AgentConfig.builder(agentName).mode(AgentMode.SUBAGENT);
            // 可选：覆盖模型配置
            if (args.has("model")) {
                var parts = args.get("model").asText().split(":", 2);
                var providerId = parts[0];
                var modelId = parts.length > 1 ? parts[1] : "default";
                builder.model(io.opencode.core.provider.ModelRef.of(providerId, modelId));
            }
            var subConfig = builder.build();
            var subSession = new InMemorySession(subConfig);

            var response = agentLoop.process(subSession, prompt, subConfig).get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            var text = response.text().orElse("");

            return ExecuteResult.of("Sub-agent: " + agentName, new Tool.Metadata() {},
                "Sub-agent '" + agentName + "' result:\n\n" + text);
        } catch (Exception e) {
            return ExecuteResult.of("Error", Tool.Metadata.EMPTY,
                "Sub-agent task failed: " + e.getMessage());
        }
    }
}
