package com.garaho.ime.engine;

import android.util.Log;

import com.garaho.ime.rime.RimeLifecycle;
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
    private final PinyinSession session = new PinyinSession();
    private EngineListener listener;
    private List<String> candidates = Collections.emptyList();
    private String composing = "";
    private String currentPhraseKey = "";
    private String lastFedLetters = "";
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
            RimeLifecycle.markNativeStarted();
            Log.i(TAG, "Rime started; schema deploys asynchronously on work thread");
            return new RimeEngine();
        } catch (Throwable t) {
            Log.e(TAG, "Rime startup failed", t);
            return null;
        }
    }

    public static boolean hasStartedInProcess() {
        return RimeLifecycle.hasNativeStarted();
    }

    /**
     * Build an engine against an already-running native Rime (same process),
     * without calling {@link Rime#startupRime} again. Used when the IME service
     * is recreated mid-process: native state is reusable, re-initializing it
     * would start a second, conflicting maintenance session.
     *
     * @return an engine bound to the native process, or {@code null} if native
     *         Rime is not running / not loadable in this process. Callers must
     *         still wait for schema readiness while state is DEPLOYING.
     */
    public static RimeEngine tryReattach(String schemaId) {
        if (!RimeLifecycle.hasNativeStarted() || !Rime.loadLibrary()) {
            return null;
        }
        try {
            RimeEngine engine = new RimeEngine();
            if (RimeLifecycle.getNativeState() == RimeLifecycle.NativeState.READY) {
                try {
                    String current = Rime.getCurrentRimeSchema();
                    if (!schemaId.equals(current)) {
                        Rime.selectRimeSchema(schemaId);
                    }
                } catch (Throwable t) {
                    Log.w(TAG, "reattach schema select failed: " + t);
                }
            }
            Log.i(TAG, "Rime reattached (native already started)");
            return engine;
        } catch (Throwable t) {
            Log.w(TAG, "Rime reattach failed: " + t);
            return null;
        }
    }

    /**
     * Persist user learning at a safe point (no deployment in flight). Returns
     * false if native Rime is not running so callers need not probe separately.
     */
    public static boolean syncUserData() {
        if (!RimeLifecycle.hasNativeStarted() || !Rime.loadLibrary()) {
            return false;
        }
        try {
            boolean ok = Rime.syncRimeUserData();
            Log.i(TAG, "syncRimeUserData -> " + ok);
            return ok;
        } catch (Throwable t) {
            Log.w(TAG, "syncRimeUserData failed: " + t);
            return false;
        }
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
                    RimeLifecycle.markSchemaReady();
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

        // Rime may expose a leading-character candidate for a multi-syllable
        // composition. Commit that prefix while rebuilding Rime with the
        // remaining digits, instead of clearing the whole composition.
        if (commitPrefixAndKeepTail(chosen)) {
            return true;
        }

        int nativeIndex = nativeCandidates.indexOf(chosen);
        if (nativeIndex < 0) {
            // User-word fast path: the chosen word came from the user
            // dictionary, not from Rime. Clear Rime's stale composition
            // before committing so its internal state stays consistent
            // (audit M-1).
            try {
                Rime.clearRimeComposition();
            } catch (Throwable ignored) {
            }
            Log.d(TAG, "selectCandidate: user-word fast path");
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

    private boolean commitPrefixAndKeepTail(String chosen) {
        String phrase = session.getPhraseKey();
        int separator = phrase == null ? -1 : phrase.indexOf('\'');
        if (separator <= 0) {
            return false;
        }
        String prefixKey = phrase.substring(0, separator);
        if (!PinyinDictionary.lookup(prefixKey).contains(chosen)) {
            return false;
        }
        String consumed = PinyinSyllables.t9Encode(prefixKey);
        String digits = session.getDigits();
        if (!digits.startsWith(consumed) || digits.length() <= consumed.length()) {
            return false;
        }
        String remaining = digits.substring(consumed.length());
        session.reset();
        for (int i = 0; i < remaining.length(); i++) {
            session.processDigit(remaining.charAt(i) - '0');
        }
        if (listener != null) {
            listener.onCommit(chosen);
        }
        pushPhraseToRime();
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

    @Override
    public boolean confirmAndAdvancePinyin(int index) {
        if (!session.confirmAndAdvance(index)) {
            return false;
        }
        pushPhraseToRime();
        return true;
    }

    @Override
    public void setLoopMode(boolean loop) {
        session.setLoopMode(loop);
        pushPhraseToRime();
    }

    @Override
    public boolean isLoopMode() {
        return session.isLoopMode();
    }

    @Override
    public int getLoopEditPosition() {
        return session.getLoopEditPosition();
    }

    @Override
    public int getLoopPositionCount() {
        return session.getLoopPositionCount();
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
        boolean appended = !lastFedLetters.isEmpty()
                && letters.startsWith(lastFedLetters);
        try {
            if (!appended) {
                Rime.clearRimeComposition();
            }
        } catch (Throwable t) {
            Log.w(TAG, "clearRimeComposition failed: " + t);
        }
        String feed = appended ? letters.substring(lastFedLetters.length()) : letters;
        if (!feed.isEmpty()) {
            boolean ok = false;
            try {
                ok = Rime.simulateRimeKeySequence(feed);
            } catch (Throwable t) {
                Log.w(TAG, "simulateRimeKeySequence threw: " + t);
            }
            if (!ok) {
                for (int i = 0; i < feed.length(); i++) {
                    try {
                        Rime.processRimeKey((int) feed.charAt(i), 0);
                    } catch (Throwable t) {
                        Log.w(TAG, "processRimeKey failed at index " + i, t);
                        break;
                    }
                }
            }
        }
        lastFedLetters = letters;
        refresh();
    }

    private void refresh() {
        List<String> list = new ArrayList<>();
        String prefixCandidateKey = firstSyllableOf(currentPhraseKey);

        // Always read Rime's candidates first.  The phone-specific, staged
        // syllable candidates below are only a fallback/extra set; they must
        // never displace a real Rime phrase at the head of the candidate bar.
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
                + " usable=" + list.size());
        List<String> rimeCandidates = new ArrayList<>(list);
        // User-added words come after the native Rime dictionary.  This keeps
        // the learned/custom entries available without stealing Rime's first
        // candidate position.
        list = com.garaho.ime.user.UserWordSource.append(
                currentPhraseKey, rimeCandidates, userWords);
        if (prefixCandidateKey != null) {
            // Append the manually selectable first-syllable candidates after
            // Rime and user words. LinkedHashSet keeps order and removes
            // duplicates.
            java.util.LinkedHashSet<String> combined =
                    new java.util.LinkedHashSet<>(list);
            combined.addAll(PinyinDictionary.lookup(prefixCandidateKey));
            list = new ArrayList<>(combined);
        }
        nativeCandidates = rimeCandidates;
        candidates = list;
        CommitProto pending = safeCommit();
        if (listener != null) {
            if (pending != null && pending.text != null && !pending.text.isEmpty()) {
                listener.onCommit(pending.text);
            }
            notifyCandidates();
        }
    }

    private void notifyCandidates() {
        if (listener != null) {
            listener.onComposingChanged(composing);
            listener.onCandidatesChanged(candidates);
        }
    }

    private static String firstSyllableOf(String phraseKey) {
        if (phraseKey == null) {
            return null;
        }
        int separator = phraseKey.indexOf('\'');
        return separator > 0 ? phraseKey.substring(0, separator) : null;
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
        lastFedLetters = "";
        if (listener != null) {
            listener.onCommit(text);
            listener.onComposingChanged(composing);
            listener.onCandidatesChanged(candidates);
        }
    }
}
