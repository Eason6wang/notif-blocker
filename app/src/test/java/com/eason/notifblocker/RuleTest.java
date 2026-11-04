package com.eason.notifblocker;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class RuleTest {
    @Test
    public void titleMatchIsCaseInsensitiveAndPartial() {
        Rule rule = new Rule("", "voicemail", true);
        assertTrue(rule.matches("com.samsung.android.dialer", "New Voicemail"));
        assertFalse(rule.matches("com.samsung.android.dialer", "Missed call"));
        assertFalse(rule.matches("com.samsung.android.dialer", null));
    }

    @Test
    public void packageMustMatchWhenSet() {
        Rule rule = new Rule("com.a", "charging", true);
        assertTrue(rule.matches("com.a", "Fast charging"));
        assertFalse(rule.matches("com.b", "Fast charging"));
    }

    @Test
    public void packageOnlyRuleBlocksEverythingFromThatApp() {
        Rule rule = new Rule("com.a", "", true);
        assertTrue(rule.matches("com.a", "anything"));
        assertTrue(rule.matches("com.a", null));
        assertFalse(rule.matches("com.b", "anything"));
    }

    @Test
    public void disabledOrEmptyRulesNeverMatch() {
        assertFalse(new Rule("", "x", false).matches("p", "x"));
        assertTrue(new Rule("", "", true).isEmpty());
        assertFalse(new Rule("", "", true).matches("p", "x"));
    }

    @Test
    public void jsonRoundTrip() throws Exception {
        Rule rule = Rule.fromJson(new Rule(" com.a ", " Hi ", false).toJson());
        assertEquals("com.a", rule.pkg);
        assertEquals("Hi", rule.title);
        assertFalse(rule.enabled);
    }
}
