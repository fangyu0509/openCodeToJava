package io.opencode.core.session;

import io.opencode.core.model.FileSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// 文件快照管理器 — 支持对文件修改的 undo/redo，基于 FileSnapshot.Stack 双栈实现
@Service
public class SnapshotManager {
    private static final Logger log = LoggerFactory.getLogger(SnapshotManager.class);
    // 会话 ID -> 文件快照栈的映射，每个会话独立管理自己的快照
    private final Map<String, FileSnapshot.Stack> stacks = new ConcurrentHashMap<>();

    // 在文件修改前创建快照，将当前文件内容压入 undo 栈
    public void snapshotBefore(String sessionId, Path filePath, String toolId) {
        try {
            if (Files.exists(filePath)) {
                var content = Files.readString(filePath);
                var snapshot = FileSnapshot.of(sessionId, filePath, content, toolId);
                stack(sessionId).push(snapshot);
                log.debug("Snapshot saved for {} (session={}, tool={})", filePath, sessionId, toolId);
            }
        } catch (Exception e) {
            log.warn("Failed to create snapshot for {}: {}", filePath, e.getMessage());
        }
    }

    // 撤销最近一次修改：从 undo 栈弹出，将当前内容推入 redo 栈，再恢复旧内容
    public boolean undo(String sessionId) {
        var s = stack(sessionId).undoPop();
        if (s == null) return false;
        try {
            var currentContent = Files.readString(s.filePath());
            var redoSnap = FileSnapshot.of(s.sessionId(), s.filePath(), currentContent, s.toolId());
            stack(sessionId).redoPush(redoSnap);
            Files.writeString(s.filePath(), s.content());
            log.info("Undid change to {} (session={}, tool={})", s.filePath(), sessionId, s.toolId());
            return true;
        } catch (Exception e) {
            log.error("Failed to undo {}: {}", s.filePath(), e.getMessage());
            return false;
        }
    }

    // 重做：从 redo 栈弹出，将当前内容推入 undo 栈，再恢复快照内容
    public boolean redo(String sessionId) {
        var s = stack(sessionId).redoPop();
        if (s == null) return false;
        try {
            var currentContent = Files.readString(s.filePath());
            var undoSnap = FileSnapshot.of(s.sessionId(), s.filePath(), currentContent, s.toolId());
            stack(sessionId).undoPush(undoSnap);
            Files.writeString(s.filePath(), s.content());
            log.info("Redid change to {} (session={}, tool={})", s.filePath(), sessionId, s.toolId());
            return true;
        } catch (Exception e) {
            log.error("Failed to redo {}: {}", s.filePath(), e.getMessage());
            return false;
        }
    }

    // 检查是否可撤销
    public boolean canUndo(String sessionId) {
        return stack(sessionId).canUndo();
    }

    // 检查是否可重做
    public boolean canRedo(String sessionId) {
        return stack(sessionId).canRedo();
    }

    // 获取 undo 栈深度
    public int undoSize(String sessionId) {
        return stack(sessionId).undoSize();
    }

    // 获取 redo 栈深度
    public int redoSize(String sessionId) {
        return stack(sessionId).redoSize();
    }

    // 清除指定会话的所有快照
    public void clear(String sessionId) {
        stacks.remove(sessionId);
    }

    // 获取或创建某个会话的快照栈
    private FileSnapshot.Stack stack(String sessionId) {
        return stacks.computeIfAbsent(sessionId, k -> new FileSnapshot.Stack());
    }
}
