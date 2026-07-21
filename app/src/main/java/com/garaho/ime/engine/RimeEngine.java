package com.garaho.ime.engine;

import android.util.Log;

import com.osfans.trime.core.CandidateProto;
import com.osfans.trime.core.CommitProto;
import com.osfans.trime.core.Rime;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Native backend that drives the prebuilt {@code librime_jni.so} via the
 * vendored {@link Rime} JNI surface (design doc §3.3).
 *
 * <p>Because the rime schema accepts QWERTY letter keycodes (not raw T9
 * digits), this engine is <i>hybrid</i>: each incoming digit is buffered, the
 * shared {@link T9Segmenter} converts the buffer to a pinyin phrase, and that
 * phrase is fed to librime through {@link Rime#simulateRimeKeySequence}. rime
 * then returns real dictionary candidates - which become far richer once a
 * full rime dictionary (e.g. rime-ice-t9) is dropped into the shared data dir.
 *
 * <p>Engine selection in {@code GarahoImeService} uses this class only when
 * {@link #isAvailable()} is true; otherwise it falls back to
 * {@link T9PinyinEngine}.
 */
public final class RimeEngine implements ImeEngine {

    private static final String TAG = "RimeEngine";
    private static final int FETCH_LIMIT = 12;

    private final StringBuilder digits = new StringBuilder();
    private EngineListener listener;
    private List<String> candidates = Collections.emptyList();
    private String composing = "";

    /**
     * @return {@code true} if the librime shared library has been successfully
     *         loaded (and thus this engine is usable).
     */
    public static boolean isAvailable() {
        return Rime.loadLibrary();
    }

    /**
     * Load librime, run {@link Rime#startupRime} against the staged rime data
     * dirs, and return a ready {@link RimeEngine} - or {@code null} if any step
     * fails (in which case the caller should fall back to {@link T9PinyinEngine}).
     *
     * <p>{@code fullCheck=true} triggers first-launch dictionary compilation.
     * For the bundled starter dictionary this is sub-millisecond; if a large
     * rime-ice dictionary is dropped in, move this call off the main thread.
     */
    public static RimeEngine tryCreate(File sharedDir, File userDir, String version) {
        if (!Rime.loadLibrary()) {
            Log.w(TAG, "librime_jni.so unavailable; skipping native engine");
            return null;
        }
        try {
            // fullCheck=false: only rebuild the prism/table when stale. Schema
            // deployment (start_maintenance) runs on librime's work thread and
            // completes asynchronously, so do NOT probe getCurrentRimeSchema()
            // here - it reliably returns ".default" before the work thread
            // finishes even though deployment will succeed shortly after.
            Rime.startupRime(sharedDir.getAbsolutePath(), userDir.getAbsolutePath(), version, false);
            Log.i(TAG, "Rime started; schema deploys asynchronously on work thread");
            return new RimeEngine();
        } catch (Throwable t) {
            Log.e(TAG, "Rime startup failed", t);
            return null;
        }
    }

    @Override
    public void setListener(EngineListener listener) {
        this.listener = listener;
    }

    @Override
    public boolean processDigit(int digit) {
        if (!isAvailable()) {
            return false;
        }
        if (digit < 2 || digit > 9) {
            return false;
        }
        digits.append((char) ('0' + digit));
        pushPhraseToRime();
        return true;
    }

    @Override
    public boolean backspace() {
        if (!isAvailable()) {
            return false;
        }
        if (digits.length() == 0) {
            Rime.processRimeKey(0xFF08, 0);
            refresh();
            return true;
        }
        digits.deleteCharAt(digits.length() - 1);
        pushPhraseToRime();
        return true;
    }

    @Override
    public boolean selectCandidate(int index) {
        if (!isAvailable() || index < 0) {
            return false;
        }
        String chosen = fetchCandidateText(index);
        boolean ok = false;
        try {
            ok = Rime.selectRimeCandidate(index, true);
        } catch (Throwable t) {
            Log.w(TAG, "selectRimeCandidate failed: " + t);
        }
        if (!ok) {
            return false;
        }
        try {
            Rime.commitRimeComposition();
        } catch (Throwable ignored) {
        }
        CommitProto commit = safeCommit();
        String text = (commit != null && commit.text != null && !commit.text.isEmpty())
                ? commit.text
                : (chosen != null ? chosen : "");
        digits.setLength(0);
        composing = "";
        candidates = Collections.emptyList();
        if (listener != null) {
            listener.onCommit(text);
            listener.onComposingChanged(composing);
            listener.onCandidatesChanged(candidates);
        }
        return true;
    }

    private static String fetchCandidateText(int index) {
        try {
            CandidateProto[] arr = Rime.getRimeCandidates(index, 1);
            if (arr != null && arr.length > 0 && arr[0] != null) {
                return arr[0].text;
            }
        } catch (Throwable t) {
            Log.w(TAG, "getRimeCandidates(index) failed: " + t);
        }
        return null;
    }

    @Override
    public void reset() {
        if (!isAvailable()) {
            return;
        }
        digits.setLength(0);
        composing = "";
        candidates = Collections.emptyList();
        Rime.clearRimeComposition();
        if (listener != null) {
            listener.onComposingChanged(composing);
            listener.onCandidatesChanged(candidates);
        }
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

    /**
     * Re-segment the digit buffer and replay the resulting pinyin phrase into
     * rime as a fresh key sequence. The previous composition is cleared first
     * so each keystroke starts from a clean state.
     */
    private void pushPhraseToRime() {
        String phraseKey = T9Segmenter.bestPhraseKey(digits.toString());
        composing = phraseKey;
        Rime.clearRimeComposition();
        String letters = T9Segmenter.phraseKeyToLetters(phraseKey);
        if (!letters.isEmpty()) {
            Rime.simulateRimeKeySequence(letters);
        }
        refresh();
    }

    private void refresh() {
        List<String> list = new ArrayList<>();
        try {
            CandidateProto[] arr = Rime.getRimeCandidates(0, FETCH_LIMIT);
            if (arr != null) {
                for (CandidateProto c : arr) {
                    if (c != null && c.text != null && !c.text.isEmpty()) {
                        list.add(c.text);
                    }
                }
            }
        } catch (Throwable t) {
            Log.w(TAG, "getRimeCandidates failed: " + t);
        }
        candidates = list;
        CommitProto pending = safeCommit();
        if (listener != null) {
            if (pending != null && pending.text != null && !pending.text.isEmpty()) {
                listener.onCommit(pending.text);
            }
            listener.onComposingChanged(composing);
            listener.onCandidatesChanged(candidates);
        }
    }

    private static CommitProto safeCommit() {
        try {
            return Rime.getRimeCommit();
        } catch (Throwable t) {
            Log.w(TAG, "getRimeCommit failed: " + t);
            return null;
        }
    }
}
