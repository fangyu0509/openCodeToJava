package io.opencode.core.tool.impl;

import io.opencode.core.tool.ExecuteResult;
import io.opencode.core.tool.Tool;
import io.opencode.core.tool.ToolContext;
import io.opencode.core.tool.util.ToolUtils;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;

// 读取文件工具：读取指定路径的文件内容并返回
@Component
public class ReadTool implements Tool<Tool.Metadata> {

    @Override
    public String id() { return "read"; }

    @Override
    public String description() {
        return "Read the contents of a file at the specified path. Use this tool when you need to examine file contents.";
    }

    @Override
    public io.opencode.core.tool.util.JsonSchema parameters() {
        return ToolUtils.schema()
            .string("filePath", "The absolute path to the file to read", true)
            .build();
    }

    @Override
    // 执行读取：检查文件存在性和可读性，读取内容并附带文件大小和行数信息
    public ExecuteResult<Tool.Metadata> execute(JsonNode args, ToolContext ctx) {
        var path = args.get("filePath").asText();
        var filePath = Path.of(path);

        if (!Files.exists(filePath)) {
            return ExecuteResult.of("Error", Tool.Metadata.EMPTY, "File not found: " + path);
        }
        if (!Files.isReadable(filePath)) {
            return ExecuteResult.of("Error", Tool.Metadata.EMPTY, "Permission denied: " + path);
        }

        try {
            var content = Files.readString(filePath);
            var size = Files.size(filePath);
            var lines = content.split("\n", -1).length;
            // 对过长内容进行截断
            var output = ToolUtils.truncate(content);
            return ExecuteResult.of("Read " + filePath.getFileName(), new Tool.Metadata() {},
                "File: " + path + " (" + size + " bytes, " + lines + " lines)\n\n" + output);
        } catch (Exception e) {
            return ExecuteResult.of("Error", Tool.Metadata.EMPTY, "Failed to read " + path + ": " + e.getMessage());
        }
    }
}
