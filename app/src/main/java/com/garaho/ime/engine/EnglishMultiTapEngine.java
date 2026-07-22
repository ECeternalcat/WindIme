package com.garaho.ime.engine;

import android.os.Handler;
import android.os.Looper;

import com.garaho.ime.settings.GarahoPrefs;

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

    private final Runnable timeoutRunnable = new Runnable() {
        @Override
        public void run() {
            finalizePending();
        }
    };

    public EnglishMultiTapEngine(GarahoPrefs prefs) {
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
        composing = MultiTapHighlight.apply(
                String.valueOf(MultiTapCore.letter(digit, pendingIndex)), 0, 1);
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
            char c = MultiTapCore.letter(pendingDigit, pendingIndex);
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
