package com.garaho.ime.engine;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class EnglishCapitalizationTest {

    @Test
    public void emptyOrNullIsSentenceStart() {
        assertTrue(EnglishCapitalization.atSentenceStart(""));
        assertTrue(EnglishCapitalization.atSentenceStart(null));
    }

    @Test
    public void onlyWhitespaceIsSentenceStart() {
        assertTrue(EnglishCapitalization.atSentenceStart("   "));
        assertTrue(EnglishCapitalization.atSentenceStart("\n"));
        assertTrue(EnglishCapitalization.atSentenceStart("\n \n  "));
    }

    @Test
    public void afterTerminatorIsSentenceStart() {
        assertTrue(EnglishCapitalization.atSentenceStart("Hi. "));
        assertTrue(EnglishCapitalization.atSentenceStart("what?"));
        assertTrue(EnglishCapitalization.atSentenceStart("yes!\n"));
        assertTrue(EnglishCapitalization.atSentenceStart("end."));
        assertTrue(EnglishCapitalization.atSentenceStart("done.   "));
    }

    @Test
    public void midSentenceIsNotSentenceStart() {
        assertFalse(EnglishCapitalization.atSentenceStart("hello "));
        assertFalse(EnglishCapitalization.atSentenceStart("Hello world"));
        assertFalse(EnglishCapitalization.atSentenceStart("a,b"));
        assertFalse(EnglishCapitalization.atSentenceStart("it's"));
    }

    @Test
    public void capitalizeLowercasesFirstLetterToUpper() {
        assertEquals("Hello", EnglishCapitalization.capitalize("hello"));
        assertEquals("World", EnglishCapitalization.capitalize("world"));
        assertEquals("I", EnglishCapitalization.capitalize("i"));
    }

    @Test
    public void capitalizePreservesAlreadyCapital() {
        assertEquals("Hello", EnglishCapitalization.capitalize("Hello"));
        assertEquals("WORD", EnglishCapitalization.capitalize("WORD"));
    }

    @Test
    public void capitalizeIgnoresNonLetterInitial() {
        assertEquals("123", EnglishCapitalization.capitalize("123"));
        assertEquals(".ok", EnglishCapitalization.capitalize(".ok"));
        assertEquals("", EnglishCapitalization.capitalize(""));
    }

    @Test
    public void capitalizeNullSafe() {
        assertEquals("", EnglishCapitalization.capitalize(null));
    }
}
