package com.garaho.ime.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Lightweight pure-Java T9 pinyin engine (Phase-1 backend).
 *
 * <p>Flow:
 * <ol>
 *   <li>Each T9 digit ({@code 2}-{@code 9}) appends to an internal buffer.</li>
 *   <li>{@link T9Segmenter} splits the buffer into every valid pinyin-syllable
 *       combination; each split yields a phrase key like {@code "ni'hao"}.</li>
 *   <li>{@link PinyinDictionary} lookups produce ranked candidates - longer
 *       phrase matches first, then leading-syllable single characters.</li>
 *   <li>{@link #selectCandidate(int)} commits and resets the buffer.</li>
 * </ol>
 *
 * <p>Deterministic and side-effect-free so it can be unit tested on the host
 * JVM with no Android dependencies.
 */
public final class T9PinyinEngine implements ImeEngine {

    private static final int MAX_CANDIDATES = 12;
    private static final int MAX_BUFFER_DIGITS = 16;

    private final StringBuilder buffer = new StringBuilder();
    private EngineListener listener;
    private List<String> candidates = Collections.emptyList();
    private String composing = "";
    private com.garaho.ime.user.UserWordSource userWords;

    public void setUserWordSource(com.garaho.ime.user.UserWordSource src) {
        this.userWords = src;
    }

    @Override
    public void setListener(EngineListener listener) {
        this.listener = listener;
    }

    @Override
    public boolean processDigit(int digit) {
        if (digit < 2 || digit > 9) {
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
        List<List<String>> segmentations = T9Segmenter.segment(buffer.toString());
        List<String> base = buildCandidates(segmentations);
        String phrase = T9Segmenter.bestPhraseKey(segmentations);
        candidates = com.garaho.ime.user.UserWordSource.merge(phrase, base, userWords);
        composing = phrase;
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

    private static List<String> buildCandidates(List<List<String>> segmentations) {
        if (segmentations.isEmpty()) {
            return Collections.emptyList();
        }
        List<List<String>> sorted = new ArrayList<>(segmentations);
        Collections.sort(sorted, T9Segmenter.phraseHitComparator());

        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (List<String> seg : sorted) {
            String key = T9Segmenter.joinKey(seg);
            for (String word : PinyinDictionary.lookup(key)) {
                out.add(word);
                if (out.size() >= MAX_CANDIDATES) {
                    return new ArrayList<>(out);
                }
            }
        }
        List<String> leadSeg = sorted.get(0);
        if (!leadSeg.isEmpty()) {
            for (String word : PinyinDictionary.lookup(leadSeg.get(0))) {
                out.add(word);
                if (out.size() >= MAX_CANDIDATES) {
                    break;
                }
            }
        }
        return new ArrayList<>(out);
    }
}
