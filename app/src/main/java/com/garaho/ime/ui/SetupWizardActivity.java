package com.garaho.ime.ui;

import com.garaho.ime.R;
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

    private static final InputAction[] STEPS = {
            InputAction.TOGGLE_LANG_MODE,
            InputAction.SHOW_SYMBOL_PANEL,
            InputAction.SHOW_QUICK_MENU,
            InputAction.BACKSPACE_DELETE,
    };

    private TextView promptView;
    private TextView tipView;
    private TextView statusView;
    private int currentStep = 0;
    private final Map<InputAction, KeyMapConfig.Mapping> captured = new LinkedHashMap<>();
    private final java.util.Set<InputAction> skipped = new java.util.LinkedHashSet<>();
    private Vibrator vibrator;
    private KeyMapper keyMapper;
    private GarahoPrefs prefs;
    private KeyMapConfig baseConfig;
    private int targetSlot;
    private boolean finished;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_setup_wizard);
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
        renderStep();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (finished || currentStep >= STEPS.length) {
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
        InputAction target = STEPS[currentStep];
        if (keyCode == KeyEvent.KEYCODE_BACK && target != InputAction.BACKSPACE_DELETE) {
            finish();
            return true;
        }
        if (KeyMapper.isReservedFor(keyCode, target)) {
            tipView.setText(R.string.wizard_key_reserved);
            tipView.setVisibility(android.view.View.VISIBLE);
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
        InputAction target = STEPS[currentStep];
        captured.remove(target);
        skipped.add(target);
        advanceStep();
    }

    private void goToPreviousStep() {
        if (currentStep == 0) {
            return;
        }
        currentStep--;
        InputAction target = STEPS[currentStep];
        captured.remove(target);
        skipped.remove(target);
        renderStep();
    }

    private void advanceStep() {
        currentStep++;
        if (currentStep >= STEPS.length) {
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

    private void renderStep() {
        if (currentStep >= STEPS.length) {
            return;
        }
        InputAction a = STEPS[currentStep];
        promptView.setText(getString(R.string.wizard_target_format,
                KeymapProfilesActivity.slotName(this, targetSlot),
                getString(R.string.wizard_press_action_prompt, displayName(a))));
        if (a == InputAction.BACKSPACE_DELETE) {
            tipView.setText(getString(R.string.wizard_controls) + "\n"
                    + getString(R.string.wizard_back_as_delete_tip));
            tipView.setVisibility(android.view.View.VISIBLE);
        } else {
            tipView.setText(R.string.wizard_controls);
            tipView.setVisibility(android.view.View.VISIBLE);
        }
        StringBuilder sb = new StringBuilder();
        sb.append(getString(R.string.wizard_step_format, currentStep + 1, STEPS.length)).append('\n');
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
                        currentStep = STEPS.length - 1;
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
            case CONFIRM_SELECTION: return getString(R.string.action_confirm);
            case NAV_UP: return getString(R.string.action_nav_up);
            case NAV_DOWN: return getString(R.string.action_nav_down);
            case NAV_LEFT: return getString(R.string.action_nav_left);
            case NAV_RIGHT: return getString(R.string.action_nav_right);
            default: return a.name();
        }
    }
}
