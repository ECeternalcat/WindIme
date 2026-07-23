package com.garaho.ime;

import com.garaho.ime.engine.ChineseMultiTapEngine;
import com.garaho.ime.engine.EngineListener;
import com.garaho.ime.engine.EnglishMultiTapEngine;
import com.garaho.ime.engine.EnglishT9Engine;
import com.garaho.ime.engine.ImeEngine;
import com.garaho.ime.engine.InputMode;
import com.garaho.ime.engine.LayeredPinyinEngine;
import com.garaho.ime.engine.MultiTapSupport;
import com.garaho.ime.engine.RimeEngine;
import com.garaho.ime.engine.T9PinyinEngine;
import com.garaho.ime.keymap.InputAction;
import com.garaho.ime.keymap.KeyMapper;
import com.garaho.ime.rime.RimeData;
import com.garaho.ime.rime.RimeMaintenance;
import com.garaho.ime.rime.RimeRuntimeStatus;
import com.garaho.ime.settings.GarahoPrefs;
import com.garaho.ime.ui.CandidateBar;
import com.garaho.ime.ui.SymbolPanel;

import android.inputmethodservice.InputMethodService;
import android.os.Handler;
import android.os.Looper;
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
    private static final long RIME_DEPLOY_TIMEOUT_MS = 30 * 60 * 1000L;

    private KeyMapper keyMapper;
    private ImeEngine pinyinEngine;
    private volatile RimeEngine pendingRimeEngine;
    private volatile boolean destroyed;
    private volatile Thread rimeInitThread;
    private EnglishT9Engine englishEngine;
    private ChineseMultiTapEngine zhMultiTapEngine;
    private EnglishMultiTapEngine enMultiTapEngine;
    private InputMode mode = InputMode.ZH;

    private CandidateBar candidateBar;
    private SymbolPanel symbolPanel;
    private FrameLayout rootContainer;

    private boolean showModeBar = true;
    private boolean inputSessionActive;
    private boolean inputViewActive;
    private String[] barLabels = null;
    private InputMode[] barModes = null;
    private int modeBarIndex = 0;
    private CharSequence currentComposing = "";

    private boolean backspaceHeld;
    private boolean poundHeld;
    private boolean consumeBoundBackKeyUp;
    private final Handler resetComboHandler = new Handler(Looper.getMainLooper());
    private final Runnable resetComboRunnable = new Runnable() {
        @Override
        public void run() {
            if (backspaceHeld && poundHeld) {
                checkSafeEscapeCombo();
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        keyMapper = new KeyMapper(this);

        // Keep the IME immediately usable while the large rime-ice source
        // dictionaries are extracted and compiled on a background thread.
        pinyinEngine = new T9PinyinEngine();
        Log.i(TAG, "Pinyin engine: built-in T9PinyinEngine (Rime preparing in background)");
        pinyinEngine.setListener(this);
        setRimeStatus(RimeRuntimeStatus.State.PREPARING, "正在准备雾凇词库");

        englishEngine = new EnglishT9Engine();
        englishEngine.setListener(this);

        GarahoPrefs prefs = new GarahoPrefs(this);
        zhMultiTapEngine = new ChineseMultiTapEngine(prefs);
        zhMultiTapEngine.setListener(this);
        enMultiTapEngine = new EnglishMultiTapEngine(prefs);
        enMultiTapEngine.setListener(this);

        com.garaho.ime.user.UserDictionary userDict = com.garaho.ime.user.UserDictionary.get(this);
        ((T9PinyinEngine) pinyinEngine).setUserWordSource(userDict);
        zhMultiTapEngine.setUserWordSource(userDict);
        prepareRimeInBackground(userDict);
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
        updateBackendStatus();
        enterModeBar();
        return rootContainer;
    }

    @Override
    public void onStartInput(android.view.inputmethod.EditorInfo attribute, boolean restarting) {
        super.onStartInput(attribute, restarting);
        keyMapper.reload();
        inputSessionActive = true;
        inputViewActive = false;
        showModeBar = true;
        Log.d(TAG, "onStartInput restarting=" + restarting);
    }

    @Override
    public void onStartInputView(android.view.inputmethod.EditorInfo info, boolean restarting) {
        super.onStartInputView(info, restarting);
        inputSessionActive = true;
        inputViewActive = true;
        adoptReadyRimeEngine();
        enterModeBar();
        Log.d(TAG, "onStartInputView restarting=" + restarting);
    }

    @Override
    public void onFinishInputView(boolean finishingInput) {
        inputViewActive = false;
        Log.d(TAG, "onFinishInputView finishingInput=" + finishingInput);
        super.onFinishInputView(finishingInput);
    }

    @Override
    public void onFinishInput() {
        inputViewActive = false;
        inputSessionActive = false;
        showModeBar = true;
        if (symbolPanel != null && symbolPanel.isShowing()) {
            symbolPanel.dismiss();
        }
        currentComposing = "";
        ImeEngine active = activeEngine();
        if (active != null) {
            active.reset();
        }
        Log.d(TAG, "onFinishInput");
        super.onFinishInput();
    }

    private void prepareRimeInBackground(final com.garaho.ime.user.UserWordSource userDict) {
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                RimeData data = new RimeData(GarahoImeService.this);
                if (RimeMaintenance.hasPending(GarahoImeService.this)
                        && RimeEngine.hasStartedInProcess()) {
                    setRimeStatus(RimeRuntimeStatus.State.PREPARING, "维护待进程重启执行");
                    Log.w(TAG, "Rime maintenance deferred until a fresh process starts");
                    return;
                }
                if (!RimeMaintenance.applyPending(GarahoImeService.this,
                        data.getSharedDir(), data.getUserDir())) {
                    setRimeStatus(RimeRuntimeStatus.State.FAILED, "维护操作失败");
                    return;
                }
                if (!data.ensureExtracted(GarahoImeService.this) || destroyed) {
                    Log.w(TAG, "rime-ice data unavailable; keeping Java T9 fallback");
                    if (!destroyed) {
                        setRimeStatus(RimeRuntimeStatus.State.FAILED, "词库解包失败");
                    }
                    return;
                }
                RimeEngine engine = RimeEngine.tryCreate(
                        data.getSharedDir(), data.getUserDir(), BuildConfig.VERSION_NAME);
                if (engine == null) {
                    setRimeStatus(RimeRuntimeStatus.State.FAILED, "native Rime 不可用");
                    Log.w(TAG, "rime-ice unavailable; keeping Java T9 fallback");
                    return;
                }
                if (!engine.awaitSchema("rime_ice", RIME_DEPLOY_TIMEOUT_MS)) {
                    if (!destroyed) {
                        setRimeStatus(RimeRuntimeStatus.State.FAILED, "schema 部署超时");
                    }
                    Log.w(TAG, "rime-ice unavailable; keeping Java T9 fallback");
                    return;
                }
                engine.setUserWordSource(userDict);
                engine.setListener(GarahoImeService.this);
                if (destroyed) {
                    return;
                }
                pendingRimeEngine = engine;
                setRimeStatus(RimeRuntimeStatus.State.PREPARING, "已就绪，下次输入启用");
                Log.i(TAG, "rime-ice ready; it will activate for the next input field");
            }
        }, "WindIme-RimeInit");
        rimeInitThread = thread;
        thread.start();
    }

    @Override
    public void onDestroy() {
        destroyed = true;
        pendingRimeEngine = null;
        resetComboHandler.removeCallbacks(resetComboRunnable);
        Thread thread = rimeInitThread;
        if (thread != null) {
            thread.interrupt();
        }
        super.onDestroy();
    }

    /** Swap engines only between input sessions so an in-progress word is never lost. */
    private void adoptReadyRimeEngine() {
        RimeEngine ready = pendingRimeEngine;
        if (ready == null) {
            return;
        }
        pendingRimeEngine = null;
        if (pinyinEngine != null) {
            pinyinEngine.reset();
        }
        pinyinEngine = ready;
        setRimeStatus(RimeRuntimeStatus.State.READY, "雾凇词库已启用");
        Log.i(TAG, "Pinyin engine -> native rime-ice");
    }

    private void setRimeStatus(final RimeRuntimeStatus.State state, String detail) {
        RimeRuntimeStatus.set(this, state, detail);
        resetComboHandler.post(new Runnable() {
            @Override
            public void run() {
                updateBackendStatus();
            }
        });
    }

    private void updateBackendStatus() {
        if (candidateBar == null) {
            return;
        }
        RimeRuntimeStatus.State state = RimeRuntimeStatus.get(this).state;
        switch (state) {
            case PREPARING:
                candidateBar.setBackendStatus(getString(R.string.rime_status_preparing_short));
                break;
            case READY:
                candidateBar.setBackendStatus(getString(R.string.rime_status_ready_short));
                break;
            case FAILED:
                candidateBar.setBackendStatus(getString(R.string.rime_status_failed_short));
                break;
            case LIGHTWEIGHT:
            default:
                candidateBar.setBackendStatus(getString(R.string.rime_status_lightweight_short));
                break;
        }
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
        // InputMethodService can receive a late hardware event after the input
        // view was hidden. Never resolve or consume it outside an active input
        // view, otherwise ENTER/DPAD keys can appear dead in the host UI.
        if (!InputEventGate.accepts(inputSessionActive, inputViewActive)) {
            return false;
        }
        // Symbol/phrase panel is modal: while open, route every key to it.
        if (symbolPanel != null && symbolPanel.isShowing()) {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                symbolPanel.handleKey(keyCode);
            }
            return true;
        }
        InputAction action = keyMapper.resolve(keyCode, event.getScanCode());
        if (action != InputAction.NONE) {
            Log.d(TAG, "onKeyDown keyCode=" + keyCode + " scan=" + event.getScanCode() + " -> " + action + " (mode=" + mode + ")");
        }
        // Some Japanese flip phones share BACK and backspace. Respect an
        // explicit user mapping: delete composition/editor text first, then
        // hide the IME when there is nothing left to delete. Once hidden, the
        // lifecycle gate releases the next BACK press to the host system.
        if (keyCode == KeyEvent.KEYCODE_BACK && action == InputAction.BACKSPACE_DELETE) {
            consumeBoundBackKeyUp = true;
            trackResetComboDown(action, event);
            return handleBoundBackKey();
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
        if (action == InputAction.BACKSPACE_DELETE) {
            trackResetComboDown(action, event);
        } else if (action == InputAction.INPUT_KEY_POUND) {
            trackResetComboDown(action, event);
        }
        return handleAction(action) || super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && consumeBoundBackKeyUp) {
            consumeBoundBackKeyUp = false;
            trackResetComboUp(InputAction.BACKSPACE_DELETE);
            return true;
        }
        InputAction action = keyMapper.resolve(keyCode, event.getScanCode());
        trackResetComboUp(action);
        return super.onKeyUp(keyCode, event);
    }

    @Override
    public boolean onKeyLongPress(int keyCode, KeyEvent event) {
        return super.onKeyLongPress(keyCode, event);
    }

    private void trackResetComboDown(InputAction action, KeyEvent event) {
        if (event.getRepeatCount() != 0) {
            return;
        }
        if (action == InputAction.BACKSPACE_DELETE) {
            backspaceHeld = true;
        } else if (action == InputAction.INPUT_KEY_POUND) {
            poundHeld = true;
        }
        if (backspaceHeld && poundHeld) {
            resetComboHandler.removeCallbacks(resetComboRunnable);
            resetComboHandler.postDelayed(resetComboRunnable, RESET_COMBO_WINDOW_MS);
        }
    }

    private void trackResetComboUp(InputAction action) {
        if (action == InputAction.BACKSPACE_DELETE) {
            backspaceHeld = false;
        } else if (action == InputAction.INPUT_KEY_POUND) {
            poundHeld = false;
        }
        if (!backspaceHeld || !poundHeld) {
            resetComboHandler.removeCallbacks(resetComboRunnable);
        }
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
                return moveLayerFocus(-1);
            case NAV_RIGHT:
                return moveLayerFocus(1);
            case NAV_UP:
                if (candidateBar != null) {
                    ImeEngine upEngine = activeEngine();
                    if (upEngine instanceof LayeredPinyinEngine) {
                        candidateBar.moveLayer(false);
                    } else {
                        candidateBar.expandGrid(false);
                    }
                    return true;
                }
                return false;
            case NAV_DOWN:
                if (candidateBar != null) {
                    ImeEngine downEngine = activeEngine();
                    if (downEngine instanceof LayeredPinyinEngine) {
                        candidateBar.moveLayer(true);
                    } else {
                        candidateBar.expandGrid(true);
                    }
                    return true;
                }
                return false;
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
                return deleteFromEditor();
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
            boolean processed = active.processDigit(digit);
            if (processed && active instanceof LayeredPinyinEngine && candidateBar != null) {
                candidateBar.activatePinyinLayer();
            }
            return processed;
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
            if (active instanceof LayeredPinyinEngine && candidateBar != null) {
                candidateBar.activatePinyinLayer();
            }
            return true;
        }
        return deleteFromEditor();
    }

    private boolean handleBoundBackKey() {
        ImeEngine active = activeEngine();
        if (active != null && active.backspace()) {
            if (active instanceof LayeredPinyinEngine && candidateBar != null) {
                candidateBar.activatePinyinLayer();
            }
            return true;
        }
        boolean deleted = deleteFromEditorIfPossible();
        if (!BoundBackKeyPolicy.shouldHideIme(deleted, editorIsKnownEmpty())) {
            return true;
        }
        inputViewActive = false;
        showModeBar = true;
        requestHideSelf(0);
        Log.d(TAG, "Mapped BACK found nothing to delete; hiding IME");
        return true;
    }

    /** Delete one char (or the active selection) from the editor via the InputConnection. */
    private boolean deleteFromEditor() {
        android.view.inputmethod.InputConnection ic = getCurrentInputConnection();
        if (ic == null) {
            return false;
        }
        CharSequence sel = ic.getSelectedText(0);
        if (sel != null && sel.length() > 0) {
            ic.commitText("", 1);
        } else {
            ic.deleteSurroundingText(1, 0);
        }
        return true;
    }

    /** Delete only when a selection or a character before the cursor exists. */
    private boolean deleteFromEditorIfPossible() {
        android.view.inputmethod.InputConnection ic = getCurrentInputConnection();
        if (ic == null) {
            return false;
        }
        CharSequence selected = ic.getSelectedText(0);
        if (selected != null && selected.length() > 0) {
            return ic.commitText("", 1);
        }
        CharSequence before = ic.getTextBeforeCursor(1, 0);
        if (before == null || before.length() == 0) {
            return false;
        }
        return ic.deleteSurroundingText(1, 0);
    }

    private boolean editorIsKnownEmpty() {
        android.view.inputmethod.InputConnection ic = getCurrentInputConnection();
        if (ic == null) {
            return false;
        }
        CharSequence selected = ic.getSelectedText(0);
        if (selected != null && selected.length() > 0) {
            return false;
        }
        CharSequence before = ic.getTextBeforeCursor(1, 0);
        if (before != null && before.length() > 0) {
            return false;
        }
        CharSequence after = ic.getTextAfterCursor(1, 0);
        return before != null && after != null && after.length() == 0;
    }

    private boolean confirmSelection() {
        if (candidateBar == null) {
            return false;
        }
        ImeEngine active = activeEngine();
        if (active == null) {
            return false;
        }
        if (candidateBar.getActiveLayer() == CandidateBar.InputLayer.PINYIN
                && active instanceof LayeredPinyinEngine) {
            LayeredPinyinEngine layered = (LayeredPinyinEngine) active;
            int index = candidateBar.getPinyinFocusIndex();
            if (index < 0 || index >= layered.getPinyinOptions().size()) {
                return false;
            }
            boolean confirmed = layered.confirmPinyinOption(index);
            if (confirmed) {
                candidateBar.activateCandidateLayer();
            }
            // Partial letter options are selectable previews but cannot lock a
            // syllable yet. Consume OK so ENTER never leaks into the editor.
            return true;
        }
        if (active instanceof LayeredPinyinEngine && active.candidateCount() == 0) {
            return true;
        }
        int focus = candidateBar.getFocusIndex();
        return active.selectCandidate(focus);
    }

    private boolean moveLayerFocus(int delta) {
        if (candidateBar == null) {
            return false;
        }
        ImeEngine active = activeEngine();
        if (!candidateBar.moveFocus(delta)) {
            // Layered navigation is modal: do not let a boundary key move the
            // host editor cursor. Other modes retain their previous behavior.
            return active instanceof LayeredPinyinEngine;
        }
        if (candidateBar.getActiveLayer() == CandidateBar.InputLayer.PINYIN
                && active instanceof LayeredPinyinEngine) {
            candidateBar.resetCandidateFocus();
            return ((LayeredPinyinEngine) active)
                    .previewPinyinOption(candidateBar.getPinyinFocusIndex());
        }
        return true;
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
            candidateBar.setPinyinOptions(new String[0], -1);
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
            candidateBar.setPinyinOptions(new String[0], -1);
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
            ImeEngine active = activeEngine();
            if (active instanceof LayeredPinyinEngine) {
                LayeredPinyinEngine layered = (LayeredPinyinEngine) active;
                candidateBar.setPinyinOptions(
                        layered.getPinyinOptions().toArray(new String[0]),
                        layered.getSelectedPinyinIndex());
            } else {
                candidateBar.setPinyinOptions(new String[0], -1);
            }
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
