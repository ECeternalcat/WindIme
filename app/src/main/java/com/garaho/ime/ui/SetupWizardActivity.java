package com.garaho.ime.ui;

import com.garaho.ime.R;
import com.garaho.ime.compat.SoftkeyGuideHelper;
import com.garaho.ime.keymap.InputAction;
import com.garaho.ime.keymap.KeyMapConfig;
import com.garaho.ime.keymap.KeyMapper;
import com.garaho.ime.keymap.KeymapSlots;
import com.garaho.ime.settings.GarahoPrefs;
import com.garaho.ime.settings.KeymapProfilesActivity;

import android.app.AlertDialog;
import android.app.Activity;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Vibrator;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 0-Touch calibration wizard (design doc §3.2).
 *
 * <p>State-machine-driven: each step waits for the next physical keypress and
 * binds it to the requested {@link InputAction}. Progress is reflected entirely
 * through D-Pad / OK focused text - no touch required.
 */
public class SetupWizardActivity extends Activity {

    public static final String EXTRA_TARGET_SLOT = "target_slot";

    private static final long BUZZ_MS = 50;

    /** Core calibration steps present on all devices. */
    private static final InputAction[] CORE_STEPS = {
            InputAction.TOGGLE_LANG_MODE,
            InputAction.SHOW_SYMBOL_PANEL,
            InputAction.SHOW_QUICK_MENU,
            InputAction.BACKSPACE_DELETE,
            InputAction.ENTER,
            InputAction.TOGGLE_CAPS,
            InputAction.COLLAPSE_IME,
    };

    /** Extra steps appended only on Kyocera devices with a Softkey Guide. */
    private static final InputAction[] KYOCERA_STEPS = {
            InputAction.SOFTKEY_LEFT,
            InputAction.SOFTKEY_RIGHT,
    };

