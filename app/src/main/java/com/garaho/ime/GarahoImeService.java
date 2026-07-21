package com.garaho.ime;

import com.garaho.ime.keymap.InputAction;
import com.garaho.ime.keymap.KeyMapper;
import com.garaho.ime.rime.RimeBridge;
import com.garaho.ime.ui.CandidateBar;
import com.garaho.ime.ui.SymbolPanel;

import android.inputmethodservice.InputMethodService;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import java.io.File;

/**
 * Top-level IME entry point (design doc §2 layer 5).
 *
 * <p>Receives physical {@link KeyEvent}s, routes them through {@link KeyMapper}
 * to obtain an {@link InputAction}, then drives either the RIME core (librime.so
 * via {@link RimeBridge}) or the 0-Touch focus UI ({@link CandidateBar} /
 * {@link SymbolPanel}).
 */
public class GarahoImeService extends InputMethodService {

    private static final String TAG = "GarahoIme";
    private static final long RESET_COMBO_WINDOW_MS = 5000;

    private KeyMapper keyMapper;
    private CandidateBar candidateBar;
    private SymbolPanel symbolPanel;
    private FrameLayout rootContainer;

    private long backspaceDownAt;
    private long poundDownAt;

    @Override
    public void onCreate() {
        super.onCreate();
        keyMapper = new KeyMapper(this);
        File sharedDir = new File(getFilesDir(), "rime");
        File userDir = new File(getFilesDir(), "rime_user");
        if (!sharedDir.exists()) sharedDir.mkdirs();
        if (!userDir.exists()) userDir.mkdirs();
        RimeBridge.init(sharedDir.getAbsolutePath(), userDir.getAbsolutePath());
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
    public View onCreateCandidatesView() {
        return super.onCreateCandidatesView();
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
            case INPUT_KEY_0:
            case INPUT_KEY_1:
            case INPUT_KEY_2:
            case INPUT_KEY_3:
            case INPUT_KEY_4:
            case INPUT_KEY_5:
            case INPUT_KEY_6:
            case INPUT_KEY_7:
            case INPUT_KEY_8:
            case INPUT_KEY_9:
            case INPUT_KEY_STAR:
            case INPUT_KEY_POUND:
                return handleT9Key(action);

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

    private boolean handleT9Key(InputAction action) {
        int digit = action.ordinal() - InputAction.INPUT_KEY_1.ordinal();
        if (digit >= 0 && digit <= 8) {
            if (RimeBridge.isLoaded()) {
                RimeBridge.processKey('1' + digit, 0);
                refreshCandidates();
                return true;
            }
            commitChar((char) ('1' + digit));
            return true;
        }
        if (action == InputAction.INPUT_KEY_0) {
            if (RimeBridge.isLoaded()) {
                RimeBridge.processKey('0', 0);
                refreshCandidates();
                return true;
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
        InputConnectionExt conn = new InputConnectionExt(getCurrentInputConnection());
        CharSequence sel = conn.getSelectedText(0);
        if (!TextUtils.isEmpty(sel)) {
            conn.deleteSurroundingText(0, sel.length());
            return true;
        }
        conn.deleteSurroundingText(1, 0);
        return true;
    }

    private boolean confirmSelection() {
        if (candidateBar == null) {
            return false;
        }
        String word = candidateBar.consumeSelected();
        if (word == null) {
            return false;
        }
        InputConnectionExt conn = new InputConnectionExt(getCurrentInputConnection());
        conn.commitText(word, 1);
        return true;
    }

    private void switchLanguage() {
        switchToNextInputMethod(false);
    }

    private void showSymbolPanel() {
        if (symbolPanel == null) {
            symbolPanel = new SymbolPanel(this, new SymbolPanel.OnSymbolPicked() {
                @Override
                public void onSymbolPicked(String symbol) {
                    InputConnectionExt conn = new InputConnectionExt(getCurrentInputConnection());
                    conn.commitText(symbol, 1);
                }
            });
            symbolPanel.attachTo(rootContainer);
        }
        symbolPanel.show();
    }

    private void refreshCandidates() {
        if (candidateBar == null) {
            return;
        }
        String[] candidates = RimeBridge.getCandidates();
        candidateBar.setCandidates(candidates == null ? new String[0] : candidates);
    }

    private void commitChar(char c) {
        InputConnectionExt conn = new InputConnectionExt(getCurrentInputConnection());
        conn.commitText(String.valueOf(c), 1);
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

    private static final class InputConnectionExt {
        private final android.view.inputmethod.InputConnection ic;

        InputConnectionExt(android.view.inputmethod.InputConnection ic) {
            this.ic = ic;
        }

        CharSequence getSelectedText(int flags) {
            return ic == null ? null : ic.getSelectedText(flags);
        }

        void deleteSurroundingText(int before, int after) {
            if (ic != null) ic.deleteSurroundingText(before, after);
        }

        void commitText(CharSequence text, int newCursor) {
            if (ic != null) ic.commitText(text, newCursor);
        }
    }
}
