package com.garaho.ime;

import com.garaho.ime.engine.ChineseMultiTapEngine;
import com.garaho.ime.engine.EngineListener;
import com.garaho.ime.engine.EnglishMultiTapEngine;
import com.garaho.ime.engine.EnglishT9Engine;
import com.garaho.ime.engine.ImeEngine;
import com.garaho.ime.engine.InputMode;
import com.garaho.ime.engine.MultiTapSupport;
import com.garaho.ime.engine.RimeEngine;
import com.garaho.ime.engine.T9PinyinEngine;
import com.garaho.ime.keymap.InputAction;
import com.garaho.ime.keymap.KeyMapper;
import com.garaho.ime.rime.RimeData;
import com.garaho.ime.settings.GarahoPrefs;
import com.garaho.ime.ui.CandidateBar;
import com.garaho.ime.ui.SymbolPanel;

import android.inputmethodservice.InputMethodService;
import android.os.SystemClock;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import java.util.List;

/**
 * Top-level IME entry point (design doc §2 layer 5).
 *
 * <p>Receives physical {@link KeyEvent}s, routes them through {@link KeyMapper}
 * to obtain an {@link InputAction}, then dispatches to the active input mode
 * (design doc §3.1.1 - 中/英/数字/符号):
 * <ul>
 *   <li>{@link InputMode#ZH} - pinyin via {@link T9PinyinEngine} or
 *       {@link RimeEngine} (librime).</li>
 *   <li>{@link InputMode#EN} - {@link EnglishT9Engine} predictive dictionary.</li>
 *   <li>{@link InputMode#NUM} - direct digit commit, no candidate strip.</li>
 *   <li>SYM - {@link SymbolPanel} via {@code SHOW_SYMBOL_PANEL}.</li>
 * </ul>
 * The {@code TOGGLE_LANG_MODE} action cycles ZH &rarr; EN &rarr; NUM internally.
 */
public class GarahoImeService extends InputMethodService implements EngineListener {

    private static final String TAG = "GarahoIme";
    private static final long RESET_COMBO_WINDOW_MS = 5000;

    private KeyMapper keyMapper;
    private ImeEngine pinyinEngine;
    private EnglishT9Engine englishEngine;
    private ChineseMultiTapEngine zhMultiTapEngine;
    private EnglishMultiTapEngine enMultiTapEngine;
    private InputMode mode = InputMode.ZH;

    private CandidateBar candidateBar;
    private SymbolPanel symbolPanel;
    private FrameLayout rootContainer;

    private long backspaceDownAt;
    private long poundDownAt;

    @Override
    public void onCreate() {
        super.onCreate();
        keyMapper = new KeyMapper(this);

        RimeData rimeData = new RimeData(this);
        rimeData.ensureExtracted(this);

        RimeEngine rimeEngine = RimeEngine.tryCreate(
                rimeData.getSharedDir(), rimeData.getUserDir(), "0.2.1");
        if (rimeEngine != null) {
            pinyinEngine = rimeEngine;
            Log.i(TAG, "Pinyin engine: native librime (RimeEngine)");
        } else {
            pinyinEngine = new T9PinyinEngine();
            Log.i(TAG, "Pinyin engine: built-in T9PinyinEngine");
        }
        pinyinEngine.setListener(this);

        englishEngine = new EnglishT9Engine();
        englishEngine.setListener(this);

        GarahoPrefs prefs = new GarahoPrefs(this);
        zhMultiTapEngine = new ChineseMultiTapEngine(prefs);
        zhMultiTapEngine.setListener(this);
        enMultiTapEngine = new EnglishMultiTapEngine(prefs);
        enMultiTapEngine.setListener(this);
    }

