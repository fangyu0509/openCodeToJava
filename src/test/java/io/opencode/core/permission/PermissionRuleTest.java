package io.opencode.core.permission;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PermissionRuleTest {

    @Test
    void allowFactory() {
        var rule = PermissionRule.allow("**/*.java");
        assertEquals("**/*.java", rule.pattern());
        assertEquals(PermissionAction.ALLOW, rule.action());
    }

    @Test
    void denyFactory() {
        var rule = PermissionRule.deny("**/secrets/**");
        assertEquals(PermissionAction.DENY, rule.action());
    }

    @Test
    void askFactory() {
        var rule = PermissionRule.ask("/tmp/**");
        assertEquals(PermissionAction.ASK, rule.action());
    }

    @Test
    void blankPatternThrows() {
        assertThrows(IllegalArgumentException.class, () -> new PermissionRule("", PermissionAction.ALLOW));
        assertThrows(IllegalArgumentException.class, () -> new PermissionRule(null, PermissionAction.ALLOW));
    }

    @Test
    void nullActionDefaultsToDeny() {
        var rule = new PermissionRule("**/*", null);
        assertEquals(PermissionAction.DENY, rule.action());
    }

    @Test
    void equality() {
        var a = PermissionRule.allow("**/*");
        var b = PermissionRule.allow("**/*");
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }
}
