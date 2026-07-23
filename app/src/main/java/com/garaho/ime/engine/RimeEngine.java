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
public final class RimeEngine implements ImeEngine, LayeredPinyinEngine {

    private static final String TAG = "RimeEngine";
    private static final int FETCH_LIMIT = 12;
    private static volatile boolean startedInProcess;

    private final PinyinSession session = new PinyinSession();
    private EngineListener listener;
    private List<String> candidates = Collections.emptyList();
    private String composing = "";
    private String currentPhraseKey = "";
    private com.garaho.ime.user.UserWordSource userWords;

    public void setUserWordSource(com.garaho.ime.user.UserWordSource src) {
        this.userWords = src;
    }

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
            startedInProcess = true;
            Log.i(TAG, "Rime started; schema deploys asynchronously on work thread");
            return new RimeEngine();
        } catch (Throwable t) {
            Log.e(TAG, "Rime startup failed", t);
            return null;
        }
    }

    public static boolean hasStartedInProcess() {
        return startedInProcess;
    }

    /**
     * Wait for asynchronous schema deployment and select the requested schema.
     * Intended for the background initialization thread, never the IME thread.
     */
    public boolean awaitSchema(String schemaId, long timeoutMs) {
        long deadline = android.os.SystemClock.uptimeMillis() + Math.max(0, timeoutMs);
        do {
            try {
                String current = Rime.getCurrentRimeSchema();
                if (schemaId.equals(current) || Rime.selectRimeSchema(schemaId)) {
                    Log.i(TAG, "Rime schema ready: " + schemaId);
                    return true;
                }
            } catch (Throwable t) {
                Log.w(TAG, "Schema readiness probe failed: " + t);
            }
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        } while (android.os.SystemClock.uptimeMillis() < deadline);
        Log.e(TAG, "Timed out waiting for Rime schema " + schemaId);
        return false;
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
        session.processDigit(digit);
        pushPhraseToRime();
        return true;
    }

    @Override
    public boolean backspace() {
        if (!isAvailable()) {
            return false;
        }
        if (!session.backspace()) {
            // Nothing composing: hand the backspace back to the host so it can
            // delete already-committed text via the InputConnection.
            return false;
        }
        pushPhraseToRime();
        return true;
    }

    @Override
    public boolean selectCandidate(int index) {
        if (!isAvailable() || index < 0) {
            return false;
        }
        if (index >= candidates.size()) {
            return false;
        }
        String chosen = candidates.get(index);
        int nativeIndex = nativeCandidates.indexOf(chosen);
        if (nativeIndex < 0) {
            clearAfterCommit(chosen);
            return true;
        }
        boolean ok = false;
        try {
            ok = Rime.selectRimeCandidate(nativeIndex, true);
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
        clearAfterCommit(text);
        return true;
    }

    @Override
    public void reset() {
        if (!isAvailable()) {
            return;
        }
        session.reset();
        composing = "";
        candidates = Collections.emptyList();
        nativeCandidates = Collections.emptyList();
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
        pushPhraseToRime();
        return true;
    }

    @Override
    public boolean confirmPinyinOption(int index) {
        if (!session.confirm(index)) {
            return false;
        }
        pushPhraseToRime();
        return true;
    }

    /**
     * Re-segment the digit buffer and replay the resulting pinyin phrase into
     * rime as a fresh key sequence. Uses best-effort (greedy prefix)
     * segmentation so candidates appear for the longest valid syllable prefix
     * even while the user is mid-syllable. Raw digits are never fed to rime
     * (they would be echoed as ASCII); only segmented pinyin letters are sent.
     */
    private void pushPhraseToRime() {
        String buf = session.getDigits();
        String phraseKey = session.getPhraseKey();
        String letters = phraseKey;
        currentPhraseKey = phraseKey;
        composing = session.getComposing();
        Log.d(TAG, "pushPhrase: digits=" + buf + " phrase=" + phraseKey + " letters=" + letters);
        try {
            Rime.clearRimeComposition();
        } catch (Throwable t) {
            Log.w(TAG, "clearRimeComposition failed: " + t);
        }
        if (!letters.isEmpty()) {
            boolean ok = false;
            try {
                ok = Rime.simulateRimeKeySequence(letters);
            } catch (Throwable t) {
                Log.w(TAG, "simulateRimeKeySequence threw: " + t);
            }
            Log.d(TAG, "simulateRimeKeySequence('" + letters + "') -> " + ok);
            if (!ok) {
                for (int i = 0; i < letters.length(); i++) {
                    try {
                        Rime.processRimeKey((int) letters.charAt(i), 0);
                    } catch (Throwable t) {
                        Log.w(TAG, "processRimeKey(" + letters.charAt(i) + ") threw: " + t);
                        break;
                    }
                }
            }
        }
        refresh();
    }

    private void refresh() {
        List<String> list = new ArrayList<>();
        CandidateProto[] arr = null;
        try {
            arr = Rime.getRimeCandidates(0, FETCH_LIMIT);
        } catch (Throwable t) {
            Log.w(TAG, "getRimeCandidates failed: " + t);
        }
        if (arr != null) {
            for (CandidateProto c : arr) {
                if (c != null && c.text != null && !c.text.isEmpty()) {
                    list.add(c.text);
                }
            }
        }
        Log.d(TAG, "getRimeCandidates -> count=" + (arr == null ? -1 : arr.length)
                + " usable=" + list.size()
                + (list.isEmpty() ? "" : " first=" + list.get(0)));
        nativeCandidates = list;
        candidates = com.garaho.ime.user.UserWordSource.merge(currentPhraseKey, list, userWords);
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

    private List<String> nativeCandidates = Collections.emptyList();

    private void clearAfterCommit(String text) {
        try {
            Rime.clearRimeComposition();
        } catch (Throwable ignored) {
        }
        session.reset();
        composing = "";
        candidates = Collections.emptyList();
        nativeCandidates = Collections.emptyList();
        if (listener != null) {
            listener.onCommit(text);
            listener.onComposingChanged(composing);
            listener.onCandidatesChanged(candidates);
        }
    }
}
