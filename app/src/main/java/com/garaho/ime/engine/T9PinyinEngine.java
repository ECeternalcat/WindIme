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
public final class T9PinyinEngine implements ImeEngine, LayeredPinyinEngine {

    private static final int MAX_CANDIDATES = 12;
    private static final int MAX_BUFFER_DIGITS = 16;

    private final PinyinSession session = new PinyinSession();
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
        if (session.getDigits().length() >= MAX_BUFFER_DIGITS) {
            return false;
        }
        session.processDigit(digit);
        recompute();
        return true;
    }

    @Override
    public boolean backspace() {
        if (!session.backspace()) {
            return false;
        }
        recompute();
        return true;
    }

    @Override
    public boolean selectCandidate(int index) {
        if (index < 0 || index >= candidates.size()) {
            return false;
        }
        String word = candidates.get(index);
        session.reset();
        candidates = Collections.emptyList();
        composing = "";
        fire(word);
        return true;
    }

    @Override
    public void reset() {
        if (session.isEmpty() && candidates.isEmpty() && composing.isEmpty()) {
            return;
        }
        session.reset();
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
        return session.getDigits();
    }

    private void recompute() {
        String phrase = session.getPhraseKey();
        List<String> base = buildCandidates(phrase);
        candidates = com.garaho.ime.user.UserWordSource.merge(phrase, base, userWords);
        composing = session.getComposing();
        fire(null);
    }

    @Override
    public List<String> getPinyinOptions() {
        return session.getOptions();
    }

    @Override
    public int getSelectedPinyinIndex() {
        return session.getSelectedIndex();
    }

    @Override
    public boolean previewPinyinOption(int index) {
        if (!session.preview(index)) {
            return false;
        }
        recompute();
        return true;
    }

    @Override
    public boolean confirmPinyinOption(int index) {
        if (!session.confirm(index)) {
            return false;
        }
        recompute();
        return true;
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

    private static List<String> buildCandidates(String phraseKey) {
        if (phraseKey == null || phraseKey.isEmpty()) {
            return Collections.emptyList();
        }
        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (String word : PinyinDictionary.lookup(phraseKey)) {
            out.add(word);
            if (out.size() >= MAX_CANDIDATES) {
                return new ArrayList<>(out);
            }
        }
        int separator = phraseKey.indexOf('\'');
        if (separator > 0) {
            for (String word : PinyinDictionary.lookup(phraseKey.substring(0, separator))) {
                out.add(word);
                if (out.size() >= MAX_CANDIDATES) {
                    break;
                }
            }
        }
        return new ArrayList<>(out);
    }
}
