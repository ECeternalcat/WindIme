package com.garaho.ime.engine;

import android.os.Handler;
import android.os.Looper;

import com.garaho.ime.settings.GarahoPrefs;
import com.garaho.ime.engine.MultiTapCore.MtapTable;

import java.util.Collections;
import java.util.List;

/**
 * Classic a-b-c Multi-tap engine (design doc §1.1 state [4] 英文 Multi-tap).
 *
 * <p>Pressing a digit repeatedly cycles through its letter group; pressing a
 * different digit or pausing longer than the configured Multi-tap interval
 * finalises the current letter, which fires {@link EngineListener#onCommit}.
 * The cycling letter shows as the composing preview. Intended for passwords,
 * URLs and abbreviations where predictive T9 is undesirable.
 */
public final class EnglishMultiTapEngine implements ImeEngine, MultiTapSupport {

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final GarahoPrefs prefs;
    private EngineListener listener;

    private int pendingDigit = -1;
    private int pendingIndex = 0;
    private CharSequence composing = "";

    /** Shared case-shift state; the service owns the single instance. */
    private CapsState caps;
    /**
     * Legacy fallback when no Caps key is calibrated: the lowercase cycle
     * continues into uppercase (a b c A B C). Only affects the lower-case
     * global state; upper case is always the isolated ABC table.
     */
    private boolean capsFallback;
    /** Table locked for the letter currently being cycled (CapsState §consume). */
    private boolean letterUppercase;

    private final Runnable timeoutRunnable = new Runnable() {
        @Override
        public void run() {
            finalizePending();
        }
    };

    public EnglishMultiTapEngine(GarahoPrefs prefs) {
        this.prefs = prefs;
    }

    /** Inject the shared {@link CapsState} (one case mechanism app-wide). */
    public void setCapsState(CapsState caps) {
        this.caps = caps;
    }

    /** No Caps key calibrated -> enable the abcABC mixed-cycle fallback. */
    public void setCapsFallback(boolean fallback) {
        this.capsFallback = fallback;
    }

    @Override
    public void setListener(EngineListener listener) {
        this.listener = listener;
    }

    private MtapTable tableForNewLetter() {
        if (caps == null) {
            return capsFallback ? MtapTable.MIXED : MtapTable.LOWER;
        }
        boolean upper = caps.nextLetterUppercase();
        caps.consumePendingShift();
        letterUppercase = upper;
        if (upper) {
            return MtapTable.UPPER;
        }
        return capsFallback ? MtapTable.MIXED : MtapTable.LOWER;
    }

    private MtapTable tableForCurrentLetter() {
        return letterUppercase ? MtapTable.UPPER
                : (capsFallback ? MtapTable.MIXED : MtapTable.LOWER);
    }

    @Override
    public boolean processDigit(int digit) {
        if (!MultiTapCore.isMultiTapDigit(digit)) {
            return false;
        }
        handler.removeCallbacks(timeoutRunnable);
        MtapTable table;
        if (digit == pendingDigit) {
            pendingIndex++;
            table = tableForCurrentLetter();
        } else {
            finalizePending();
            pendingDigit = digit;
            pendingIndex = 0;
            table = tableForNewLetter();
        }
        composing = MultiTapHighlight.apply(
                String.valueOf(MultiTapCore.letter(digit, pendingIndex, table)), 0, 1);
        fireComposing();
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
            char c = MultiTapCore.letter(pendingDigit, pendingIndex, tableForCurrentLetter());
            if (listener != null && c != 0) {
                listener.onCommit(String.valueOf(c));
            }
        }
        pendingDigit = -1;
        pendingIndex = 0;
        if (composing.length() > 0) {
            composing = "";
            fireComposing();
        }
    }

    @Override
    public boolean backspace() {
        if (pendingDigit >= 0) {
            handler.removeCallbacks(timeoutRunnable);
            pendingDigit = -1;
            pendingIndex = 0;
            composing = "";
            fireComposing();
            return true;
        }
        return false;
    }

    @Override
    public boolean selectCandidate(int index) {
        flushPending();
        return true;
    }

    @Override
    public void reset() {
        handler.removeCallbacks(timeoutRunnable);
        pendingDigit = -1;
        pendingIndex = 0;
        if (composing.length() > 0) {
            composing = "";
            fireComposing();
        }
    }

    @Override
    public int candidateCount() {
        return 0;
    }

    public String getComposing() {
        return composing == null ? "" : composing.toString();
    }

    private int timeoutMs() {
        return prefs == null ? 600 : Math.max(100, prefs.getMultiTapTimeout());
    }

    private void fireComposing() {
        if (listener != null) {
            listener.onComposingChanged(composing);
            listener.onCandidatesChanged(Collections.<String>emptyList());
        }
    }

    public List<String> getCandidates() {
        return Collections.emptyList();
    }
}
