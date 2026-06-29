package io.opencode.core.tool.impl;

import io.opencode.core.session.SnapshotManager;
import io.opencode.core.tool.ExecuteResult;
import io.opencode.core.tool.Tool;
import io.opencode.core.tool.ToolContext;
import io.opencode.core.tool.util.ToolUtils;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

// 编辑文件工具：在文件中精确查找并替换文本，要求 oldString 必须唯一匹配
@Component
public class EditTool implements Tool<Tool.Metadata> {
    private final SnapshotManager snapshotManager;  // 快照管理器，用于编辑前备份

    public EditTool(SnapshotManager snapshotManager) {
        this.snapshotManager = snapshotManager;
    }

    @Override
    public String id() { return "edit"; }

    @Override
    public String description() {
        return "Edit a file by finding and replacing exact text. The oldString must match exactly once. Use this for targeted edits.";
    }

    @Override
    public io.opencode.core.tool.util.JsonSchema parameters() {
        return ToolUtils.schema()
            .string("filePath", "The absolute path to the file to edit", true)
            .string("oldString", "The exact text to search for (must appear exactly once)", true)
            .string("newString", "The replacement text", true)
            .build();
    }

    @Override
    // 执行编辑：先备份快照，检查文件存在性，精确匹配 oldString（必须唯一），然后执行替换
    public ExecuteResult<Tool.Metadata> execute(JsonNode args, ToolContext ctx) {
        var path = args.get("filePath").asText();
        var oldString = args.get("oldString").asText();
        var newString = args.get("newString").asText();
        var filePath = Path.of(path);

        if (!Files.exists(filePath)) {
            return ExecuteResult.of("Error", Tool.Metadata.EMPTY, "File not found: " + path);
        }

        try {
            snapshotManager.snapshotBefore(ctx.sessionId().value(), filePath, id());
            var content = Files.readString(filePath);

            // 检查 oldString 在文件中出现的位置和次数（必须恰好出现一次）
            int index = content.indexOf(oldString);
            if (index == -1) {
                return ExecuteResult.of("Error", Tool.Metadata.EMPTY,
                    "Could not find oldString in file. It may have already been edited, or the text doesn't match exactly.");
            }
            int lastIndex = content.lastIndexOf(oldString);
            if (index != lastIndex) {
                return ExecuteResult.of("Error", Tool.Metadata.EMPTY,
                    "Found " + countOccurrences(content, oldString) + " matches of oldString. Please provide more surrounding context to make the match unique.");
            }

            // 执行替换并写回文件
            var newContent = content.substring(0, index) + newString + content.substring(index + oldString.length());
            Files.writeString(filePath, newContent, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

            return ExecuteResult.of("Edited " + filePath.getFileName(), new Tool.Metadata() {},
                "Successfully edited " + path + "\n\n```diff\n- " + oldString + "\n+ " + newString + "\n```");
        } catch (Exception e) {
            return ExecuteResult.of("Error", Tool.Metadata.EMPTY, "Failed to edit " + path + ": " + e.getMessage());
        }
    }

    // 统计子串在字符串中出现的次数
    private int countOccurrences(String haystack, String needle) {
        int count = 0, idx = 0;
        while ((idx = haystack.indexOf(needle, idx)) != -1) {
            count++;
            idx += needle.length();
        }
        return count;
    }
}
