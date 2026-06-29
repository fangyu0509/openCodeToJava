package io.opencode.core.model;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class FileSnapshotTest {

    @Test
    void testCreateSnapshot() {
        var snap = FileSnapshot.of("session-1", Path.of("/test/file.txt"), "hello", "write");
        assertEquals("session-1", snap.sessionId());
        assertEquals(Path.of("/test/file.txt"), snap.filePath());
        assertEquals("hello", snap.content());
        assertEquals("write", snap.toolId());
        assertTrue(snap.timestamp() > 0);
    }

    @Test
    void testStackPushUndoPopRedoPop() {
        var stack = new FileSnapshot.Stack();
        assertFalse(stack.canUndo());
        assertFalse(stack.canRedo());
        assertEquals(0, stack.undoSize());
        assertEquals(0, stack.redoSize());

        var s1 = FileSnapshot.of("s1", Path.of("/a.txt"), "a", "write", 100);
        var s2 = FileSnapshot.of("s1", Path.of("/b.txt"), "b", "edit", 200);

        stack.push(s1);
        assertTrue(stack.canUndo());
        assertEquals(1, stack.undoSize());
        assertEquals(0, stack.redoSize());

        stack.push(s2);
        assertEquals(2, stack.undoSize());

        stack.undoPop();
        assertEquals(1, stack.undoSize());
        assertEquals(0, stack.redoSize()); // undoPop doesn't push to redo

        stack.redoPush(s2);
        assertEquals(1, stack.redoSize());
    }

    @Test
    void testRedoClearedOnPush() {
        var stack = new FileSnapshot.Stack();
        var s1 = FileSnapshot.of("s1", Path.of("/a.txt"), "a", "write", 100);
        var s2 = FileSnapshot.of("s1", Path.of("/b.txt"), "b", "write", 200);

        stack.push(s1);
        stack.undoPop();
        stack.redoPush(s1);
        assertTrue(stack.canRedo());

        stack.push(s2);
        assertFalse(stack.canRedo());
        assertEquals(1, stack.undoSize());
    }

    @Test
    void testUndoOnEmptyStack() {
        var stack = new FileSnapshot.Stack();
        assertNull(stack.undoPop());
        assertNull(stack.redoPop());
    }

    @Test
    void testUndoPushRedoPush() {
        var stack = new FileSnapshot.Stack();
        var s1 = FileSnapshot.of("s1", Path.of("/a.txt"), "a", "write", 100);

        stack.undoPush(s1);
        assertTrue(stack.canUndo());

        var popped = stack.undoPop();
        assertEquals(s1, popped);
        assertFalse(stack.canUndo());

        stack.redoPush(s1);
        assertTrue(stack.canRedo());
        assertEquals(s1, stack.redoPop());
    }
}
