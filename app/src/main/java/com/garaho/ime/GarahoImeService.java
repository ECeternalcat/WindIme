package com.garaho.ime;

import com.garaho.ime.engine.EngineListener;
import com.garaho.ime.engine.ImeEngine;
import com.garaho.ime.engine.RimeEngine;
import com.garaho.ime.engine.T9PinyinEngine;
import com.garaho.ime.keymap.InputAction;
import com.garaho.ime.keymap.KeyMapper;
import com.garaho.ime.rime.RimeData;
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
 * to obtain an {@link InputAction}, then drives either the active
 * {@link ImeEngine} (Phase-1 {@link T9PinyinEngine} or Phase-4
 * {@link RimeEngine}) and the 0-Touch focus UI ({@link CandidateBar} /
 * {@link SymbolPanel}).
 */
public class GarahoImeService extends InputMethodService implements EngineListener {

    private static final String TAG = "GarahoIme";
    private static final long RESET_COMBO_WINDOW_MS = 5000;

    private KeyMapper keyMapper;
    private ImeEngine engine;
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
                rimeData.getSharedDir(), rimeData.getUserDir(), "0.2.0");
        if (rimeEngine != null) {
            engine = rimeEngine;
            Log.i(TAG, "Engine: native librime (RimeEngine)");
        } else {
            engine = new T9PinyinEngine();
            Log.i(TAG, "Engine: built-in T9PinyinEngine");
        }
        engine.setListener(this);
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
        return rootContainer;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        InputAction action = keyMapper.resolve(keyCode, event.getScanCode());
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
                switchLanguage();
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
        int digit = action.ordinal() - InputAction.INPUT_KEY_0.ordinal();
        if (digit >= 2 && digit <= 9) {
            return engine.processDigit(digit);
        }
        if (action == InputAction.INPUT_KEY_1) {
            if (engine.candidateCount() > 0) {
                return engine.selectCandidate(0);
            }
            commitChar('1');
            return true;
        }
        if (action == InputAction.INPUT_KEY_0) {
            if (engine.candidateCount() > 0) {
                boolean ok = engine.selectCandidate(0);
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
        if (engine.backspace()) {
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
        int focus = candidateBar.getFocusIndex();
        return engine.selectCandidate(focus);
    }

    private void switchLanguage() {
        engine.reset();
        switchToNextInputMethod(false);
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
