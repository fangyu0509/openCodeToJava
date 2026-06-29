package io.opencode.core.tool.impl;

import io.opencode.core.tool.ExecuteResult;
import io.opencode.core.tool.Tool;
import io.opencode.core.tool.ToolContext;
import io.opencode.core.tool.util.ToolUtils;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

// 提问工具：通过事件总线向用户发送问题并等待回答
@Component
public class QuestionTool implements Tool<Tool.Metadata> {
    private static final Logger log = LoggerFactory.getLogger(QuestionTool.class);

    @Override
    public String id() { return "question"; }

    @Override
    public String description() {
        return "Ask the user a question and deliver the answer via the event bus. Use this when you need clarification or confirmation.";
    }

    @Override
    public io.opencode.core.tool.util.JsonSchema parameters() {
        return ToolUtils.schema()
            .string("question", "The question to ask the user", true)
            .string("options", "Comma-separated list of options (e.g. yes,no,cancel)")
            .build();
    }

    @Override
    // 执行提问：构造提示文本（含选项），通过上下文中的 ask 回调发送给用户
    public ExecuteResult<Tool.Metadata> execute(JsonNode args, ToolContext ctx) {
        var question = args.get("question").asText();
        var options = args.has("options") ? args.get("options").asText() : null;

        var prompt = new StringBuilder(question);
        if (options != null && !options.isBlank()) {
            prompt.append(" (").append(options).append(")");
        }

        try {
            if (ctx.ask() != null) {
                ctx.ask().accept(prompt.toString());
            }
            log.info("Question asked: {}", prompt);
            return ExecuteResult.of("Question asked", Tool.Metadata.EMPTY,
                "Question sent to user: " + prompt + "\nWaiting for answer via event bus...");
        } catch (Exception e) {
            log.error("Failed to ask question", e);
            return ExecuteResult.of("Error", Tool.Metadata.EMPTY, "Failed to ask question: " + e.getMessage());
        }
    }
}
