package com.garaho.ime.engine;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class EnglishDictionaryTest {

    @Test
    public void exactMatchReturnsWord() {
        assertEquals("hello", EnglishDictionary.exactMatches("43556").get(0));
    }

    @Test
    public void matchesRanksExactBeforePrefix() {
        java.util.List<String> r = EnglishDictionary.matches("43556");
        assertEquals("hello", r.get(0));
        assertTrue("expected non-empty list", !r.isEmpty());
    }

    @Test
    public void prefixCompletionIncludesShorterWordsFirst() {
        java.util.List<String> r = EnglishDictionary.matches("46");
        assertTrue("expected 'go' (46) in matches: " + r, r.contains("go"));
        assertTrue("index 0 should be exact or shortest, got " + r,
                "go".equals(r.get(0)) || r.get(0).length() <= 2);
    }

    @Test
    public void unknownCodeIsEmpty() {
        assertTrue(EnglishDictionary.exactMatches("00000").isEmpty());
    }

    @Test
    public void dictionaryIsPopulated() {
        assertTrue("embedded dict should have hundreds of words",
                EnglishDictionary.wordCount() > 300);
    }
}
