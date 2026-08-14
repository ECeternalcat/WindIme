package com.garaho.ime.engine;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
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

    // ----- Loop sound-selection mode -----

    @Test
    public void loopModeDefaultsOffAndNormalModelIntact() {
        PinyinSession session = type("24");
        assertFalse(session.isLoopMode());
        // Normal model exposes the partial "keep typing" letters too.
        assertTrue(session.getOptions().contains("a"));
    }

    @Test
    public void loopModeExposesFixedPositionsAndWraps() {
        // 943744 segments as two syllables (xie/zhe + shi).
        PinyinSession session = type("943744");
        session.setLoopMode(true);

        assertEquals(2, session.getLoopPositionCount());
        assertEquals(0, session.getLoopEditPosition());

        // Every reading offered at the current position shares one digit code
        // (the segmentation is fixed; only the reading varies).
        String group0 = PinyinSyllables.t9Encode(session.getOptions().get(0));
        for (String opt : session.getOptions()) {
            assertEquals(group0, PinyinSyllables.t9Encode(opt));
        }

        assertTrue(session.confirmAndAdvance(0));
        assertEquals(1, session.getLoopEditPosition());
        assertEquals("744", PinyinSyllables.t9Encode(session.getOptions().get(0)));

        // After the last position, confirm wraps back to the first.
        assertTrue(session.confirmAndAdvance(0));
        assertEquals(0, session.getLoopEditPosition());
    }

    @Test
    public void loopModeOffersOnlyCompleteSyllables() {
        PinyinSession session = type("24");
        session.setLoopMode(true);
        for (String opt : session.getOptions()) {
            assertTrue("loop options must be complete syllables: " + opt,
                    PinyinSyllables.isSyllable(opt));
        }
    }

    @Test
    public void loopPreviewSwapsReadingAtCurrentPosition() {
        PinyinSession session = type("943744");
        session.setLoopMode(true);

        List<String> opts = session.getOptions();
        int current = session.getSelectedIndex();
        int other = (current == 0) ? 1 : 0;
        String before = session.getPhraseKey();

        assertTrue(session.preview(other));
        assertNotEquals(before, session.getPhraseKey());
        // Same digit group, so the digits buffer never changes.
        assertEquals("943744", session.getDigits());
    }

    @Test
    public void loopOffRestoresNormalModel() {
        PinyinSession session = type("943744");
        session.setLoopMode(true);
        session.setLoopMode(false);

        // Back to the legacy leading-prefix model: readings for the 943 prefix
        // are exposed and the partial-letter behaviour is available again.
        assertTrue(session.getOptions().indexOf("zhe") >= 0);
    }

    private static PinyinSession type(String digits) {
        PinyinSession session = new PinyinSession();
        for (int i = 0; i < digits.length(); i++) {
            session.processDigit(digits.charAt(i) - '0');
        }
        return session;
    }
}
