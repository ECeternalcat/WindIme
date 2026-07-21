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

    private static final InputAction[] STEPS = {
            InputAction.TOGGLE_LANG_MODE,
            InputAction.SHOW_SYMBOL_PANEL,
            InputAction.BACKSPACE_DELETE,
            InputAction.CONFIRM_SELECTION,
            InputAction.NAV_UP,
            InputAction.NAV_DOWN,
            InputAction.NAV_LEFT,
            InputAction.NAV_RIGHT,
    };

    private TextView promptView;
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
        statusView = findViewById(R.id.wizard_status);
        vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        keyMapper = new KeyMapper(this);
        renderStep();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (currentStep >= STEPS.length) {
            return super.onKeyDown(keyCode, event);
        }
        InputAction target = STEPS[currentStep];
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
        KeyMapConfig config = new KeyMapConfig();
        config.deviceProfile = "User_Calibrated";
        config.version = KeyMapConfig.DEFAULT_VERSION;
        config.mappings.addAll(captured.values());
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
