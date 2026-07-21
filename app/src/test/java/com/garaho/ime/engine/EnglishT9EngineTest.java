package com.garaho.ime.engine;

import org.junit.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class EnglishT9EngineTest {

    private static final class Capture implements EngineListener {
        String composing = "";
        List<String> candidates = Collections.emptyList();
        String lastCommitted;

        @Override public void onComposingChanged(String composing) { this.composing = composing; }
        @Override public void onCandidatesChanged(List<String> candidates) { this.candidates = candidates; }
        @Override public void onCommit(String text) { this.lastCommitted = text; }
    }

    private EnglishT9Engine newEngine(Capture c) {
        EnglishT9Engine e = new EnglishT9Engine();
        e.setListener(c);
        return e;
    }

    private void type(EnglishT9Engine e, String digits) {
        for (int i = 0; i < digits.length(); i++) {
            e.processDigit(digits.charAt(i) - '0');
        }
    }

    @Test
    public void startsEmpty() {
        EnglishT9Engine e = newEngine(new Capture());
        assertEquals("", e.getComposing());
        assertEquals(0, e.candidateCount());
    }

    @Test
    public void helloPredictsHelloFirst() {
        Capture c = new Capture();
        EnglishT9Engine e = newEngine(c);
        type(e, "43556");
        assertTrue("expected hello in candidates: " + c.candidates, c.candidates.contains("hello"));
        assertEquals("hello", c.candidates.get(0));
        assertEquals("hello", e.getComposing());
    }

    @Test
    public void prefixYieldsCompletions() {
        Capture c = new Capture();
        EnglishT9Engine e = newEngine(c);
        type(e, "46");
        assertTrue("expected go (46) in candidates: " + c.candidates, c.candidates.contains("go"));
        assertTrue(c.candidates.size() >= 1);
    }

    @Test
    public void selectCandidateCommitsAndClears() {
        Capture c = new Capture();
        EnglishT9Engine e = newEngine(c);
        type(e, "43556");
        int idx = c.candidates.indexOf("hello");
        assertTrue("hello must be a candidate", idx >= 0);
        assertTrue(e.selectCandidate(idx));
        assertEquals("hello", c.lastCommitted);
        assertEquals("", e.getComposing());
        assertEquals(0, e.candidateCount());
    }

    @Test
    public void backspaceShrinksBuffer() {
        EnglishT9Engine e = newEngine(new Capture());
        type(e, "435");
        assertTrue(e.backspace());
        assertEquals("43", e.getBuffer());
        e.backspace();
        e.backspace();
        assertFalse("empty buffer cannot backspace", e.backspace());
    }

    @Test
    public void noMatchFallsBackToRawDigits() {
        Capture c = new Capture();
        EnglishT9Engine e = newEngine(c);
        type(e, "9999");
        assertEquals(1, c.candidates.size());
        assertEquals("9999", c.candidates.get(0));
        assertEquals("9999", e.getComposing());
    }

    @Test
    public void rejectsOneAndZero() {
        EnglishT9Engine e = newEngine(new Capture());
        assertFalse(e.processDigit(0));
        assertFalse(e.processDigit(1));
        assertTrue(e.processDigit(2));
    }

    @Test
    public void resetClearsState() {
        Capture c = new Capture();
        EnglishT9Engine e = newEngine(c);
        type(e, "43556");
        e.reset();
        assertEquals("", e.getComposing());
        assertEquals("", e.getBuffer());
        assertEquals(0, e.candidateCount());
    }
}
