package com.garaho.ime.engine;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class T9PinyinEngineTest {

    private static final class CapturingListener implements EngineListener {
        String composing = "";
        java.util.List<String> candidates = java.util.Collections.emptyList();
        String lastCommitted;

        @Override public void onComposingChanged(CharSequence composing) { this.composing = composing.toString(); }
        @Override public void onCandidatesChanged(java.util.List<String> candidates) { this.candidates = candidates; }
        @Override public void onCommit(String text) { this.lastCommitted = text; }
    }

    private T9PinyinEngine newEngine(CapturingListener l) {
        T9PinyinEngine e = new T9PinyinEngine();
        e.setListener(l);
        return e;
    }

    private void type(T9PinyinEngine e, String digits) {
        for (int i = 0; i < digits.length(); i++) {
            e.processDigit(digits.charAt(i) - '0');
        }
    }

    @Test
    public void startsEmpty() {
        CapturingListener l = new CapturingListener();
        T9PinyinEngine e = newEngine(l);
        assertEquals("", e.getComposing());
        assertEquals(0, e.candidateCount());
    }

    @Test
    public void singleSyllable_inputProducesCandidates() {
        CapturingListener l = new CapturingListener();
        T9PinyinEngine e = newEngine(l);
        type(e, "64");
        assertTrue("expected 你 in candidates, got " + l.candidates, l.candidates.contains("你"));
        assertFalse(l.composing.isEmpty());
    }

    @Test
    public void nihao_stagesFirstSyllableThenPhrase() {
        CapturingListener l = new CapturingListener();
        T9PinyinEngine e = newEngine(l);
        type(e, "64426");
        assertEquals("ni'hao", e.getComposing());
        assertTrue("candidates must be non-empty", l.candidates.size() > 0);
        // Staged syllable flow (candidate-bar PR): the first syllable's
        // single characters lead, and the whole phrase stays reachable
        // behind them thanks to the phrase reserve in buildCandidates.
        assertEquals("你", l.candidates.get(0));
        assertTrue("phrase 你好 must stay reachable", l.candidates.contains("你好"));
    }

    @Test
    public void selectCandidate_commitsLeadingSyllableAndKeepsTail() {
        CapturingListener l = new CapturingListener();
        T9PinyinEngine e = newEngine(l);
        type(e, "64426");
        assertTrue(e.selectCandidate(0));
        // Committing the staged leading character (你 from ni) must not
        // discard the uncommitted tail: the remaining digits survive so the
        // user can keep selecting hao.
        assertEquals("你", l.lastCommitted);
        assertEquals("426", e.getBuffer());
        assertTrue(e.candidateCount() > 0);
    }

    @Test
    public void backspaceShrinksBuffer() {
        CapturingListener l = new CapturingListener();
        T9PinyinEngine e = newEngine(l);
        type(e, "64");
        assertTrue(e.backspace());
        assertEquals("6", e.getBuffer());
        e.backspace();
        assertFalse(e.backspace());
        assertEquals("", e.getBuffer());
    }

    @Test
    public void reset_clearsEverything() {
        CapturingListener l = new CapturingListener();
        T9PinyinEngine e = newEngine(l);
        type(e, "64426");
        e.reset();
        assertEquals("", e.getComposing());
        assertEquals(0, e.candidateCount());
        assertEquals("", e.getBuffer());
    }

    @Test
    public void rejectsNonPinyinDigits() {
        CapturingListener l = new CapturingListener();
        T9PinyinEngine e = newEngine(l);
        assertFalse(e.processDigit(0));
        assertFalse(e.processDigit(1));
        assertTrue(e.processDigit(2));
        assertTrue(e.processDigit(9));
    }

    @Test
    public void shijie_resolvesToKnownPhrase() {
        CapturingListener l = new CapturingListener();
        T9PinyinEngine e = newEngine(l);
        type(e, "744543");
        assertTrue("expected 世界 in candidates, got " + l.candidates, l.candidates.contains("世界"));
    }

    @Test
    public void pinyinPreviewRefreshesWordCandidates() {
        CapturingListener l = new CapturingListener();
        T9PinyinEngine e = newEngine(l);
        type(e, "24");

        assertEquals(java.util.Arrays.asList("ai", "bi", "ci", "a", "b", "c"),
                e.getPinyinOptions());
        assertTrue(e.previewPinyinOption(0));
        assertEquals("ai", e.getComposing());
        assertEquals("爱", l.candidates.get(0));
        assertTrue(e.previewPinyinOption(2));
        assertEquals("ci", e.getComposing());
        assertEquals("次", l.candidates.get(0));
    }

    @Test
    public void confirmedPinyinContinuesAsMultipleSyllables() {
        CapturingListener l = new CapturingListener();
        T9PinyinEngine e = newEngine(l);
        type(e, "64");

        int ni = e.getPinyinOptions().indexOf("ni");
        assertTrue(e.confirmPinyinOption(ni));
        type(e, "426");

        assertEquals("ni'hao", e.getComposing());
        // Staged ordering: 你 leads, 你好 stays reachable behind it.
        assertEquals("你", l.candidates.get(0));
        assertTrue(l.candidates.contains("你好"));
    }
}
