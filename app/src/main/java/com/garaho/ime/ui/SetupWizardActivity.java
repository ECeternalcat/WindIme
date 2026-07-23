package com.garaho.ime.ui;

import com.garaho.ime.R;
import com.garaho.ime.keymap.InputAction;
import com.garaho.ime.keymap.KeyMapConfig;
import com.garaho.ime.keymap.KeyMapper;

import android.app.Activity;
import android.os.Bundle;
import android.os.Vibrator;
import android.view.KeyEvent;
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

    private static final long BUZZ_MS = 50;

    /**
     * Only device-specific function keys are calibrated. The D-Pad
     * (NAV_UP/DOWN/LEFT/RIGHT), confirm (DPAD_CENTER/ENTER) and digit keys are
     * Android-standard keycodes already covered by the bundled keymap and the
     * always-on {@code STANDARD_ANDROID} fallback in {@code KeyMapper}, so they
     * never need binding.
     */
    private static final InputAction[] STEPS = {
            InputAction.TOGGLE_LANG_MODE,
            InputAction.SHOW_SYMBOL_PANEL,
            InputAction.BACKSPACE_DELETE,
    };

    private TextView promptView;
    private TextView tipView;
    private TextView statusView;
    private int currentStep = 0;
    private final Map<InputAction, KeyMapConfig.Mapping> captured = new LinkedHashMap<>();
    private Vibrator vibrator;
    private KeyMapper keyMapper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_setup_wizard);
        promptView = findViewById(R.id.wizard_prompt);
        tipView = findViewById(R.id.wizard_tip);
        statusView = findViewById(R.id.wizard_status);
        vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        keyMapper = new KeyMapper(this);
        renderStep();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // BACK skips the current step (some flip-phones have no dedicated
        // symbol / language key); after the last step BACK finishes normally.
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (currentStep < STEPS.length) {
                currentStep++;
                if (currentStep >= STEPS.length) {
                    finishWizard();
                } else {
                    renderStep();
                }
                return true;
            }
            return super.onKeyDown(keyCode, event);
        }
        if (currentStep >= STEPS.length) {
            return super.onKeyDown(keyCode, event);
        }
        InputAction target = STEPS[currentStep];
        // Reject keys reserved for core input/navigation (digits, *, #, D-Pad,
        // OK, ENTER) - binding them to a function would shadow T9 typing or
        // cursor movement. Confirming the standard DEL(67) for the backspace
        // step is allowed.
        if (KeyMapper.isReservedFor(keyCode, target)) {
            tipView.setText(R.string.wizard_reserved_key);
            tipView.setVisibility(android.view.View.VISIBLE);
            buzz();
            return true;
        }
        // Reject a key already bound to another action this session.
        for (Map.Entry<InputAction, KeyMapConfig.Mapping> e : captured.entrySet()) {
            KeyMapConfig.Mapping m = e.getValue();
            if (e.getKey() != target
                    && (m.keycode == keyCode
                    || (event.getScanCode() != 0 && m.scanCode == event.getScanCode()))) {
                tipView.setText(R.string.wizard_already_bound);
                tipView.setVisibility(android.view.View.VISIBLE);
                buzz();
                return true;
            }
        }
        KeyMapConfig.Mapping m = new KeyMapConfig.Mapping(event.getScanCode(), keyCode, target);
        captured.put(target, m);
        buzz();
        currentStep++;
        if (currentStep >= STEPS.length) {
            finishWizard();
        } else {
            renderStep();
        }
        return true;
    }

    private void renderStep() {
        if (currentStep >= STEPS.length) {
            return;
        }
        InputAction a = STEPS[currentStep];
        promptView.setText(getString(R.string.wizard_press_action_prompt, displayName(a)));
        tipView.setText(R.string.wizard_skip_tip);
        tipView.setVisibility(android.view.View.VISIBLE);
        StringBuilder sb = new StringBuilder();
        sb.append(getString(R.string.wizard_step_format, currentStep + 1, STEPS.length)).append('\n');
        for (Map.Entry<InputAction, KeyMapConfig.Mapping> e : captured.entrySet()) {
            sb.append(displayName(e.getKey()))
              .append(" -> sc=").append(e.getValue().scanCode)
              .append(" kc=").append(e.getValue().keycode)
              .append('\n');
        }
        statusView.setText(sb.toString());
    }

    private void finishWizard() {
        // Start from the factory default so the T9 digit pad, D-Pad and
        // enter/backspace stay mapped even though the wizard only captures a
        // handful of function keys. Calibrated captures then override the
        // matching action, and everything is written to user_keymap.json.
        KeyMapConfig config = keyMapper.getConfig();
        if (config == null) {
            config = new KeyMapConfig();
        }
        for (KeyMapConfig.Mapping m : config.mappings) {
            if (captured.containsKey(m.action)) {
                m.scanCode = captured.get(m.action).scanCode;
                m.keycode = captured.get(m.action).keycode;
            }
        }
        for (KeyMapConfig.Mapping m : captured.values()) {
            boolean present = false;
            for (KeyMapConfig.Mapping existing : config.mappings) {
                if (existing.action == m.action) {
                    present = true;
                    break;
                }
            }
            if (!present) {
                config.mappings.add(new KeyMapConfig.Mapping(m.scanCode, m.keycode, m.action));
            }
        }
        config.deviceProfile = "User_Calibrated";
        config.version = KeyMapConfig.DEFAULT_VERSION;
        boolean ok = keyMapper.saveUserConfig(config);
        promptView.setText(ok ? R.string.wizard_done_ok : R.string.wizard_done_fail);
        statusView.setText(config.deviceProfile + " (" + config.mappings.size() + ")");
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
