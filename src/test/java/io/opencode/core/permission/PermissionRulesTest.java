package io.opencode.core.permission;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PermissionRulesTest {

    @Test
    void defaultRulesAllEmpty() {
        var rules = PermissionRules.defaultRules();
        assertTrue(rules.read().isEmpty());
        assertTrue(rules.edit().isEmpty());
        assertTrue(rules.shell().isEmpty());
        assertTrue(rules.externalDirectory().isEmpty());
        assertTrue(rules.task().isEmpty());
    }

    @Test
    void strictRules() {
        var rules = PermissionRules.strict();
        assertEquals(1, rules.read().size());
        assertEquals(1, rules.edit().size());
        assertEquals(1, rules.shell().size());
        assertEquals(1, rules.externalDirectory().size());
        assertEquals(1, rules.task().size());
        assertEquals(PermissionAction.ALLOW, rules.read().get(0).action());
        assertEquals(PermissionAction.DENY, rules.edit().get(0).action());
    }

    @Test
    void permissiveRules() {
        var rules = PermissionRules.permissive();
        assertEquals(PermissionAction.ALLOW, rules.read().get(0).action());
        assertEquals(PermissionAction.ALLOW, rules.edit().get(0).action());
        assertEquals(PermissionAction.ASK, rules.shell().get(0).action());
        assertEquals(PermissionAction.ASK, rules.externalDirectory().get(0).action());
    }

    @Test
    void nullFieldsDefaultToEmpty() {
        var rules = new PermissionRules(null, null, null, null, null);
        assertTrue(rules.read().isEmpty());
        assertTrue(rules.edit().isEmpty());
        assertTrue(rules.shell().isEmpty());
        assertTrue(rules.externalDirectory().isEmpty());
        assertTrue(rules.task().isEmpty());
    }

    @Test
    void listsAreCopied() {
        var list = new java.util.ArrayList<PermissionRule>();
        list.add(PermissionRule.allow("**/*"));
        var rules = new PermissionRules(list, List.of(), List.of(), List.of(), List.of());
        list.add(PermissionRule.deny("**/*"));
        assertEquals(1, rules.read().size());
    }
}
