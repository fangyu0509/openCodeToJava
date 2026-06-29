package io.opencode.core.tool;

import io.opencode.core.session.FilePart;

import java.util.Collections;
import java.util.List;

// 工具执行结果：包含标题、元数据、输出文本和附件列表
public record ExecuteResult<M extends Tool.Metadata>(
    String title,            // 结果标题，用于展示
    M metadata,              // 工具特定的元数据
    String output,           // 输出文本内容
    List<FilePart> attachments  // 附件文件列表
) {
    // 紧凑构造器：确保附件列表不为 null，且不可修改
    public ExecuteResult {
        attachments = attachments != null
            ? Collections.unmodifiableList(attachments)
            : Collections.emptyList();
    }

    // 创建无附件的执行结果
    public static <M extends Tool.Metadata> ExecuteResult<M> of(String title, M metadata, String output) {
        return new ExecuteResult<>(title, metadata, output, List.of());
    }

    // 创建带附件的执行结果
    public static <M extends Tool.Metadata> ExecuteResult<M> of(String title, M metadata, String output, List<FilePart> attachments) {
        return new ExecuteResult<>(title, metadata, output, attachments);
    }
}