    private TextView stepView;
    private TextView promptView;
    private TextView tipView;
    private TextView statusView;
    private int currentStep = 0;
    private InputAction[] steps;
    private final Map<InputAction, KeyMapConfig.Mapping> captured = new LinkedHashMap<>();
    private final java.util.Set<InputAction> skipped = new java.util.LinkedHashSet<>();
    private Vibrator vibrator;
    private KeyMapper keyMapper;
    private GarahoPrefs prefs;
    private KeyMapConfig baseConfig;
    private int targetSlot;
    private boolean finished;
    private boolean isKyocera;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_setup_wizard);
        stepView = findViewById(R.id.wizard_step);
        promptView = findViewById(R.id.wizard_prompt);
        tipView = findViewById(R.id.wizard_tip);
        statusView = findViewById(R.id.wizard_status);
        vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        keyMapper = new KeyMapper(this);
        prefs = new GarahoPrefs(this);
        targetSlot = getIntent().getIntExtra(EXTRA_TARGET_SLOT, -1);
        if (!KeymapSlots.isUser(targetSlot)) {
            finish();
            return;
        }
        baseConfig = keyMapper.baseConfigForSlot(targetSlot);
        if (baseConfig == null) {
            finish();
            return;
        }
        // Detect Kyocera vendor Softkey Guide; only those devices have the
        // physical left/right soft keys below the screen that we can calibrate.
        isKyocera = SoftkeyGuideHelper.create(this) != null;
        steps = buildSteps();
        renderStep();
    }

    private InputAction[] buildSteps() {
        if (!isKyocera) {
            return CORE_STEPS;
        }
        // On Kyocera feature phones the left/right soft keys are fixed by the
        // vendor framework (menu / symbol), so SHOW_QUICK_MENU and
        // SHOW_SYMBOL_PANEL are both handled by the physical soft keys and must
        // not be calibrated separately; DISMISS_IME is handled by the native
        // center-OK "完成" protocol. Skip those and instead calibrate the
        // physical SOFTKEY_LEFT / SOFTKEY_RIGHT.
        java.util.ArrayList<InputAction> list = new java.util.ArrayList<>();
        for (InputAction a : CORE_STEPS) {
            if (a == InputAction.SHOW_QUICK_MENU
                    || a == InputAction.SHOW_SYMBOL_PANEL
                    || a == InputAction.DISMISS_IME) {
                continue;
            }
            list.add(a);
        }
        for (InputAction a : KYOCERA_STEPS) {
            list.add(a);
        }
        return list.toArray(new InputAction[0]);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (finished || currentStep >= steps.length) {
            return super.onKeyDown(keyCode, event);
        }
        if (event.getRepeatCount() > 0) {
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
            skipCurrentStep();
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
            goToPreviousStep();
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
            skipCurrentStep();
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
            return true;
        }
        InputAction target = steps[currentStep];
        if (keyCode == KeyEvent.KEYCODE_BACK
                && target != InputAction.BACKSPACE_DELETE
                && target != InputAction.COLLAPSE_IME) {
            finish();
            return true;
        }
        if (KeyMapper.isReservedFor(keyCode, target)) {
            tipView.setText(R.string.wizard_key_reserved);
            tipView.setVisibility(android.view.View.VISIBLE);
            return true;
        }
        // The return key may combine short and long press: when it is already
        // captured as backspace and the user offers the same key for the
        // collapse step, do not reject it as a duplicate - let the user pick
        // the long-press behaviour (the short press stays backspace either
        // way). The choice is stored in the back-key long-press setting.
        if (target == InputAction.COLLAPSE_IME
                && keyCode == KeyEvent.KEYCODE_BACK
                && capturedBackspaceOnBackKey()) {
            showBackKeyLongPressChoice();
            return true;
        }
        if (isAlreadyCaptured(keyCode, event.getScanCode())) {
            tipView.setText(R.string.wizard_key_duplicate);
            tipView.setVisibility(android.view.View.VISIBLE);
            return true;
        }
        KeyMapConfig.Mapping m = new KeyMapConfig.Mapping(event.getScanCode(), keyCode, target);
        captured.put(target, m);
        skipped.remove(target);
        buzz();
        advanceStep();
        return true;
    }

    private void skipCurrentStep() {
        InputAction target = steps[currentStep];
        captured.remove(target);
        skipped.add(target);
        advanceStep();
    }

    private void goToPreviousStep() {
        if (currentStep == 0) {
            return;
        }
        currentStep--;
        InputAction target = steps[currentStep];
        captured.remove(target);
        skipped.remove(target);
        renderStep();
    }

    private void advanceStep() {
        currentStep++;
        if (currentStep >= steps.length) {
            finishWizard();
        } else {
            renderStep();
        }
    }

    private boolean isAlreadyCaptured(int keyCode, int scanCode) {
        for (KeyMapConfig.Mapping mapping : captured.values()) {
            if (keyCode != 0 && mapping.keycode == keyCode) {
                return true;
            }
            if (scanCode != 0 && mapping.scanCode == scanCode) {
                return true;
            }
        }
        return false;
    }

    /** Whether the return key (KEYCODE_BACK) was captured as backspace earlier in this wizard run. */
    private boolean capturedBackspaceOnBackKey() {
        KeyMapConfig.Mapping backspace = captured.get(InputAction.BACKSPACE_DELETE);
        return backspace != null && backspace.keycode == KeyEvent.KEYCODE_BACK;
    }

    /**
     * Long-press behaviour choice for the return key that is already bound as
     * backspace. Short press stays backspace either way; the long press either
     * collapses the IME (iWnn-style, also the settings default) or keeps
     * rapid-deleting. The chosen value lands in the same setting the
     * input-and-keys page exposes.
     */
    private void showBackKeyLongPressChoice() {
        final String[] options = {
                getString(R.string.back_long_press_collapse),
                getString(R.string.back_long_press_fast_delete),
        };
        new AlertDialog.Builder(this)
                .setTitle(R.string.wizard_back_long_press_title)
                .setMessage(R.string.wizard_back_long_press_message)
                .setItems(options, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        prefs.setBackKeyLongPress(which == 0
                                ? com.garaho.ime.settings.GarahoPrefs.BACK_LONG_PRESS_COLLAPSE
                                : com.garaho.ime.settings.GarahoPrefs.BACK_LONG_PRESS_FAST_DELETE);
                        skipped.remove(InputAction.COLLAPSE_IME);
                        buzz();
                        advanceStep();
                    }
                })
                .show();
    }

    private void renderStep() {
        if (currentStep >= steps.length) {
            return;
        }
        InputAction a = steps[currentStep];
        stepView.setText(getString(R.string.wizard_step_format, currentStep + 1, steps.length));
        promptView.setText(getString(R.string.wizard_press_action_prompt, displayName(a)));
        if (a == InputAction.BACKSPACE_DELETE) {
            tipView.setText(R.string.wizard_back_as_delete_tip);
            tipView.setVisibility(android.view.View.VISIBLE);
        } else if (a == InputAction.COLLAPSE_IME) {
            tipView.setText(R.string.wizard_collapse_tip);
            tipView.setVisibility(android.view.View.VISIBLE);
        } else if (a == InputAction.SOFTKEY_LEFT || a == InputAction.SOFTKEY_RIGHT) {
            tipView.setText(R.string.wizard_softkey_tip);
            tipView.setVisibility(android.view.View.VISIBLE);
        } else {
            // Full instructions live on the pre-calibration notice page; the
            // per-step highlight only carries the short action-specific hint.
            tipView.setVisibility(android.view.View.GONE);
        }
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<InputAction, KeyMapConfig.Mapping> e : captured.entrySet()) {
            sb.append(displayName(e.getKey()))
              .append(" -> sc=").append(e.getValue().scanCode)
              .append(" kc=").append(e.getValue().keycode)
                .append('\n');
        }
        for (InputAction action : skipped) {
            sb.append(displayName(action)).append(" -> ")
                    .append(getString(R.string.wizard_skipped)).append('\n');
        }
        statusView.setText(sb.toString());
    }

    private void finishWizard() {
        finished = true;
        final KeyMapConfig result = KeyMapConfig.merge(baseConfig, captured);
        result.deviceProfile = "User_Keymap_" + targetSlot;
        result.version = KeyMapConfig.DEFAULT_VERSION;
        showNameDialog(result);
    }

    private void showNameDialog(final KeyMapConfig result) {
        View body = LayoutInflater.from(this).inflate(R.layout.dialog_keymap_name, null);
        final EditText field = body.findViewById(R.id.keymap_name);
        field.setText(KeymapProfilesActivity.slotName(this, targetSlot));
        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.wizard_name_config)
                .setView(body)
                .setPositiveButton(R.string.wizard_save_and_use, null)
                .setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        finished = false;
                        currentStep = steps.length - 1;
                        renderStep();
                    }
                })
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(view -> {
                    String name = field.getText().toString().trim();
                    if (name.isEmpty()) {
                        field.setError(getString(R.string.keymap_name_required));
                        return;
                    }
                    if (!keyMapper.saveUserSlot(targetSlot, result)) {
                        field.setError(getString(R.string.wizard_done_fail));
                        return;
                    }
                    prefs.setKeymapSlotName(targetSlot, name);
                    keyMapper.activateSlot(targetSlot);
                    dialog.dismiss();
                    showDone(name);
                }));
        dialog.show();
        field.requestFocus();
        field.selectAll();
    }

    private void showDone(String name) {
        promptView.setText(R.string.wizard_done_ok);
        statusView.setText(getString(R.string.wizard_saved_profile, name));
        tipView.setText(R.string.wizard_done_controls);
        tipView.setVisibility(View.VISIBLE);
        setResult(RESULT_OK);
    }

    private void buzz() {
        if (vibrator != null && vibrator.hasVibrator()) {
            vibrator.vibrate(BUZZ_MS);
        }
    }

    private String displayName(InputAction a) {
        switch (a) {
            case TOGGLE_LANG_MODE: return getString(R.string.action_lang_mode);
            case SHOW_SYMBOL_PANEL: return getString(R.string.action_symbol_panel);
            case SHOW_QUICK_MENU: return getString(R.string.action_quick_menu);
            case BACKSPACE_DELETE: return getString(R.string.action_backspace);
            case ENTER: return getString(R.string.action_enter);
            case TOGGLE_CAPS: return getString(R.string.action_caps);
            case DISMISS_IME: return getString(R.string.action_dismiss);
            case COLLAPSE_IME: return getString(R.string.action_collapse);
            case CONFIRM_SELECTION: return getString(R.string.action_confirm);
            case NAV_UP: return getString(R.string.action_nav_up);
            case NAV_DOWN: return getString(R.string.action_nav_down);
            case NAV_LEFT: return getString(R.string.action_nav_left);
            case NAV_RIGHT: return getString(R.string.action_nav_right);
            case SOFTKEY_LEFT: return getString(R.string.action_softkey_left);
            case SOFTKEY_RIGHT: return getString(R.string.action_softkey_right);
            default: return a.name();
        }
    }
}
