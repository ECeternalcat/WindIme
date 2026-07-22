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

    private boolean showModeBar = true;
    private String[] barLabels = null;
    private InputMode[] barModes = null;
    private int modeBarIndex = 0;
    private CharSequence currentComposing = "";

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

        com.garaho.ime.user.UserDictionary userDict = com.garaho.ime.user.UserDictionary.get(this);
        if (pinyinEngine instanceof com.garaho.ime.engine.T9PinyinEngine) {
            ((com.garaho.ime.engine.T9PinyinEngine) pinyinEngine).setUserWordSource(userDict);
        } else if (pinyinEngine instanceof com.garaho.ime.engine.RimeEngine) {
            ((com.garaho.ime.engine.RimeEngine) pinyinEngine).setUserWordSource(userDict);
        }
        zhMultiTapEngine.setUserWordSource(userDict);
    }

    @Override
    public View onCreateInputView() {
        LayoutInflater inflater = (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);
        rootContainer = new FrameLayout(this);
        // The input view must not steal focus from the host editor - if it
        // does, getCurrentInputConnection() can go null and commits silently
        // drop. Keep it non-focusable; key events still arrive via onKeyDown.
        rootContainer.setFocusable(false);
        rootContainer.setFocusableInTouchMode(false);
        candidateBar = (CandidateBar) inflater.inflate(R.layout.view_candidate_bar, rootContainer, false);
        candidateBar.setFocusable(false);
        candidateBar.setFocusableInTouchMode(false);
        candidateBar.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        rootContainer.addView(candidateBar,
                new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT));
        candidateBar.setModeLabel(indicatorLabel());
        enterModeBar();
        return rootContainer;
    }

    @Override
    public void onStartInputView(android.view.inputmethod.EditorInfo info, boolean restarting) {
        super.onStartInputView(info, restarting);
        enterModeBar();
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
        // Symbol/phrase panel is modal: while open, route every key to it.
        if (symbolPanel != null && symbolPanel.isShowing()) {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                symbolPanel.handleKey(keyCode);
            }
            return true;
        }
        // Two-stage BACK (doc §1 / iWnn quick-select): while composing, first
        // BACK cancels composing and returns to the mode bar; a second BACK
        // (when already on the mode bar) falls through to dismiss the IME.
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (!showModeBar) {
                enterModeBar();
                return true;
            }
            return false;
        }
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
        if (showModeBar) {
            return handleModeBarAction(action);
        }
        // Composing state: multi-tap engines keep a cycling letter; finalise it
        // before any unrelated action so the letter isn't lost. Cycling digits
        // (2-9) handle finalize internally; backspace cancels the pending letter.
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

    /** Key routing while the iWnn-style mode bar is up (composing empty). */
    private boolean handleModeBarAction(InputAction action) {
        switch (action) {
            case NAV_LEFT:
                moveModeBar(-1);
                return true;
            case NAV_RIGHT:
                moveModeBar(1);
                return true;
            case CONFIRM_SELECTION:
                return confirmModeBar();
            case TOGGLE_LANG_MODE:
                advanceModeBarToNextInputMode();
                return true;
            case SHOW_SYMBOL_PANEL:
                showSymbolPanel();
                return true;
            case BACKSPACE_DELETE:
                return false;
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
                return startTypingFromModeBar(action);
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
            // backspaced out of the whole composing -> back to the mode bar
            if (currentComposing.length() == 0) {
                enterModeBar();
            }
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
     * Build the mode-bar entries from the user's configured mode loop plus a
     * trailing 符 (symbol/phrase) entry. {@code barModes[i] == null} marks 符.
     */
    private void buildModeBar() {
        List<InputMode> loop = new GarahoPrefs(this).getModeLoop();
        barLabels = new String[loop.size() + 1];
        barModes = new InputMode[loop.size() + 1];
        for (int i = 0; i < loop.size(); i++) {
            barModes[i] = loop.get(i);
            barLabels[i] = loop.get(i).label();
        }
        barModes[loop.size()] = null;
        barLabels[loop.size()] = getString(R.string.mode_bar_symbol);
        modeBarIndex = 0;
        for (int i = 0; i < barModes.length; i++) {
            if (barModes[i] == mode) {
                modeBarIndex = i;
                break;
            }
        }
    }

    /** Show the mode bar (composing cleared / input started / BACK pressed). */
    private void enterModeBar() {
        buildModeBar();
        showModeBar = true;
        ImeEngine active = activeEngine();
        if (active != null) {
            active.reset();
        }
        currentComposing = "";
        if (candidateBar != null) {
            candidateBar.setCandidates(new String[0]);
            candidateBar.setComposingText("");
            candidateBar.showModeBar(true);
            candidateBar.setModeBar(barLabels, modeBarIndex);
        }
    }

    private void exitModeBar() {
        showModeBar = false;
        if (candidateBar != null) {
            candidateBar.showModeBar(false);
            candidateBar.setModeLabel(indicatorLabel());
        }
    }

    private void moveModeBar(int delta) {
        if (barLabels == null || barLabels.length == 0) {
            return;
        }
        modeBarIndex = (modeBarIndex + delta + barLabels.length) % barLabels.length;
        applyModeBarSelection();
    }

    /** Cycling onto an input mode activates it immediately; 符 only highlights. */
    private void applyModeBarSelection() {
        InputMode m = (barModes != null && modeBarIndex < barModes.length) ? barModes[modeBarIndex] : null;
        if (m != null && m != mode) {
            ImeEngine prev = activeEngine();
            if (prev != null) {
                prev.reset();
            }
            mode = m;
            currentComposing = "";
        }
        if (candidateBar != null) {
            candidateBar.setModeBar(barLabels, modeBarIndex);
        }
    }

    private boolean confirmModeBar() {
        if (barModes == null || modeBarIndex >= barModes.length) {
            return false;
        }
        if (barModes[modeBarIndex] == null) {
            showSymbolPanel();
        }
        return true;
    }

    /** TOGGLE advances to the next input mode, skipping the 符 entry. */
    private void advanceModeBarToNextInputMode() {
        if (barModes == null) {
            return;
        }
        for (int i = 1; i <= barModes.length; i++) {
            int idx = (modeBarIndex + i) % barModes.length;
            if (barModes[idx] != null) {
                modeBarIndex = idx;
                applyModeBarSelection();
                return;
            }
        }
    }

    /**
     * A digit was pressed on the mode bar: lock in the highlighted input mode
     * and begin composing (or, in NUM mode, commit the digit directly while
     * staying on the mode bar since NUM has no composing state).
     */
    private boolean startTypingFromModeBar(InputAction action) {
        InputMode m = (barModes != null && modeBarIndex < barModes.length) ? barModes[modeBarIndex] : null;
        if (m == null) {
            return false; // 符 highlighted -> ignore digit
        }
        mode = m;
        if (m == InputMode.NUM) {
            return handleDigit(action);
        }
        exitModeBar();
        return handleDigit(action);
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
        if (rootContainer == null) {
            return;
        }
        if (symbolPanel == null) {
            symbolPanel = new SymbolPanel(this, new SymbolPanel.OnSymbolPicked() {
                @Override
                public void onSymbolPicked(String symbol) {
                    commitTextToEditor(symbol);
                }
            });
        }
        symbolPanel.show(rootContainer);
    }

    private void commitChar(char c) {
        commitTextToEditor(String.valueOf(c));
    }

    private void commitTextToEditor(CharSequence text) {
        android.view.inputmethod.InputConnection ic = getCurrentInputConnection();
        if (ic == null) {
            Log.w(TAG, "commitText SKIPPED: InputConnection null (no focused editor) text=\"" + text + "\"");
            return;
        }
        boolean ok = ic.commitText(text, 1);
        Log.d(TAG, "commitText ok=" + ok + " text=\"" + text + "\"");
    }

    @Override
    public void onComposingChanged(CharSequence composing) {
        currentComposing = composing;
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
