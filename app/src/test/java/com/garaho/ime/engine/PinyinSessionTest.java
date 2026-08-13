package com.garaho.ime.engine;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PinyinSessionTest {

    @Test
    public void input24ExposesRequestedLayerOptions() {
        PinyinSession session = type("24");

        assertEquals(Arrays.asList("ai", "bi", "ci", "a", "b", "c"), session.getOptions());
        assertEquals("bi", session.getPhraseKey());
    }

    @Test
    public void previewChangesPhraseWithoutChangingDigits() {
        PinyinSession session = type("24");

        assertTrue(session.preview(0));
        assertEquals("ai", session.getPhraseKey());
        assertEquals("24", session.getDigits());
        assertTrue(session.preview(2));
        assertEquals("ci", session.getPhraseKey());
        assertEquals("24", session.getDigits());
    }

    @Test
    public void confirmingSyllableStartsNewTailOnNextDigit() {
        PinyinSession session = type("24");

        assertTrue(session.confirm(0));
        assertTrue(session.processDigit(6));

        assertEquals(Arrays.asList("ai"), session.getLockedSyllables());
        assertEquals("ai'o", session.getPhraseKey());
        assertEquals("246", session.getDigits());
    }

    @Test
    public void previewedCompleteSyllableLocksOnNextDigit() {
        // Moving the cursor onto a complete reading (e.g. 24 -> ai) and then
        // continuing to type must lock that reading, so multi-syllable input
        // works without an explicit OK (mirrors confirm() above).
        PinyinSession session = type("24");
        int ai = session.getOptions().indexOf("ai");

        assertTrue(session.preview(ai));
        assertEquals("ai", session.getPhraseKey());
        assertEquals("24", session.getDigits());

        assertTrue(session.processDigit(6));

        assertEquals(Arrays.asList("ai"), session.getLockedSyllables());
        assertEquals("ai'o", session.getPhraseKey());
        assertEquals("246", session.getDigits());
    }

    @Test
    public void partialLetterCannotBeConfirmedAsSyllable() {
        PinyinSession session = type("24");

        assertFalse(session.confirm(3));
        assertEquals("", session.getPhraseKey());
        assertEquals("a", session.getComposing());
    }

    @Test
    public void highlightedLetterConstrainsTheNextDigit() {
        PinyinSession session = type("2");
        int b = session.getOptions().indexOf("b");
        assertTrue(session.preview(b));

        session.processDigit(4);

        assertEquals("bi", session.getPhraseKey());
        assertEquals("bi", session.getOptions().get(session.getSelectedIndex()));
    }

    @Test
    public void partialLetterDoesNotProduceACompletePhrase() {
        PinyinSession session = type("24");
        int a = session.getOptions().indexOf("a");

        assertTrue(session.preview(a));
        assertEquals("", session.getPhraseKey());
        assertEquals("a", session.getComposing());
    }

    @Test
    public void backspaceRestoresPreviousLockedSyllable() {
        PinyinSession session = type("24");
        session.confirm(0);
        session.processDigit(6);

        assertTrue(session.backspace());
        assertEquals("ai", session.getPhraseKey());
        assertTrue(session.backspace());
        assertTrue(session.getLockedSyllables().isEmpty());
        assertEquals("24", session.getDigits());
        assertEquals("ai", session.getPhraseKey());
    }

    @Test
    public void lockedPrefixPrefersKnownPhraseForNextSyllable() {
        PinyinSession session = type("64");
        session.confirm(session.getOptions().indexOf("ni"));

        session.processDigit(4);
        session.processDigit(2);
        session.processDigit(6);

        assertEquals("ni'hao", session.getPhraseKey());
    }

    @Test
    public void previewLeadingPrefixUpdatesPhraseKey() {
        // 943744: default segmentation is xie'shi
        PinyinSession session = type("943744");
        // getOptions() exposes readings for the prefix digit code (943 = xie/zhe/...)
        int zhe = session.getOptions().indexOf("zhe");
        assertTrue("zhe must appear in options", zhe >= 0);

        assertTrue(session.preview(zhe));
        assertEquals("zhe'shi", session.getPhraseKey());
        assertEquals("zhe'shi", session.getComposing());
    }

    @Test
    public void previewLeadingPrefixLocksCorrectly() {
        // After previewing zhe for 943744, continuing to type should lock zhe
        PinyinSession session = type("943744");
        int zhe = session.getOptions().indexOf("zhe");
        assertTrue(session.preview(zhe));
        assertEquals("zhe'shi", session.getPhraseKey());

        // Confirm the prefix selection and type more digits
        assertTrue(session.confirm(zhe));
        assertTrue(session.processDigit(4));
        assertTrue(session.processDigit(2));
        assertTrue(session.processDigit(6));

        // The locked syllables should contain zhe and shi (not xie)
        assertTrue(session.getLockedSyllables().contains("zhe"));
        assertTrue(session.getLockedSyllables().contains("shi"));
    }

    private static PinyinSession type(String digits) {
        PinyinSession session = new PinyinSession();
        for (int i = 0; i < digits.length(); i++) {
            session.processDigit(digits.charAt(i) - '0');
        }
        return session;
    }
}
