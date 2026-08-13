package com.garaho.ime;

import com.garaho.ime.compat.SoftkeyGuideHelper;
import com.garaho.ime.engine.ChineseMultiTapEngine;
import com.garaho.ime.engine.EngineListener;
import com.garaho.ime.engine.EnglishCapitalization;
import com.garaho.ime.engine.EnglishMultiTapEngine;
import com.garaho.ime.engine.EnglishT9Engine;
import com.garaho.ime.engine.ImeEngine;
import com.garaho.ime.engine.InputMode;
import com.garaho.ime.engine.LayeredPinyinEngine;
import com.garaho.ime.engine.MultiTapSupport;
import com.garaho.ime.engine.RimeEngine;
import com.garaho.ime.engine.T9PinyinEngine;
import com.garaho.ime.feedback.KeyFeedback;
import com.garaho.ime.keymap.InputAction;
import com.garaho.ime.keymap.KeyMapper;
import com.garaho.ime.keymap.KeymapSlots;
import com.garaho.ime.rime.RimeData;
import com.garaho.ime.rime.RimeLifecycle;
import com.garaho.ime.rime.RimeMaintenance;
import com.garaho.ime.rime.RimeRuntimeStatus;
import com.garaho.ime.settings.GarahoPrefs;
import com.garaho.ime.settings.KeymapProfilesActivity;
import com.garaho.ime.ui.CandidateBar;
import com.garaho.ime.ui.QuickMenuPanel;
import com.garaho.ime.ui.SettingsActivity;
import com.garaho.ime.ui.SetupWizardActivity;
import com.garaho.ime.ui.SymbolPanel;

