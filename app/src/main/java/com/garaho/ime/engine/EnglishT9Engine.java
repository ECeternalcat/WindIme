package com.garaho.ime.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Predictive English T9 engine (design doc §3.3.2).
 *
 * <p>Buffers T9 digits and presents the most likely dictionary words via
 * {@link EnglishDictionary}. Behaviour:
 * <ul>
 *   <li>Exact-code matches rank first (shorter = more common).</li>
 *   <li>Prefix-completion matches follow.</li>
 *   <li>If the buffer resolves to nothing, the raw digit string is offered as
 *       a fallback candidate so the user can still commit it.</li>
 * </ul>
 *
 * <p>Composing preview shows the top candidate (or the raw digits when no
 * match exists), giving immediate feedback on the candidate strip.
 */
public final class EnglishT9Engine implements ImeEngine {

    private static final int MAX_CANDIDATES = 12;
    private static final int MAX_BUFFER_DIGITS = 16;

    private final StringBuilder buffer = new StringBuilder();
    private EngineListener listener;
    private List<String> candidates = Collections.emptyList();
    private String composing = "";

    @Override
    public void setListener(EngineListener listener) {
        this.listener = listener;
    }

    @Override
    public boolean processDigit(int digit) {
        if (!EnglishT9Codec.isValidDigit(digit)) {
            return false;
        }
        if (buffer.length() >= MAX_BUFFER_DIGITS) {
            return false;
        }
        buffer.append((char) ('0' + digit));
        recompute();
        return true;
    }

    @Override
    public boolean backspace() {
        if (buffer.length() == 0) {
            return false;
        }
        buffer.deleteCharAt(buffer.length() - 1);
        recompute();
        return true;
    }

    @Override
    public boolean selectCandidate(int index) {
        if (index < 0 || index >= candidates.size()) {
            return false;
        }
        String word = candidates.get(index);
        buffer.setLength(0);
        candidates = Collections.emptyList();
        composing = "";
        fire(word);
        return true;
    }

    @Override
    public void reset() {
        if (buffer.length() == 0 && candidates.isEmpty() && composing.isEmpty()) {
            return;
        }
        buffer.setLength(0);
        candidates = Collections.emptyList();
        composing = "";
        fire(null);
    }

    @Override
    public int candidateCount() {
        return candidates.size();
    }

    public List<String> getCandidates() {
        return candidates;
    }

    public String getComposing() {
        return composing;
    }

    public String getBuffer() {
        return buffer.toString();
    }

    private void recompute() {
        String buf = buffer.toString();
        List<String> matches = EnglishDictionary.matches(buf);
        List<String> out = new ArrayList<>(Math.min(matches.size(), MAX_CANDIDATES));
        for (String w : matches) {
            out.add(w);
            if (out.size() >= MAX_CANDIDATES) {
                break;
            }
        }
        if (out.isEmpty()) {
            out.add(buf);
        }
        candidates = out;
        composing = out.get(0);
        fire(null);
    }

    private void fire(String committed) {
        if (listener == null) {
            return;
        }
        if (committed != null) {
            listener.onCommit(committed);
        }
        listener.onComposingChanged(composing);
        listener.onCandidatesChanged(candidates);
    }
}
