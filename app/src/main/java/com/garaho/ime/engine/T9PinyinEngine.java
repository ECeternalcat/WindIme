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

        // A leading-syllable candidate (for example 一 from yi'zhi) must not
        // discard the uncommitted tail. Rebuild the session from the digits
        // after the consumed prefix, then commit only this word.
        String phrase = session.getPhraseKey();
        int separator = phrase == null ? -1 : phrase.indexOf('\'');
        if (separator > 0) {
            String prefixKey = phrase.substring(0, separator);
            if (PinyinDictionary.lookup(prefixKey).contains(word)) {
                String consumedDigits = PinyinSyllables.t9Encode(prefixKey);
                String allDigits = session.getDigits();
                if (allDigits.startsWith(consumedDigits)
                        && allDigits.length() > consumedDigits.length()) {
                    String remaining = allDigits.substring(consumedDigits.length());
                    session.reset();
                    for (int i = 0; i < remaining.length(); i++) {
                        session.processDigit(remaining.charAt(i) - '0');
                    }
                    recompute();
                    fire(word);
                    return true;
                }
            }
        }

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

        // Flip-phone input is syllable-by-syllable: for hou'xuan show hou
        // first, then keep xuan for the next selection. Do not let a full
        // phrase such as 候选 bypass that interaction.
        int separator = phraseKey.indexOf('\'');
        if (separator > 0) {
            String firstSyllable = phraseKey.substring(0, separator);
            // Keep the staged syllable flow as the default ordering.
            for (String word : PinyinDictionary.lookup(firstSyllable)) {
                out.add(word);
                if (out.size() >= MAX_CANDIDATES) {
                    break;
                }
            }
            // Still expose a complete phrase such as 测试 after the current
            // syllable candidates, so the fast whole-word path is available.
            if (out.size() < MAX_CANDIDATES) {
                for (String word : PinyinDictionary.lookup(phraseKey)) {
                    out.add(word);
                    if (out.size() >= MAX_CANDIDATES) {
                        break;
                    }
                }
            }
            return new ArrayList<>(out);
        }

        for (String word : PinyinDictionary.lookup(phraseKey)) {
            out.add(word);
            if (out.size() >= MAX_CANDIDATES) {
                return new ArrayList<>(out);
            }
        }
        return new ArrayList<>(out);
    }
}
