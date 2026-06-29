package io.opencode.core.session;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class SnapshotManagerTest {

    @Test
    void testSnapshotBeforeRestoresOnUndo() throws Exception {
        var mgr = new SnapshotManager();
        var tempFile = Files.createTempFile("snapshot-test", ".txt");
        try {
            Files.writeString(tempFile, "original content");
            var sid = "test-session-1";

            mgr.snapshotBefore(sid, tempFile, "write");
            assertTrue(mgr.canUndo(sid));
            assertEquals(1, mgr.undoSize(sid));
            assertFalse(mgr.canRedo(sid));
            assertEquals(0, mgr.redoSize(sid));

            Files.writeString(tempFile, "modified content");
            assertEquals("modified content", Files.readString(tempFile));

            var ok = mgr.undo(sid);
            assertTrue(ok);
            assertEquals("original content", Files.readString(tempFile));
            assertEquals(0, mgr.undoSize(sid));
            assertEquals(1, mgr.redoSize(sid));
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    @Test
    void testRedoAfterUndo() throws Exception {
        var mgr = new SnapshotManager();
        var tempFile = Files.createTempFile("snapshot-redo", ".txt");
        try {
            Files.writeString(tempFile, "v1");
            var sid = "test-session-2";

            mgr.snapshotBefore(sid, tempFile, "write");
            Files.writeString(tempFile, "v2");

            mgr.undo(sid);
            assertEquals("v1", Files.readString(tempFile));

            mgr.redo(sid);
            assertEquals("v2", Files.readString(tempFile));
            assertFalse(mgr.canRedo(sid));
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    @Test
    void testUndoOnNonExistentFile() {
        var mgr = new SnapshotManager();
        var sid = "test-session-3";
        mgr.snapshotBefore(sid, Path.of("/nonexistent/file.txt"), "write");
        assertFalse(mgr.canUndo(sid));
    }

    @Test
    void testClear() {
        var mgr = new SnapshotManager();
        var sid = "test-session-4";
        mgr.snapshotBefore(sid, Path.of("/tmp/nonexistent"), "test");
        mgr.clear(sid);
        assertFalse(mgr.canUndo(sid));
        assertFalse(mgr.canRedo(sid));
    }

    @Test
    void testMultipleSnapshots() throws Exception {
        var mgr = new SnapshotManager();
        var f1 = Files.createTempFile("snap-multi-1", ".txt");
        var f2 = Files.createTempFile("snap-multi-2", ".txt");
        var sid = "test-session-5";
        try {
            Files.writeString(f1, "f1-v1");
            Files.writeString(f2, "f2-v1");

            mgr.snapshotBefore(sid, f1, "write");
            mgr.snapshotBefore(sid, f2, "edit");
            assertEquals(2, mgr.undoSize(sid));

            mgr.undo(sid);
            assertEquals("f2-v1", Files.readString(f2));
            assertEquals(1, mgr.undoSize(sid));

            mgr.undo(sid);
            assertEquals("f1-v1", Files.readString(f1));
            assertEquals(0, mgr.undoSize(sid));
        } finally {
            Files.deleteIfExists(f1);
            Files.deleteIfExists(f2);
        }
    }

    @Test
    void testUndoNoSnapshotReturnsFalse() {
        var mgr = new SnapshotManager();
        assertFalse(mgr.undo("nonexistent"));
        assertFalse(mgr.redo("nonexistent"));
    }
}