import android.app.AlertDialog;
import android.inputmethodservice.InputMethodService;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.FrameLayout;
import android.widget.Toast;

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
    private static final long RIME_RETRY_DELAY_MS = 3000L;
    private static final int RIME_MAX_RETRIES = 20;
    private static final long READY_FLASH_MS = 500L;
    private static final int CAPITALIZE_LOOKBACK = 16;
    private static final String RIME_SCHEMA_ID = "rime_ice";

    private KeyMapper keyMapper;
    private GarahoPrefs prefs;
    private KeyFeedback keyFeedback;
    private SoftkeyGuideHelper softkeyGuide;
    private ImeEngine pinyinEngine;
    private volatile RimeEngine pendingRimeEngine;
    private volatile boolean destroyed;
    private volatile Thread rimeInitThread;
    private int rimeInitRetryCount;
    private EnglishT9Engine englishEngine;
    private ChineseMultiTapEngine zhMultiTapEngine;
    private EnglishMultiTapEngine enMultiTapEngine;
    private InputMode mode = InputMode.ZH;

    private CandidateBar candidateBar;
    private SymbolPanel symbolPanel;
    private QuickMenuPanel quickMenuPanel;
    private FrameLayout rootContainer;

    private boolean showModeBar = true;
    private boolean inputSessionActive;
    private boolean inputViewActive;
    private boolean privateInput;
    private boolean privateDirectDigits;
    /** Password-field mode: false = English multi-tap, true = direct digits. */
    private boolean privateNumericMode;
    private boolean privateUppercase;
    private int expectedPrivateSelectionDelta = -1;
    private boolean suppressEngineCallbacks;
    private final PrivateMultiTapState privateMultiTapState = new PrivateMultiTapState();
    private boolean keymapPromptShown;
    private String[] barLabels = null;
    private InputMode[] barModes = null;
    private int modeBarIndex = 0;
    private CharSequence currentComposing = "";
    /** Package name of the current host editor (set in onStartInput). */
    private String currentHostPackage;

    private boolean backspaceHeld;
    private boolean poundHeld;
    private boolean consumeBoundBackKeyUp;
    private boolean consumeMenuKeyUp;
    /**
     * Session-level gate for the iWnn-compatible "active finish" protocol.
     * Set to {@code true} when the user presses center OK to complete input;
     * read and reset in {@link #onFinishInputView} to build the
     * {@code Finish_IME} bundle. Prevents ordinary focus switches or BACK
     * from accidentally sending {@code isActiveFinish=true}.
     */
    private boolean activeFinish;
    private boolean quickMenuShowingKeymaps;
    private boolean quickMenuShowingModes;
    private int[] quickMenuKeymapSlots = new int[0];
    private final InputMode[] quickMenuModes = {
            InputMode.ZH,
            InputMode.ZH_MTAP,
            InputMode.EN,
            InputMode.EN_MTAP,
            InputMode.NUM,
    };
    private final Handler resetComboHandler = new Handler(Looper.getMainLooper());
    private final Runnable hideBackendStatusRunnable = new Runnable() {
        @Override
        public void run() {
            if (candidateBar != null) {
                candidateBar.setBackendStatus(null);
            }
        }
    };
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
        setRimeStatus(RimeRuntimeStatus.State.PREPARING, "正在准备标准词库");

        englishEngine = new EnglishT9Engine();
        englishEngine.setListener(this);

        prefs = new GarahoPrefs(this);
        keyFeedback = new KeyFeedback(this);
        keyFeedback.setMode(prefs.getFeedback());
        softkeyGuide = SoftkeyGuideHelper.create(this);
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
    public boolean onEvaluateFullscreenMode() {
        // Password fields on flip phones are easier to use in the native
        // extract/fullscreen layout, just like the vendor IME.
        if (privateInput) {
            return true;
        }
        // Only opt into the framework's fullscreen/extract layout for host
        // packages whose own EditText the framework blanks in extract mode (so
        // the native ExtractEditText is needed to show the text + cursor, like
        // iWnn). For every other app, return false so its normal field stays
        // visible and the user can move the cursor with the d-pad. The package
        // list is user-configurable (设置 → 输入设定 → 全屏兼容列表);
        // default = Notepad (np701kc.md §15).
        if (currentHostPackage == null || prefs == null) {
            return false;
        }
        return prefs.isFullscreenCompatPackage(currentHostPackage);
    }

    @Override
    public View onCreateInputView() {        rootContainer = new FrameLayout(this);
        // The input view must not steal focus from the host editor - if it
        // does, getCurrentInputConnection() can go null and commits silently
        // drop. Keep it non-focusable; key events still arrive via onKeyDown.
        rootContainer.setFocusable(false);
        rootContainer.setFocusableInTouchMode(false);
        buildInputView();
        return rootContainer;
    }

    /**
     * (Re)build the compact candidate-strip input view. The framework wraps it
     * below its native {@code ExtractEditText} when in fullscreen/extract mode
     * (np701kc.md §15). Called from {@link #onCreateInputView()}.
     */
    private void buildInputView() {
        // Detach any modal panels first: their overlay views are children of
        // rootContainer and would be orphaned by removeAllViews.
        if (symbolPanel != null && symbolPanel.isShowing()) {
            symbolPanel.dismiss();
        }
        if (quickMenuPanel != null && quickMenuPanel.isShowing()) {
            quickMenuPanel.dismiss();
        }
        symbolPanel = null;
        quickMenuPanel = null;
        rootContainer.removeAllViews();

        LayoutInflater inflater = (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);
        candidateBar = (CandidateBar) inflater.inflate(R.layout.view_candidate_bar, rootContainer, false);
        candidateBar.setFocusable(false);
        candidateBar.setFocusableInTouchMode(false);
        candidateBar.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);

        // Compact candidate strip only. The framework wraps this view below its
        // native ExtractEditText when in fullscreen/extract mode (the editable
        // field text + cursor), giving the iWnn-style behaviour. We never build
        // a custom text mirror (np701kc.md §15).
        rootContainer.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        rootContainer.setMinimumHeight(0);
        rootContainer.addView(candidateBar, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        candidateBar.setModeLabel(indicatorLabel());
        updateBackendStatus();
        enterModeBar();
    }

    @Override
    public void onStartInput(android.view.inputmethod.EditorInfo attribute, boolean restarting) {
        if (!restarting) {
            // Some hosts start the next editor without a reliable finish callback.
            // Cancel while getCurrentInputConnection() still identifies the old field.
            clearAllEngineState();
        }
        super.onStartInput(attribute, restarting);
        keyMapper.reload();
        if (keyFeedback != null && prefs != null) {
            keyFeedback.setMode(prefs.getFeedback());
        }
        inputSessionActive = true;
        inputViewActive = false;
        showModeBar = true;
        currentHostPackage = attribute != null ? attribute.packageName : null;
        boolean wasPrivateInput = privateInput;
        privateInput = attribute != null
                && PrivateInputPolicy.isPrivateField(attribute.inputType, attribute.imeOptions);
        privateDirectDigits = privateInput && PrivateInputPolicy.usesDirectDigits(attribute.inputType);
        privateNumericMode = privateDirectDigits;
        privateUppercase = false;
        if (privateInput || wasPrivateInput) {
            clearAllEngineState();
        }
        if (privateInput) {
            showModeBar = true;
            dismissPrivatePanels();
            clearCandidateUi();
        }
        if (prefs != null) {
            prefs.setLastHostPackage(currentHostPackage);
        }
        if (attribute != null) {
            int actionId = attribute.imeOptions & 0x000000ff;
            Log.d(TAG, "onStartInput restarting=" + restarting
                    + " private=" + privateInput
                    + " actionId=" + actionId
                    + " packageName=" + attribute.packageName);
        }
    }

    @Override
    public void onStartInputView(android.view.inputmethod.EditorInfo info, boolean restarting) {
        super.onStartInputView(info, restarting);
        inputSessionActive = true;
        inputViewActive = true;
        activeFinish = false;
        adoptReadyRimeEngine();
        if (privateInput) {
            showModeBar = true;
            dismissPrivatePanels();
            updatePrivateModeBar();
        } else {
            enterModeBar();
        }
        if (!restarting) {
            maybePromptKeymapSetup();
        }
        refreshSoftkeyGuide();
        // iWnn lifecycle notification: unknown hosts ignore this.
        android.view.inputmethod.InputConnection ic = getCurrentInputConnection();
        if (ic != null) {
            try {
                ic.performPrivateCommand("Start_IME", null);
            } catch (Throwable ignored) {
            }
        }
        Log.d(TAG, "onStartInputView restarting=" + restarting);
    }

    @Override
    public void onUpdateSelection(int oldSelStart, int oldSelEnd, int newSelStart, int newSelEnd,
            int candidatesStart, int candidatesEnd) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd,
                candidatesStart, candidatesEnd);
        if (privateInput && privateMultiTapState != null) {
            boolean expected = expectedPrivateSelectionDelta >= 0
                    && oldSelStart == oldSelEnd
                    && newSelStart == newSelEnd
                    && newSelStart == oldSelStart + expectedPrivateSelectionDelta;
            expectedPrivateSelectionDelta = -1;
            if (!expected) {
                privateMultiTapState.breakCycle();
            }
        }
        // Cursor moved in the host editor; the native ExtractEditText mirrors it
        // automatically, so nothing to do here.
    }

    @Override
    public void onFinishInputView(boolean finishingInput) {
        inputViewActive = false;
        // iWnn lifecycle notification: send Finish_IME with the active-finish
        // flag so vendor hosts (Mail, MemoPad, etc.) can distinguish a
        // user-initiated center-OK completion from an ordinary focus switch.
        android.view.inputmethod.InputConnection ic = getCurrentInputConnection();
        if (ic != null) {
            try {
                android.os.Bundle bundle = new android.os.Bundle();
                bundle.putBoolean("isActiveFinish", activeFinish);
                ic.performPrivateCommand("Finish_IME", bundle);
            } catch (Throwable ignored) {
            }
        }
        activeFinish = false;
        // Clear any vendor Softkey Guide label so it does not leak to the next window.
        if (softkeyGuide != null) {
            refreshSoftkeyGuide();
        }
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
        if (quickMenuPanel != null && quickMenuPanel.isShowing()) {
            quickMenuPanel.dismiss();
        }
        clearAllEngineState();
        Log.d(TAG, "onFinishInput");
        super.onFinishInput();
    }

    /**
     * On the very first input session (user just enabled the IME and tapped an
     * editor), prompt to create a keymap configuration if none exists yet.
     * Conditions: no user slot configured AND prompt not previously dismissed.
     */
    private void maybePromptKeymapSetup() {
        if (prefs == null || keyMapper == null) {
            return;
        }
        // Show at most once per process; the activity's buttons persist the
        // "dismissed" flag so a "later"/"create" choice never re-prompts.
        if (keymapPromptShown || prefs.isKeymapPromptDismissed()) {
            return;
        }
        if (keyMapper.hasAnyUserSlot()) {
            return;
        }
        keymapPromptShown = true;
        // Hosted by a transparent Activity: a service-shown dialog would need
        // SYSTEM_ALERT_WINDOW (and crash with BadTokenException without it).
        startActivity(new android.content.Intent(
                this, com.garaho.ime.settings.KeymapPromptActivity.class)
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));
    }

    /**
     * Sole owner of native Rime initialization (improvement doc §7). Runs on a
     * background thread, assigns a monotonic session id for structured logging,
     * and is serialized process-wide by {@link RimeLifecycle} so a Service
     * recreate or concurrent retry cannot start a second maintenance session or
     * call {@code startupRime} twice. On any failure it leaves the pure-Java
     * T9 fallback active; the next fresh process start retries automatically.
     *
     * <p>The Runnable captures only a {@link java.lang.ref.WeakReference} to the
     * Service and the application Context, so a long-running deploy (up to 30
     * min) never prevents the Service from being GC'd (audit H-3).
     */
    private void prepareRimeInBackground(final com.garaho.ime.user.UserWordSource userDict) {
        final int sid = RimeLifecycle.nextSessionId();
        if (!RimeLifecycle.beginSession()) {
            // Another init session is already running in this process (e.g.
            // the thread from a previous Service instance is still winding
            // down). Schedule a delayed retry so this instance eventually
            // acquires the session slot once the old thread calls endSession().
            Log.i(TAG, RimeLifecycle.format(sid, "init-deferred", "another session running; will retry"));
            setRimeStatus(RimeRuntimeStatus.State.PREPARING, "部署进行中");
            scheduleRimeInitRetry(userDict);
            return;
        }
        startRimeInitThread(sid, userDict);
    }

    /**
     * Poll every {@link #RIME_RETRY_DELAY_MS} until the process-wide session
     * slot is free, then kick off the normal init thread. Gives up after
     * {@link #RIME_MAX_RETRIES} attempts (~60 s) to avoid an infinite loop.
     */
    private void scheduleRimeInitRetry(final com.garaho.ime.user.UserWordSource userDict) {
        if (destroyed || rimeInitRetryCount >= RIME_MAX_RETRIES) {
            return;
        }
        rimeInitRetryCount++;
        resetComboHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (destroyed) {
                    return;
                }
                int sid = RimeLifecycle.nextSessionId();
                if (!RimeLifecycle.beginSession()) {
                    // Old thread still holds the slot (may be in awaitSchema
                    // or winding down). Keep polling.
                    rlog(sid, "retry-waiting", "attempt " + rimeInitRetryCount);
                    scheduleRimeInitRetry(userDict);
                    return;
                }
                rlog(sid, "retry-acquired", "attempt " + rimeInitRetryCount);
                startRimeInitThread(sid, userDict);
            }
        }, RIME_RETRY_DELAY_MS);
    }

    private void startRimeInitThread(final int sid, final com.garaho.ime.user.UserWordSource userDict) {
        final java.lang.ref.WeakReference<GarahoImeService> selfRef =
                new java.lang.ref.WeakReference<>(this);
        final android.content.Context appContext = getApplicationContext();
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    GarahoImeService self = selfRef.get();
                    if (self == null || self.destroyed) {
                        return;
                    }
                    if (RimeEngine.hasStartedInProcess()) {
                        // Native Rime is already initialized in this process
                        // (Service recreated). Reuse it; if the old Service was
                        // interrupted during deployment, continue probing instead
                        // of publishing early or calling startupRime again.
                        rlog(sid, "reattach", "native already started");
                        if (RimeMaintenance.hasPending(appContext)) {
                            rlog(sid, "maintenance-deferred", "native running; wait for fresh process");
                        }
                        RimeEngine engine = RimeEngine.tryReattach(RIME_SCHEMA_ID);
                        if (engine == null) {
                            self = selfRef.get();
                            if (self != null && !self.destroyed) {
                                self.setRimeStatus(RimeRuntimeStatus.State.FAILED, "重新挂载失败");
                            }
                            rlog(sid, "reattach-failed", "keeping Java T9 fallback");
                            return;
                        }
                        if (RimeLifecycle.getNativeState()
                                == RimeLifecycle.NativeState.DEPLOYING) {
                            rlog(sid, "await-schema", RIME_SCHEMA_ID + " (reattach)");
                            if (!engine.awaitSchema(RIME_SCHEMA_ID, RIME_DEPLOY_TIMEOUT_MS)) {
                                self = selfRef.get();
                                if (self != null && !self.destroyed) {
                                    self.setRimeStatus(RimeRuntimeStatus.State.FAILED,
                                            "schema 部署超时");
                                }
                                rlog(sid, "schema-timeout", "keeping Java T9 fallback");
                                return;
                            }
                            rlog(sid, "schema-ready", RIME_SCHEMA_ID + " (reattach)");
                        }
                        self = selfRef.get();
                        if (self != null && !self.destroyed) {
                            self.attachReadyRime(engine, userDict, sid, "标准词库已就绪（重新挂载）");
                        }
                        return;
                    }
                    RimeData data = new RimeData(appContext);
                    if (!RimeMaintenance.applyPending(appContext,
                            data.getSharedDir(), data.getUserDir())) {
                        self = selfRef.get();
                        if (self != null && !self.destroyed) {
                            self.setRimeStatus(RimeRuntimeStatus.State.FAILED, "维护操作失败");
                        }
                        rlog(sid, "maintenance-failed", "");
                        return;
                    }
                    if (!data.ensureExtracted(appContext)) {
                        self = selfRef.get();
                        if (self != null && !self.destroyed) {
                            self.setRimeStatus(RimeRuntimeStatus.State.FAILED, "词库解包失败");
                        }
                        rlog(sid, "extract-failed", "keeping Java T9 fallback");
                        return;
                    }
                    self = selfRef.get();
                    if (self == null || self.destroyed) {
                        rlog(sid, "extract-failed", "service destroyed after extract");
                        return;
                    }
                    rlog(sid, "startup", "begin native init");
                    RimeEngine engine = RimeEngine.tryCreate(
                            data.getSharedDir(), data.getUserDir(), BuildConfig.VERSION_NAME);
                    if (engine == null) {
                        self = selfRef.get();
                        if (self != null && !self.destroyed) {
                            self.setRimeStatus(RimeRuntimeStatus.State.FAILED, "native Rime 不可用");
                        }
                        rlog(sid, "startup-failed", "keeping Java T9 fallback");
                        return;
                    }
                    rlog(sid, "await-schema", RIME_SCHEMA_ID);
                    if (!engine.awaitSchema(RIME_SCHEMA_ID, RIME_DEPLOY_TIMEOUT_MS)) {
                        self = selfRef.get();
                        if (self != null && !self.destroyed) {
                            self.setRimeStatus(RimeRuntimeStatus.State.FAILED, "schema 部署超时");
                        }
                        rlog(sid, "schema-timeout", "keeping Java T9 fallback");
                        return;
                    }
                    // Safe point: deployment finished, no background work in flight.
                    // Note: syncUserData is deferred to the settings/maintenance
                    // page; calling it here on first install triggers a harmless
                    // but confusing ".temp" error when no user dicts exist yet.
                    rlog(sid, "schema-ready", RIME_SCHEMA_ID);
                    self = selfRef.get();
                    if (self != null && !self.destroyed) {
                        self.attachReadyRime(engine, userDict, sid, "标准词库已就绪");
                    }
                } finally {
                    RimeLifecycle.endSession();
                }
            }
        }, "WindIme-RimeInit");
        rimeInitThread = thread;
        thread.start();
    }

    private void attachReadyRime(RimeEngine engine,
                                 com.garaho.ime.user.UserWordSource userDict,
                                 int sid, String detail) {
        engine.setUserWordSource(userDict);
        engine.setListener(GarahoImeService.this);
        if (destroyed) {
            rlog(sid, "abandoned", "service destroyed before attach");
            return;
        }
        pendingRimeEngine = engine;
        setRimeStatus(RimeRuntimeStatus.State.READY, detail);
        rlog(sid, "ready", detail);
        // If the input view is currently visible, adopt immediately on the
        // main thread instead of waiting for the next onStartInputView (which
        // may never come if the user does not re-focus a text field).
        resetComboHandler.post(new Runnable() {
            @Override
            public void run() {
                adoptReadyRimeEngine();
            }
        });
    }

    private static void rlog(int sid, String event, String detail) {
        Log.i(TAG, RimeLifecycle.format(sid, event, detail));
    }

    @Override
    public void onDestroy() {
        destroyed = true;
        pendingRimeEngine = null;
        resetComboHandler.removeCallbacks(resetComboRunnable);
        resetComboHandler.removeCallbacksAndMessages(null);
        // Reset all engines to remove any pending Multi-tap Handler callbacks,
        // preventing a short-lived leak of the Service via the timeoutRunnable
        // (audit M-2).
        if (pinyinEngine != null) pinyinEngine.reset();
        if (englishEngine != null) englishEngine.reset();
        if (zhMultiTapEngine != null) zhMultiTapEngine.reset();
        if (enMultiTapEngine != null) enMultiTapEngine.reset();
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
        setRimeStatus(RimeRuntimeStatus.State.READY, "标准词库已启用");
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
                // READY is shown as a brief flash when typing starts, not
                // permanently. Hide the line here so it does not linger.
                candidateBar.setBackendStatus(null);
                break;
            case FAILED:
                // Keep the failure visible: the bundled lightweight fallback
                // still works, but the user should know that Rime/雾凇 did not
                // finish preparing and may need a redeploy from settings.
                candidateBar.setBackendStatus(getString(R.string.rime_status_failed_short));
                break;
            case LIGHTWEIGHT:
            default:
                // Hide the persistent engine-status line in the common case so
                // the idle candidate strip stays a single row. On flip phones
                // the IME input-view height becomes the host editor's bottom
                // inset (np701kc.md: a 2-row strip inset 132px and hid the
                // memo field's cursor/text, while the system iWnn IME's 1-row
                // 72px strip left the field visible). Only surface the status
                // for transient/error states that the user must see.
                candidateBar.setBackendStatus(null);
                break;
        }
    }

    /**
     * Briefly flash "使用标准词库" for {@link #READY_FLASH_MS} when the user
     * starts typing in a session where the native Rime engine is active.
     */
    private void flashReadyStatus() {
        if (candidateBar == null) {
            return;
        }
        if (RimeRuntimeStatus.get(this).state != RimeRuntimeStatus.State.READY) {
            return;
        }
        resetComboHandler.removeCallbacks(hideBackendStatusRunnable);
        candidateBar.setBackendStatus(getString(R.string.rime_status_ready_short));
        resetComboHandler.postDelayed(hideBackendStatusRunnable, READY_FLASH_MS);
    }

    /** Mode label for the candidate strip, empty when the user hides the indicator. */
    private String indicatorLabel() {
        if (privateInput) {
            return "";
        }
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
        super.onEvaluateInputViewShown();
        return true;
    }

    /**
     * Force the candidate strip to appear when the user focuses a text field
     * or explicitly requests the IME. Configuration changes (keyboard
     * visibility flip on flip-phones, locale, etc.) are excluded: returning
     * {@code true} for those made the system re-evaluate the window on every
     * config event, producing a visible show/hide flicker loop.
     */
    @Override
    public boolean onShowInputRequested(int flags, boolean configChange) {
        if (configChange) {
            return false;
        }
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
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            consumeMenuKeyUp = true;
            if (!privateInput && event.getRepeatCount() == 0) {
                keyFeedback.perform();
                toggleQuickMenu();
            }
            return true;
        }
        InputAction action = keyMapper.resolve(keyCode, event.getScanCode());
        // Single feedback chokepoint: the service consumes every recognized
        // action below, which suppresses the platform key click; this is the
        // sole user-configured vibration/sound. Gate on repeatCount==0 so a
        // held backspace or digit auto-repeat does not spam feedback.
        if (action != InputAction.NONE && event.getRepeatCount() == 0) {
            keyFeedback.perform();
        }
        if (action == InputAction.SHOW_QUICK_MENU) {
            if (!privateInput && event.getRepeatCount() == 0) {
                toggleQuickMenu();
            }
            return true;
        }
        if (quickMenuPanel != null && quickMenuPanel.isShowing()) {
            // Treat the bound back-as-backspace key (action == BACKSPACE_DELETE)
            // as "back" here too: on many Japanese flip phones the physical
            // return key is not KEYCODE_BACK, so checking only the keyCode left
            // the bound key swallowed by handleAction()'s default branch and the
            // user could not leave a sub-page (e.g. mode loop) back to the menu.
            if (keyCode == KeyEvent.KEYCODE_BACK || action == InputAction.BACKSPACE_DELETE) {
                if (quickMenuShowingKeymaps || quickMenuShowingModes) {
                    showMainQuickMenu();
                } else {
                    quickMenuPanel.dismiss();
                }
            } else {
                quickMenuPanel.handleAction(action);
            }
            return true;
        }
        // Symbol/phrase panel is modal. Route mapped actions so calibrated
        // confirm and navigation keys work here as well as in the candidate UI.
        if (symbolPanel != null && symbolPanel.isShowing()) {
            if (keyCode == KeyEvent.KEYCODE_BACK || action == InputAction.BACKSPACE_DELETE) {
                symbolPanel.dismiss();
            } else if (event.getAction() == KeyEvent.ACTION_DOWN) {
                symbolPanel.handleAction(action);
            }
            return true;
        }
        // Some Japanese flip phones share BACK and backspace. Respect an
        // explicit user mapping: delete composition/editor text first, then
        // hide the IME when there is nothing left to delete. Once hidden, the
        // lifecycle gate releases the next BACK press to the host system.
        if (keyCode == KeyEvent.KEYCODE_BACK && action == InputAction.BACKSPACE_DELETE) {
            consumeBoundBackKeyUp = true;
            trackResetComboDown(action, event);
            if (privateInput) {
                privateMultiTapState.breakCycle();
                return handlePrivateBoundBackKey();
            }
            return handleBoundBackKey();
        }
        // Two-stage BACK (doc §1 / iWnn quick-select): while composing, first
        // BACK cancels composing and returns to the mode bar; a second BACK
        // (when already on the mode bar) falls through to dismiss the IME.
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (privateInput) {
                return false;
            }
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
        if (keyCode == KeyEvent.KEYCODE_MENU && consumeMenuKeyUp) {
            consumeMenuKeyUp = false;
            return true;
        }
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
        if (privateInput) {
            return handlePrivateAction(action);
        }
        if (showModeBar) {
            return handleModeBarAction(action);
        }
        boolean hadImeComposition = currentComposing != null && currentComposing.length() > 0;
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
                return navigateOrMoveCursor(-1);
            case NAV_RIGHT:
                return navigateOrMoveCursor(1);
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
                if (confirmSelection()) {
                    return true;
                }
                if (hadImeComposition) {
                    // Mid-composition with nothing highlighted: keep OK modal so
                    // the framework does not run Done in the middle of input.
                    return true;
                }
                // Idle: run the iWnn-compatible generic center-OK algorithm
                // (np701kc.md §14.7). Covers Mail, MemoPad, Browser, Data Folder
                // and standard Android editors without per-package hardcoding.
                return handleIdleCenterOk();

            case BACKSPACE_DELETE:
                return handleBackspace();

            case ENTER:
                return handleEnter();

            case DISMISS_IME:
                dismissIme();
                return true;

            case TOGGLE_LANG_MODE:
                cycleLanguageMode();
                return true;

            case SHOW_SYMBOL_PANEL:
                showSymbolPanel();
                return true;

            case SHOW_QUICK_MENU:
                toggleQuickMenu();
                return true;

            case SOFTKEY_LEFT:
                toggleQuickMenu();
                return true;

            case SOFTKEY_RIGHT:
                showSymbolPanel();
                return true;

            case SWITCH_RIME_SCHEMA:
                return false;

            default:
                return false;
        }
    }

    private boolean handlePrivateAction(InputAction action) {
        int digit = action.digit();
        if (digit >= 0) {
            if (privateNumericMode || privateDirectDigits || digit < 2) {
                privateMultiTapState.breakCycle();
                commitChar((char) ('0' + digit));
                return true;
            }
            PrivateMultiTapState.Edit edit = privateMultiTapState.press(
                    digit, android.os.SystemClock.uptimeMillis(), privateMultiTapTimeoutMs(),
                    privateUppercase);
            applyPrivateEdit(edit);
            return true;
        }
        privateMultiTapState.breakCycle();
        switch (action) {
            case TOGGLE_LANG_MODE:
            case SOFTKEY_LEFT:
                privateNumericMode = !privateNumericMode;
                updatePrivateModeBar();
                refreshSoftkeyGuide();
                return true;
            case INPUT_KEY_STAR:
                // Symbols, phrases and their shortcuts are disabled in private fields.
                return true;
            case INPUT_KEY_POUND:
                // Symbols, phrases and their shortcuts are disabled in private fields.
                privateUppercase = !privateUppercase;
                updatePrivateModeBar();
                return true;
            case BACKSPACE_DELETE:
                return deleteFromEditor();
            case NAV_LEFT:
                sendDownUpKeyEvents(KeyEvent.KEYCODE_DPAD_LEFT);
                return true;
            case NAV_RIGHT:
                sendDownUpKeyEvents(KeyEvent.KEYCODE_DPAD_RIGHT);
                return true;
            case ENTER:
                commitTextToEditor("\n");
                return true;
            case CONFIRM_SELECTION:
                return handleIdleCenterOk();
            case DISMISS_IME:
                inputViewActive = false;
                requestHideSelf(0);
                return true;
            default:
                // Prediction, modes, symbols, phrases and menus stay disabled.
                return true;
        }
    }

    /** Render the small En/123 selector used while editing password fields. */
    private void updatePrivateModeBar() {
        if (candidateBar == null || !privateInput) {
            return;
        }
        String enLabel = privateUppercase ? "EN" : "en";
        String[] labels = new String[] {enLabel, "123"};
        candidateBar.setModeBar(labels, privateNumericMode ? 1 : 0);
        candidateBar.showModeBar(true);
        candidateBar.setComposingText("");
        candidateBar.setCandidates(new String[0]);
        candidateBar.setPinyinOptions(new String[0], -1);
    }

    private int privateMultiTapTimeoutMs() {
        return prefs == null ? 600 : Math.max(100, prefs.getMultiTapTimeout());
    }

    private void applyPrivateEdit(PrivateMultiTapState.Edit edit) {
        if (edit == null) {
            return;
        }
        android.view.inputmethod.InputConnection ic = getCurrentInputConnection();
        if (ic == null) {
            Log.w(TAG, "private edit skipped: no focused editor");
            privateMultiTapState.breakCycle();
            return;
        }
        ic.beginBatchEdit();
        try {
            expectedPrivateSelectionDelta = edit.replacePrevious ? 0 : 1;
            if (edit.replacePrevious) {
                ic.deleteSurroundingText(1, 0);
            }
            ic.commitText(String.valueOf(edit.character), 1);
        } finally {
            ic.endBatchEdit();
        }
    }

    private boolean handlePrivateBoundBackKey() {
        boolean deleted = deleteFromEditorIfPossible();
        if (!BoundBackKeyPolicy.shouldHideIme(deleted, editorIsKnownEmpty())) {
            return true;
        }
        inputViewActive = false;
        requestHideSelf(0);
        return true;
    }

    /** Key routing while the iWnn-style mode bar is up (composing empty). */
    private boolean handleModeBarAction(InputAction action) {
        switch (action) {
            case NAV_LEFT:
            case NAV_RIGHT:
            case NAV_UP:
            case NAV_DOWN:
                // Idle (mode bar is only an indicator now): return direction
                // keys to the system so they move the host editor's text cursor
                // (the native ExtractEditText). Mode is switched solely via the
                // TOGGLE_LANG_MODE key, never via direction keys (np701kc.md §15).
                return false;
            case CONFIRM_SELECTION:
                // Mode bar is up => composing empty (idle). Run the generic
                // center-OK algorithm (np701kc.md §14.7).
                return handleIdleCenterOk();
            case TOGGLE_LANG_MODE:
                advanceModeBarToNextInputMode();
                return true;
            case SHOW_SYMBOL_PANEL:
                showSymbolPanel();
                return true;
            case SHOW_QUICK_MENU:
                toggleQuickMenu();
                return true;
            case SOFTKEY_LEFT:
                toggleQuickMenu();
                return true;
            case SOFTKEY_RIGHT:
                showSymbolPanel();
                return true;
            case BACKSPACE_DELETE:
                return deleteFromEditor();
            case ENTER:
                return handleEnter();
            case DISMISS_IME:
                dismissIme();
                return true;
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
                candidateBar.activateDefaultLayer();
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

    /**
     * Pure newline (calibrate-able ENTER, e.g. the {@code *} key whose glyph on
     * Japanese flip phones is a return arrow). If a composition is in flight the
     * top candidate is committed first so the input is not lost, then a newline
     * is inserted. Distinct from OK, which triggers the editor action.
     */
    private boolean handleEnter() {
        flushMultiTapIfActive();
        ImeEngine active = activeEngine();
        if (active != null && active.candidateCount() > 0) {
            active.selectCandidate(0);
        }
        commitTextToEditor("\n");
        return true;
    }

    /**
     * Hide the IME (exit fullscreen / collapse the candidate strip), keeping all
     * committed text. Used when BACK is bound to backspace and OK to enter, so a
     * dedicated key is needed to close the input. Any in-flight composition is
     * committed first so nothing is lost.
     */
    private void dismissIme() {
        flushMultiTapIfActive();
        ImeEngine active = activeEngine();
        if (active != null && active.candidateCount() > 0) {
            active.selectCandidate(0);
        }
        if (active != null) {
            active.reset();
        }
        inputViewActive = false;
        showModeBar = true;
        if (symbolPanel != null && symbolPanel.isShowing()) {
            symbolPanel.dismiss();
        }
        if (quickMenuPanel != null && quickMenuPanel.isShowing()) {
            quickMenuPanel.dismiss();
        }
        requestHideSelf(0);
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

    // ---- iWnn-compatible generic center-OK protocol (np701kc.md §14.7) ----

    /**
     * Generic idle center-OK handler mirroring iWnn's {@code processImeHide()}.
     *
     * <ol>
     *   <li>Marks this session as an "active finish" so {@link #onFinishInputView}
     *       sends {@code Finish_IME} with {@code isActiveFinish=true}.</li>
     *   <li>Sends {@code IME_User_Action_CSKKey} (harmless pre-notification;
     *       MemoPad saves here, Mail ignores it).</li>
     *   <li>If {@code imeOptions & 0x400000ff} is a bare action 2..6 (GO, SEARCH,
     *       SEND, NEXT, DONE without {@code NO_ENTER_ACTION}), calls
     *       {@code performEditorAction(masked)} — covers Browser, Data Folder,
     *       and standard Android editors.</li>
     *   <li>Otherwise (NO_ENTER_ACTION set, or action 0/1) hides the IME; the
     *       resulting {@link #onFinishInputView} sends {@code Finish_IME} —
     *       covers Mail and MemoPad.</li>
     * </ol>
     *
     * @return {@code true} if the event was consumed
     */
    private boolean handleIdleCenterOk() {
        android.view.inputmethod.InputConnection ic = getCurrentInputConnection();
        android.view.inputmethod.EditorInfo info = getCurrentInputEditorInfo();
        if (ic == null || info == null) {
            return false;
        }

        activeFinish = true;

        // Pre-notification: all hosts receive this; unknown hosts ignore it.
        try {
            ic.performPrivateCommand("IME_User_Action_CSKKey", null);
        } catch (Throwable ignored) {
        }

        // Preserve NO_ENTER_ACTION in the mask so we can distinguish bare actions.
        int masked = info.imeOptions & 0x400000ff;
        if (masked >= 2 && masked <= 6) {
            // Bare GO/SEARCH/SEND/NEXT/DONE: standard editor action.
            Log.d(TAG, "handleIdleCenterOk performEditorAction(" + masked + ")");
            try {
                ic.performEditorAction(masked);
            } catch (Throwable t) {
                Log.w(TAG, "performEditorAction failed: " + t);
            }
            return true;
        }

        // NO_ENTER_ACTION set, or action NONE/UNSPECIFIED: hide the IME.
        // onFinishInputView will send Finish_IME { isActiveFinish: true }.
        Log.d(TAG, "handleIdleCenterOk hide (masked=0x"
                + Integer.toHexString(masked) + ")");
        inputViewActive = false;
        showModeBar = true;
        requestHideSelf(0);
        return true;
    }

    /**
     * Idle (no composing, no modal panel) and the current field has a
     * meaningful center-OK action. Used to decide whether the Softkey Guide
     * shows "完成" on the center key.
     */
    private boolean isSoftkeyCompleteState() {
        if (currentComposing != null && currentComposing.length() > 0) {
            return false;
        }
        if (symbolPanel != null && symbolPanel.isShowing()) {
            return false;
        }
        if (quickMenuPanel != null && quickMenuPanel.isShowing()) {
            return false;
        }
        // Show "完成" for any field: the generic algorithm always has a
        // meaningful action (performEditorAction or hide+Finish_IME).
        return getCurrentInputConnection() != null;
    }

    /**
     * Refresh the Softkey Guide labels on vendor (Kyocera) devices.
     * <ul>
     *   <li>SK1 (left): "菜单" if the user has bound SOFTKEY_LEFT, else empty.</li>
     *   <li>SK2 (right): "符号" if the user has bound SOFTKEY_RIGHT, else empty.</li>
     *   <li>CSK (center): "完成" while idle in any field (the generic center-OK
     *       algorithm always has a meaningful action).</li>
     * </ul>
     * No-op on devices without the vendor Softkey Guide framework.
     */
    private void refreshSoftkeyGuide() {
        if (softkeyGuide == null) {
            return;
        }
        android.view.Window w = null;
        try {
            w = getWindow().getWindow();
        } catch (Throwable ignored) {
        }
        // Password-mode language hint belongs in the lower/floating guide
        // area on SHF33, not in the upper action-label row.
        CharSequence sk1 = !privateInput && hasSoftkeyLeftBinding()
                ? getString(R.string.softkey_menu) : "";
        CharSequence sk2 = !privateInput && hasSoftkeyRightBinding()
                ? getString(R.string.softkey_symbol) : "";
        CharSequence csk = isSoftkeyCompleteState() ? getString(R.string.softkey_complete) : "";
        softkeyGuide.setAllLabels(w, sk1, sk2, csk);
        if (privateInput) {
            softkeyGuide.setFloatingGuideAreaLabel(
                    w, SoftkeyGuideHelper.INDEX_SK1, "中/英");
        }
    }

    /** True if the active keymap has a physical key bound to SOFTKEY_LEFT. */
    private boolean hasSoftkeyLeftBinding() {
        return keyMapper != null && keyMapper.hasActionBound(InputAction.SOFTKEY_LEFT);
    }

    /** True if the active keymap has a physical key bound to SOFTKEY_RIGHT. */
    private boolean hasSoftkeyRightBinding() {
        return keyMapper != null && keyMapper.hasActionBound(InputAction.SOFTKEY_RIGHT);
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
            return currentComposing != null && currentComposing.length() > 0;
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

    /**
     * While composing (pinyin candidates, English candidates, or a cycling
     * multi-tap letter is on screen), LEFT/RIGHT navigates the candidate strip.
     * Once input is idle - nothing composing - LEFT/RIGHT moves the host
     * editor's cursor instead, so the user can reposition within already
     * committed text. {@link #sendDownUpKeyEvents} forwards a synthetic D-Pad
     * key to the host editor via the InputConnection; it does not re-enter the
     * IME's own {@code onKeyDown}.
     */
    private boolean navigateOrMoveCursor(int delta) {
        if (currentComposing != null && currentComposing.length() > 0) {
            return moveLayerFocus(delta);
        }
        int keyCode = delta < 0 ? KeyEvent.KEYCODE_DPAD_LEFT : KeyEvent.KEYCODE_DPAD_RIGHT;
        sendDownUpKeyEvents(keyCode);
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
        if (privateInput) {
            showModeBar = false;
            clearCandidateUi();
            return;
        }
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

    /** Use the host field's DONE/NEXT/SEARCH action, otherwise insert a newline. */
    private boolean performEditorActionOrNewline() {
        if (sendDefaultEditorAction(true)) {
            return true;
        }
        android.view.inputmethod.InputConnection ic = getCurrentInputConnection();
        return ic != null && ic.commitText("\n", 1);
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
        flashReadyStatus();
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
        // Toggle: press again to dismiss
        if (symbolPanel != null && symbolPanel.isShowing()) {
            symbolPanel.dismiss();
            refreshSoftkeyGuide();
            return;
        }
        // Exit mode bar so D-pad events are not visually ambiguous (the mode bar
        // highlight would remain frozen while the symbol panel handles nav).
        if (showModeBar) {
            exitModeBar();
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
        refreshSoftkeyGuide();
    }

    private void toggleQuickMenu() {
        if (rootContainer == null) {
            return;
        }
        if (quickMenuPanel != null && quickMenuPanel.isShowing()) {
            quickMenuPanel.dismiss();
            refreshSoftkeyGuide();
            return;
        }
        if (symbolPanel != null && symbolPanel.isShowing()) {
            symbolPanel.dismiss();
        }
        if (quickMenuPanel == null) {
            quickMenuPanel = new QuickMenuPanel(this, new QuickMenuPanel.Callback() {
                @Override
                public void onQuickMenuItem(int position) {
                    handleQuickMenuItem(position);
                }
            });
        }
        showMainQuickMenu();
        refreshSoftkeyGuide();
    }

    private void showMainQuickMenu() {
        quickMenuShowingKeymaps = false;
        quickMenuShowingModes = false;
        quickMenuPanel.show(rootContainer, R.string.quick_menu_title, new String[] {
                getString(R.string.quick_menu_keymap),
                getString(R.string.quick_menu_pick_ime),
                getString(R.string.quick_menu_mode_loop),
                getString(R.string.quick_menu_settings),
        });
    }

    private void handleQuickMenuItem(int position) {
        if (quickMenuShowingKeymaps) {
            if (position >= 0 && position < quickMenuKeymapSlots.length) {
                if (keyMapper.activateSlot(quickMenuKeymapSlots[position])) {
                    quickMenuPanel.dismiss();
                    enterModeBar();
                }
            }
            return;
        }
        if (quickMenuShowingModes) {
            toggleQuickMode(position);
            return;
        }
        switch (position) {
            case 0:
                showQuickKeymapMenu();
                break;
            case 1:
                quickMenuPanel.dismiss();
                InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.showInputMethodPicker();
                }
                break;
            case 2:
                showQuickModeLoopMenu(0);
                break;
            case 3:
                quickMenuPanel.dismiss();
                startActivity(new android.content.Intent(this, SettingsActivity.class)
                        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));
                break;
            default:
                break;
        }
    }

    private void showQuickKeymapMenu() {
        java.util.ArrayList<Integer> slots = new java.util.ArrayList<>();
        java.util.ArrayList<String> labels = new java.util.ArrayList<>();
        int activeSlot = keyMapper.getActiveSlot();
        for (int slot = KeymapSlots.FACTORY; slot <= KeymapSlots.USER_MAX; slot++) {
            if (slot == KeymapSlots.FACTORY || keyMapper.isSlotConfigured(slot)) {
                slots.add(slot);
                String name = KeymapProfilesActivity.slotName(this, slot);
                labels.add(slot == activeSlot ? "● " + name : "○ " + name);
            }
        }
        quickMenuKeymapSlots = new int[slots.size()];
        for (int i = 0; i < slots.size(); i++) {
            quickMenuKeymapSlots[i] = slots.get(i);
        }
        quickMenuShowingKeymaps = true;
        quickMenuShowingModes = false;
        quickMenuPanel.show(rootContainer, R.string.quick_menu_keymap_title,
                labels.toArray(new String[0]));
    }

    private void showQuickModeLoopMenu(int selection) {
        List<InputMode> enabled = new GarahoPrefs(this).getModeLoop();
        String[] labels = new String[quickMenuModes.length];
        boolean[] checked = new boolean[quickMenuModes.length];
        for (int i = 0; i < quickMenuModes.length; i++) {
            labels[i] = modeName(quickMenuModes[i]);
            checked[i] = enabled.contains(quickMenuModes[i]);
        }
        quickMenuShowingKeymaps = false;
        quickMenuShowingModes = true;
        quickMenuPanel.showChecked(rootContainer, R.string.quick_menu_mode_loop, labels, checked);
        quickMenuPanel.setSelection(selection);
    }

    private void toggleQuickMode(int position) {
        if (position < 0 || position >= quickMenuModes.length) {
            return;
        }
        GarahoPrefs prefs = new GarahoPrefs(this);
        List<InputMode> current = prefs.getModeLoop();
        java.util.LinkedHashSet<InputMode> selected = new java.util.LinkedHashSet<>(current);
        InputMode target = quickMenuModes[position];
        if (selected.contains(target)) {
            if (selected.size() == 1) {
                Toast.makeText(this, R.string.mode_loop_keep_one, Toast.LENGTH_SHORT).show();
                return;
            }
            selected.remove(target);
        } else {
            selected.add(target);
        }
        java.util.ArrayList<InputMode> ordered = new java.util.ArrayList<>();
        for (InputMode candidate : quickMenuModes) {
            if (selected.contains(candidate)) {
                ordered.add(candidate);
            }
        }
        prefs.setModeLoop(ordered);
        applyModeLoopChange(ordered);
        showQuickModeLoopMenu(position);
    }

    private void applyModeLoopChange(List<InputMode> enabledModes) {
        if (!enabledModes.contains(mode)) {
            ImeEngine previous = activeEngine();
            if (previous != null) {
                previous.reset();
            }
            mode = enabledModes.get(0);
            currentComposing = "";
            enterModeBar();
            return;
        }
        buildModeBar();
        if (candidateBar == null) {
            return;
        }
        candidateBar.setModeLabel(indicatorLabel());
        if (showModeBar) {
            candidateBar.setModeBar(barLabels, modeBarIndex);
        }
    }

    private String modeName(InputMode inputMode) {
        switch (inputMode) {
            case ZH: return getString(R.string.mode_zh_t9);
            case ZH_MTAP: return getString(R.string.mode_zh_mtap);
            case EN: return getString(R.string.mode_en_t9);
            case EN_MTAP: return getString(R.string.mode_en_mtap);
            case NUM: return getString(R.string.mode_num);
            default: return inputMode.name();
        }
    }

    private void commitChar(char c) {
        commitTextToEditor(String.valueOf(c));
    }

    private void commitTextToEditor(CharSequence text) {
        android.view.inputmethod.InputConnection ic = getCurrentInputConnection();
        if (ic == null) {
            Log.w(TAG, "commitText skipped: no focused editor");
            return;
        }
        boolean ok = ic.commitText(text, 1);
        Log.d(TAG, "commitText ok=" + ok + " length=" + (text == null ? 0 : text.length()));
    }

    @Override
    public void onComposingChanged(CharSequence composing) {
        if (privateInput || suppressEngineCallbacks) {
            return;
        }
        currentComposing = composing;
        if (candidateBar != null) {
            candidateBar.setComposingText(composing);
        }
        refreshSoftkeyGuide();
    }

    private void clearAllEngineState() {
        suppressEngineCallbacks = true;
        try {
            if (pinyinEngine != null) pinyinEngine.reset();
            if (englishEngine != null) englishEngine.reset();
            if (zhMultiTapEngine != null) zhMultiTapEngine.reset();
            if (enMultiTapEngine != null) enMultiTapEngine.reset();
        } finally {
            suppressEngineCallbacks = false;
        }
        privateMultiTapState.breakCycle();
        currentComposing = "";
    }

    private void dismissPrivatePanels() {
        if (symbolPanel != null && symbolPanel.isShowing()) {
            symbolPanel.dismiss();
        }
        if (quickMenuPanel != null && quickMenuPanel.isShowing()) {
            quickMenuPanel.dismiss();
        }
    }

    private void clearCandidateUi() {
        if (candidateBar == null) {
            return;
        }
        candidateBar.setCandidates(new String[0]);
        candidateBar.setPinyinOptions(new String[0], -1);
        candidateBar.setComposingText("");
        candidateBar.showModeBar(false);
        candidateBar.setModeLabel("");
    }

    @Override
    public void onCandidatesChanged(List<String> candidates) {
        if (privateInput || suppressEngineCallbacks) {
            return;
        }
        if (candidateBar != null) {
            String[] candidateArray = candidates.toArray(new String[0]);
            ImeEngine active = activeEngine();
            if (active instanceof LayeredPinyinEngine) {
                LayeredPinyinEngine layered = (LayeredPinyinEngine) active;
                candidateBar.setCandidatesAndPinyinOptions(
                        candidateArray,
                        layered.getPinyinOptions().toArray(new String[0]),
                        layered.getSelectedPinyinIndex());
            } else {
                candidateBar.setCandidatesAndPinyinOptions(candidateArray, new String[0], -1);
            }
        }
        refreshSoftkeyGuide();
    }

    @Override
    public void onCommit(String text) {
        if (text == null || privateInput || suppressEngineCallbacks) {
            return;
        }
        if ((mode == InputMode.EN || mode == InputMode.EN_MTAP)
                && prefs != null && prefs.getAutoCapitalize()) {
            text = maybeCapitalize(text);
        }
        commitTextToEditor(text);
    }

    /**
     * Upper-case the committed English text when the cursor sits at a sentence
     * boundary (start of editor or after {@code . ! ?} / line break). Respects
     * the {@code 首字母自动大写} setting and only applies to English modes.
     */
    private String maybeCapitalize(String text) {
        if (text.length() == 0 || !Character.isLetter(text.charAt(0))) {
            return text;
        }
        android.view.inputmethod.InputConnection ic = getCurrentInputConnection();
        if (ic == null) {
            return text;
        }
        CharSequence before = ic.getTextBeforeCursor(CAPITALIZE_LOOKBACK, 0);
        if (!EnglishCapitalization.atSentenceStart(before)) {
            return text;
        }
        return EnglishCapitalization.capitalize(text);
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
