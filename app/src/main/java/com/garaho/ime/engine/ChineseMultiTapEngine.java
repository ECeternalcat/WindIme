package com.garaho.ime.engine;

import android.os.Handler;
import android.os.Looper;

import com.garaho.ime.settings.GarahoPrefs;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * 中文 Multi-tap engine (design doc §1.1 state [2]). Each digit cycles through
 * its letter group (press 2 once = a, twice = b, three times = c); finalised
 * letters accumulate into a pinyin buffer. Candidates are looked up from the
 * embedded dictionary by segmenting the spelled letters, giving precise
 * control for rare characters or initials where T9 prediction falls short.
 */
public final class ChineseMultiTapEngine implements ImeEngine, MultiTapSupport {

    private static final int MAX_CANDIDATES = 12;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final GarahoPrefs prefs;
    private EngineListener listener;

    private int pendingDigit = -1;
    private int pendingIndex = 0;
    private final StringBuilder pinyin = new StringBuilder();
    private List<String> candidates = Collections.emptyList();
    private CharSequence composing = "";
    private com.garaho.ime.user.UserWordSource userWords;

    public void setUserWordSource(com.garaho.ime.user.UserWordSource src) {
        this.userWords = src;
    }

    private final Runnable timeoutRunnable = new Runnable() {
        @Override
        public void run() {
            finalizePending();
        }
    };

    public ChineseMultiTapEngine(GarahoPrefs prefs) {
        this.prefs = prefs;
    }

    @Override
    public void setListener(EngineListener listener) {
        this.listener = listener;
    }

    @Override
    public boolean processDigit(int digit) {
        if (!MultiTapCore.isMultiTapDigit(digit)) {
            return false;
        }
        handler.removeCallbacks(timeoutRunnable);
        if (digit == pendingDigit) {
            pendingIndex++;
        } else {
            finalizePending();
            pendingDigit = digit;
            pendingIndex = 0;
        }
        recompute();
        handler.postDelayed(timeoutRunnable, timeoutMs());
        return true;
    }

    @Override
    public void flushPending() {
        handler.removeCallbacks(timeoutRunnable);
        finalizePending();
    }

    private void finalizePending() {
        if (pendingDigit >= 0) {
            char c = MultiTapCore.letter(pendingDigit, pendingIndex);
            if (c != 0) {
                pinyin.append(c);
            }
        }
        pendingDigit = -1;
        pendingIndex = 0;
        recompute();
    }

    private void recompute() {
        String pending = (pendingDigit >= 0)
                ? String.valueOf(MultiTapCore.letter(pendingDigit, pendingIndex))
                : "";
        String base = pinyin.toString() + pending;
        int highlightStart = pinyin.length();
        int highlightEnd = highlightStart + pending.length();
        composing = pending.isEmpty()
                ? base
                : MultiTapHighlight.apply(base, highlightStart, highlightEnd);
        String phraseKey = T9Segmenter.joinKey(T9Segmenter.segmentLetters(pinyin.toString()));
        candidates = com.garaho.ime.user.UserWordSource.merge(phraseKey, lookup(pinyin.toString()), userWords);
        fire();
    }

    private static List<String> lookup(String letters) {
        if (letters == null || letters.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> seg = T9Segmenter.segmentLetters(letters);
        if (seg.isEmpty()) {
            return Collections.emptyList();
        }
        LinkedHashSet<String> out = new LinkedHashSet<>();
        String phraseKey = T9Segmenter.joinKey(seg);
        for (String w : PinyinDictionary.lookup(phraseKey)) {
            out.add(w);
            if (out.size() >= MAX_CANDIDATES) {
                return new ArrayList<>(out);
            }
        }
        for (String w : PinyinDictionary.lookup(seg.get(0))) {
            out.add(w);
            if (out.size() >= MAX_CANDIDATES) {
                break;
            }
        }
        return new ArrayList<>(out);
    }

    @Override
    public boolean backspace() {
        if (pendingDigit >= 0) {
            handler.removeCallbacks(timeoutRunnable);
            pendingDigit = -1;
            pendingIndex = 0;
            recompute();
            return true;
        }
        if (pinyin.length() > 0) {
            pinyin.deleteCharAt(pinyin.length() - 1);
            recompute();
            return true;
        }
        return false;
    }

    @Override
    public boolean selectCandidate(int index) {
        if (index < 0 || index >= candidates.size()) {
            return false;
        }
        String word = candidates.get(index);
        handler.removeCallbacks(timeoutRunnable);
        pendingDigit = -1;
        pendingIndex = 0;
        pinyin.setLength(0);
        candidates = Collections.emptyList();
        composing = "";
        if (listener != null) {
            listener.onCommit(word);
            listener.onComposingChanged(composing);
            listener.onCandidatesChanged(candidates);
        }
        return true;
    }

    @Override
    public void reset() {
        handler.removeCallbacks(timeoutRunnable);
        pendingDigit = -1;
        pendingIndex = 0;
        pinyin.setLength(0);
        candidates = Collections.emptyList();
        composing = "";
        fire();
    }

    @Override
    public int candidateCount() {
        return candidates.size();
    }

    public List<String> getCandidates() {
        return candidates;
    }

    public String getComposing() {
        return composing == null ? "" : composing.toString();
    }

    private int timeoutMs() {
        return prefs == null ? 600 : Math.max(100, prefs.getMultiTapTimeout());
    }

    private void fire() {
        if (listener != null) {
            listener.onComposingChanged(composing);
            listener.onCandidatesChanged(candidates);
        }
    }
}