    @Override
    public View onCreateInputView() {
        LayoutInflater inflater = (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);
        rootContainer = new FrameLayout(this);
        candidateBar = (CandidateBar) inflater.inflate(R.layout.view_candidate_bar, rootContainer, false);
        rootContainer.addView(candidateBar,
                new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT));
        candidateBar.setModeLabel(indicatorLabel());
        return rootContainer;
    }

    /** Mode label for the candidate strip, empty when the user hides the indicator. */
    private String indicatorLabel() {
        boolean show = new GarahoPrefs(this).getShowIndicator();
        return show ? mode.label() : "";
    }

    /**
     * Flip-phones expose a physical keyboard, so the platform default
     * ({@code keyboard != NOKEYS && hardKeyboardHidden != YES}) hides the IME
     * input view - observed as the candidate strip flashing for ~1s then
     * vanishing. WindIme's candidate strip <b>is</b> the primary 0-Touch UI
     * (design doc §4), so force it to stay shown whenever an editor is focused.
     */
    @Override
    public boolean onEvaluateInputViewShown() {
        return true;
    }

    @Override
    public boolean onShowInputRequested(int flags, boolean configChange) {
        return true;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        InputAction action = keyMapper.resolve(keyCode, event.getScanCode());
        if (action != InputAction.NONE) {
            Log.d(TAG, "onKeyDown keyCode=" + keyCode + " scan=" + event.getScanCode() + " -> " + action + " (mode=" + mode + ")");
        }
        if (action == InputAction.BACKSPACE_DELETE) {
            backspaceDownAt = event.getEventTime();
            if (poundDownAt != 0 && SystemClock.uptimeMillis() - poundDownAt < 50) {
                checkSafeEscapeCombo();
            }
        } else if (action == InputAction.INPUT_KEY_POUND) {
            poundDownAt = event.getEventTime();
        }
        return handleAction(action) || super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyLongPress(int keyCode, KeyEvent event) {
        InputAction action = keyMapper.resolve(keyCode, event.getScanCode());
        if (action == InputAction.BACKSPACE_DELETE) {
            if (SystemClock.uptimeMillis() - backspaceDownAt >= RESET_COMBO_WINDOW_MS) {
                checkSafeEscapeCombo();
                return true;
            }
        }
        return super.onKeyLongPress(keyCode, event);
    }

    private boolean handleAction(InputAction action) {
        if (action == InputAction.NONE) {
            return false;
        }
        // Multi-tap engines keep a cycling letter; finalise it before any
        // unrelated action (literal commit, nav, confirm, mode switch, symbol)
        // so the letter isn't lost. Cycling digits (2-9) handle finalize
        // internally; backspace cancels the pending letter instead.
        int cyclingDigit = action.digit();
        boolean isCyclingDigit = cyclingDigit >= 2 && cyclingDigit <= 9;
        if (action != InputAction.BACKSPACE_DELETE && !isCyclingDigit) {
            flushMultiTapIfActive();
        }
        switch (action) {
            case INPUT_KEY_1:
            case INPUT_KEY_2:
            case INPUT_KEY_3:
            case INPUT_KEY_4:
            case INPUT_KEY_5:
            case INPUT_KEY_6:
            case INPUT_KEY_7:
            case INPUT_KEY_8:
            case INPUT_KEY_9:
            case INPUT_KEY_0:
                return handleDigit(action);

            case NAV_LEFT:
                return candidateBar != null && candidateBar.moveFocus(-1);
            case NAV_RIGHT:
                return candidateBar != null && candidateBar.moveFocus(1);
            case NAV_UP:
                return candidateBar != null && candidateBar.expandGrid(false);
            case NAV_DOWN:
                return candidateBar != null && candidateBar.expandGrid(true);
            case CONFIRM_SELECTION:
                return confirmSelection();

            case BACKSPACE_DELETE:
                return handleBackspace();

            case TOGGLE_LANG_MODE:
                cycleLanguageMode();
                return true;

            case SHOW_SYMBOL_PANEL:
                showSymbolPanel();
                return true;

            case SWITCH_RIME_SCHEMA:
                return false;

            default:
                return false;
        }
    }

    private boolean handleDigit(InputAction action) {
        int digit = action.digit();
        if (mode == InputMode.NUM) {
            if (digit >= 0 && digit <= 9) {
                commitChar((char) ('0' + digit));
                return true;
            }
            if (action == InputAction.INPUT_KEY_STAR) {
                commitChar('*');
                return true;
            }
            if (action == InputAction.INPUT_KEY_POUND) {
                commitChar('#');
                return true;
            }
            return false;
        }

        ImeEngine active = activeEngine();
        if (digit >= 2 && digit <= 9) {
            return active.processDigit(digit);
        }
        if (action == InputAction.INPUT_KEY_1) {
            if (active.candidateCount() > 0) {
                return active.selectCandidate(0);
            }
            commitChar('1');
            return true;
        }
        if (action == InputAction.INPUT_KEY_0) {
            if (active.candidateCount() > 0) {
                boolean ok = active.selectCandidate(0);
                if (ok) {
                    commitChar(' ');
                }
                return ok;
            }
            commitChar('0');
            return true;
        }
        if (action == InputAction.INPUT_KEY_STAR) {
            commitChar('*');
            return true;
        }
        if (action == InputAction.INPUT_KEY_POUND) {
            commitChar('#');
            return true;
        }
        return false;
    }

    private boolean handleBackspace() {
        ImeEngine active = activeEngine();
        if (active != null && active.backspace()) {
            return true;
        }
        android.view.inputmethod.InputConnection ic = getCurrentInputConnection();
        if (ic != null) {
            CharSequence sel = ic.getSelectedText(0);
            if (sel != null && sel.length() > 0) {
                ic.deleteSurroundingText(0, sel.length());
            } else {
                ic.deleteSurroundingText(1, 0);
            }
            return true;
        }
        return false;
    }

    private boolean confirmSelection() {
        if (candidateBar == null) {
            return false;
        }
        ImeEngine active = activeEngine();
        if (active == null) {
            return false;
        }
        int focus = candidateBar.getFocusIndex();
        return active.selectCandidate(focus);
    }

    private ImeEngine activeEngine() {
        switch (mode) {
            case ZH:
                return pinyinEngine;
            case ZH_MTAP:
                return zhMultiTapEngine;
            case EN:
                return englishEngine;
            case EN_MTAP:
                return enMultiTapEngine;
            case NUM:
            default:
                return null;
        }
    }

    private void flushMultiTapIfActive() {
        ImeEngine active = activeEngine();
        if (active instanceof MultiTapSupport) {
            ((MultiTapSupport) active).flushPending();
        }
    }

    /**
     * Cycle through the modes the user enabled in Input Settings (design doc
     * §1.1/§1.2), clearing any in-progress composing so stale candidates never
     * leak across modes.
     */
    private void cycleLanguageMode() {
        ImeEngine previous = activeEngine();
        if (previous != null) {
            previous.reset();
        }
        List<InputMode> loop = new GarahoPrefs(this).getModeLoop();
        int idx = loop.indexOf(mode);
        int nextIdx = (idx >= 0) ? (idx + 1) % loop.size() : 0;
        mode = loop.get(nextIdx);
        if (candidateBar != null) {
            candidateBar.setModeLabel(indicatorLabel());
            candidateBar.setCandidates(new String[0]);
            candidateBar.setComposingText("");
        }
        Log.i(TAG, "Input mode -> " + mode);
    }

    private void showSymbolPanel() {
        if (symbolPanel == null) {
            symbolPanel = new SymbolPanel(this, new SymbolPanel.OnSymbolPicked() {
                @Override
                public void onSymbolPicked(String symbol) {
                    commitTextToEditor(symbol);
                }
            });
        }
        symbolPanel.show();
    }

    private void commitChar(char c) {
        commitTextToEditor(String.valueOf(c));
    }

    private void commitTextToEditor(CharSequence text) {
        android.view.inputmethod.InputConnection ic = getCurrentInputConnection();
        if (ic != null) {
            ic.commitText(text, 1);
        }
    }

    @Override
    public void onComposingChanged(String composing) {
        if (candidateBar != null) {
            candidateBar.setComposingText(composing);
        }
    }

    @Override
    public void onCandidatesChanged(List<String> candidates) {
        Log.d(TAG, "onCandidatesChanged count=" + candidates.size());
        if (candidateBar != null) {
            candidateBar.setCandidates(candidates.toArray(new String[0]));
        }
    }

    @Override
    public void onCommit(String text) {
        commitTextToEditor(text);
    }

    /**
     * Safe Escape Hatch (design doc §5.2): long-press Backspace + '#' for 5s
     * resets {@code user_keymap.json} to the bundled factory preset.
     */
    private void checkSafeEscapeCombo() {
        if (keyMapper == null) {
            return;
        }
        boolean ok = keyMapper.resetToFactory();
        Log.w(TAG, "Safe-escape combo invoked; factory reset " + (ok ? "OK" : "FAILED"));
    }
}
