package io.opencode.core.session;

import java.util.Optional;

// 文件部件 record — 表示会话中引用的文件，包含路径、可选内容、媒体类型和校验和
public record FilePart(
    String path,                // 文件路径
    Optional<String> content,   // 文件内容（仅在需要时携带）
    Optional<String> mediaType, // MIME 类型
    Optional<String> checksum   // 内容校验和
) {
    // 创建一个携带完整内容的文件部件（用于内联传输文件内容）
    public static FilePart of(String path, String content) {
        return new FilePart(path, Optional.of(content), Optional.empty(), Optional.empty());
    }

    // 创建一个仅引用路径的文件部件（不含实际内容）
    public static FilePart reference(String path) {
        return new FilePart(path, Optional.empty(), Optional.empty(), Optional.empty());
    }
}
