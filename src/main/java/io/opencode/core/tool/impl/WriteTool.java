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

// 写文件工具：写入内容到指定路径，支持创建和覆盖
@Component
public class WriteTool implements Tool<Tool.Metadata> {
    private final SnapshotManager snapshotManager;  // 快照管理器，用于写入前备份

    public WriteTool(SnapshotManager snapshotManager) {
        this.snapshotManager = snapshotManager;
    }

    @Override
    public String id() { return "write"; }

    @Override
    public String description() {
        return "Write content to a file at the specified path. Creates the file if it doesn't exist, overwrites if it does.";
    }

    @Override
    public io.opencode.core.tool.util.JsonSchema parameters() {
        return ToolUtils.schema()
            .string("filePath", "The absolute path to the file to write", true)
            .string("content", "The content to write to the file", true)
            .build();
    }

    @Override
    // 执行写入：先备份快照，创建父目录（如需要），再写入文件
    public ExecuteResult<Tool.Metadata> execute(JsonNode args, ToolContext ctx) {
        var path = args.get("filePath").asText();
        var content = args.get("content").asText();
        var filePath = Path.of(path);

        try {
            snapshotManager.snapshotBefore(ctx.sessionId().value(), filePath, id());
            var parent = filePath.getParent();
            if (parent != null && !Files.exists(parent)) {
                Files.createDirectories(parent);
            }
            Files.writeString(filePath, content, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            var size = Files.size(filePath);
            var lines = content.split("\n", -1).length;
            return ExecuteResult.of("Wrote " + filePath.getFileName(), new Tool.Metadata() {},
                "Successfully wrote " + size + " bytes (" + lines + " lines) to " + path);
        } catch (Exception e) {
            return ExecuteResult.of("Error", Tool.Metadata.EMPTY, "Failed to write " + path + ": " + e.getMessage());
        }
    }
}
