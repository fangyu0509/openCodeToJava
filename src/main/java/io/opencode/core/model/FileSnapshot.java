package io.opencode.core.model;

import java.nio.file.Path;
import java.util.List;

/**
 * 文件快照记录，保存某个时间点的文件状态，用于实现撤销/重做功能
 */
public record FileSnapshot(
    String sessionId,   // 所属会话 ID
    Path filePath,      // 文件路径
    String content,     // 文件内容快照
    String toolId,      // 触发快照的工具调用 ID
    long timestamp      // 快照创建时间戳（毫秒）
) {
    /**
     * 创建文件快照，自动使用当前系统时间作为时间戳
     */
    public static FileSnapshot of(String sessionId, Path filePath, String content, String toolId) {
        return new FileSnapshot(sessionId, filePath, content, toolId, System.currentTimeMillis());
    }

    /**
     * 创建文件快照，使用指定的时间戳
     */
    public static FileSnapshot of(String sessionId, Path filePath, String content, String toolId, long timestamp) {
        return new FileSnapshot(sessionId, filePath, content, toolId, timestamp);
    }

    /**
     * 文件快照栈，管理撤销/重做操作的历史状态
     */
    public static final class Stack {
        private final java.util.ArrayDeque<FileSnapshot> undo = new java.util.ArrayDeque<>(); // 撤销栈
        private final java.util.ArrayDeque<FileSnapshot> redo = new java.util.ArrayDeque<>(); // 重做栈

        /**
         * 推入新快照到撤销栈，并清空重做栈（新操作会使旧重做记录失效）
         */
        public void push(FileSnapshot snapshot) {
            undo.push(snapshot);
            redo.clear();
        }

        /** 弹出并返回撤销栈顶的快照 */
        public FileSnapshot undoPop() {
            return undo.poll();
        }

        /** 弹出并返回重做栈顶的快照 */
        public FileSnapshot redoPop() {
            return redo.poll();
        }

        /** 将快照推回撤销栈（通常用于重做后将快照放回原位） */
        public void undoPush(FileSnapshot snapshot) {
            undo.push(snapshot);
        }

        /** 将快照推回重做栈（通常用于撤销后将快照放回原位） */
        public void redoPush(FileSnapshot snapshot) {
            redo.push(snapshot);
        }

        /** 判断是否可以执行撤销操作 */
        public boolean canUndo() { return !undo.isEmpty(); }
        /** 判断是否可以执行重做操作 */
        public boolean canRedo() { return !redo.isEmpty(); }
        /** 获取撤销栈的深度 */
        public int undoSize() { return undo.size(); }
        /** 获取重做栈的深度 */
        public int redoSize() { return redo.size(); }

        /** 返回撤销栈的不可变副本 */
        public List<FileSnapshot> undoStack() { return List.copyOf(undo); }
        /** 返回重做栈的不可变副本 */
        public List<FileSnapshot> redoStack() { return List.copyOf(redo); }
    }
}
