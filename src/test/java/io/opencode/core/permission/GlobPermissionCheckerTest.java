package io.opencode.core.permission;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GlobPermissionCheckerTest {

    private final GlobPermissionChecker checker = new GlobPermissionChecker();
    private final PermissionRules rules = new PermissionRules(
        List.of(PermissionRule.allow("**/*.java"), PermissionRule.deny("src/test/**")),
        List.of(PermissionRule.allow("src/main/**")),
        List.of(PermissionRule.deny("**/secrets/**")),
        List.of(PermissionRule.ask("/tmp/**")),
        List.of(PermissionRule.allow("**/*"))
    );

    @Test
    void allowMatch() {
        assertEquals(PermissionAction.ALLOW, checker.check("read", "src/main/Foo.java", rules));
    }

    @Test
    void denyMatch() {
        var denyFirst = new PermissionRules(
            List.of(PermissionRule.deny("src/test/**"), PermissionRule.allow("**/*.java")),
            List.of(), List.of(), List.of(), List.of());
        assertEquals(PermissionAction.DENY, checker.check("read", "src/test/FooTest.java", denyFirst));
    }

    @Test
    void firstRuleWins() {
        var denyFirst = new PermissionRules(
            List.of(PermissionRule.deny("src/test/**"), PermissionRule.allow("**/*.java")),
            List.of(), List.of(), List.of(), List.of());
        assertEquals(PermissionAction.DENY, checker.check("read", "src/test/Foo.java", denyFirst));
    }

    @Test
    void noMatchDenies() {
        assertEquals(PermissionAction.DENY, checker.check("read", "other/file.txt", rules));
    }

    @Test
    void shellRules() {
        assertEquals(PermissionAction.DENY, checker.check("shell", "src/main/secrets/key.txt", rules));
    }

    @Test
    void writeRules() {
        assertEquals(PermissionAction.ALLOW, checker.check("write", "src/main/Bar.java", rules));
    }

    @Test
    void taskRules() {
        assertEquals(PermissionAction.ALLOW, checker.check("task", "anything", rules));
    }

    @Test
    void defaultToolUsesExternalDirectoryRules() {
        assertEquals(PermissionAction.ASK, checker.check("unknown_tool", "/tmp/foo", rules));
    }

    @Test
    void globMatchesExactPath() {
        assertTrue(GlobPermissionChecker.globMatches("src/main/Foo.java", "src/main/Foo.java"));
    }

    @Test
    void globStarMatchesSingleSegment() {
        assertTrue(GlobPermissionChecker.globMatches("src/*/Foo.java", "src/main/Foo.java"));
        assertFalse(GlobPermissionChecker.globMatches("src/*/Foo.java", "src/main/bar/Foo.java"));
    }

    @Test
    void globDoubleStarMatchesAnyDepth() {
        assertTrue(GlobPermissionChecker.globMatches("**/*.java", "src/main/Foo.java"));
        assertTrue(GlobPermissionChecker.globMatches("**/*.java", "Foo.java"));
        assertTrue(GlobPermissionChecker.globMatches("**/*.java", "a/b/c/d/Foo.java"));
    }

    @Test
    void globQuestionMarkMatchesSingleChar() {
        assertTrue(GlobPermissionChecker.globMatches("src/main/F??.java", "src/main/Foo.java"));
        assertFalse(GlobPermissionChecker.globMatches("src/main/F??.java", "src/main/Foobar.java"));
    }

    @Test
    void globDotMatchesLiteralDot() {
        assertTrue(GlobPermissionChecker.globMatches("src/main/Foo.java", "src/main/Foo.java"));
        assertFalse(GlobPermissionChecker.globMatches("src/main/Foo.java", "src/main/FooXjava"));
    }

    @Test
    void globEmptyPattern() {
        assertTrue(GlobPermissionChecker.globMatches("", ""));
    }

    @Test
    void globPatternWithDirPrefix() {
        assertTrue(GlobPermissionChecker.globMatches("src/**/Foo.java", "src/main/util/Foo.java"));
    }
}
