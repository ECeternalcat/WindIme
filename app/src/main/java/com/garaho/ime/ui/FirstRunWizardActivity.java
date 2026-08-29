package com.garaho.ime.ui;

import com.garaho.ime.R;
import com.garaho.ime.keymap.KeyMapper;
import com.garaho.ime.keymap.KeymapSlots;
import com.garaho.ime.settings.GarahoPrefs;
import com.garaho.ime.settings.ImeSetupActivity;
import com.garaho.ime.settings.ImeStatus;
import com.garaho.ime.settings.KeymapProfilesActivity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

/**
 * First-run wizard. Five stacked pages switched by D-Pad / OK:
 * <ol>
 *   <li>Usage notice and disclaimer.</li>
 *   <li>Welcome.</li>
 *   <li>Set Wind IME as the default input method (button opens
 *       {@link ImeSetupActivity}; "继续" is gated on
 *       {@link ImeStatus#isActive}).</li>
 *   <li>Key calibration (button opens the calibration flow;
 *       "继续" is gated on a configured user keymap slot).</li>
 *   <li>Done.</li>
 * </ol>
 * Shown by {@link LauncherActivity} until {@link GarahoPrefs#isFirstRunCompleted()}
 * is true. BACK steps back one page (the notice cancels).
 */
public class FirstRunWizardActivity extends Activity {

    private static final int STEP_NOTICE = 0;
    private static final int STEP_WELCOME = 1;
    private static final int STEP_DEFAULT_IME = 2;
    private static final int STEP_CALIBRATE = 3;
    private static final int STEP_DONE = 4;

    private static final int TOTAL_STEPS = 2;

    private GarahoPrefs prefs;
    private int step = STEP_NOTICE;

    private final View[] stepViews = new View[5];
    private TextView stepIndicator;
    private TextView defaultStatus;
    private TextView calibrateStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_first_run);
        prefs = new GarahoPrefs(this);

        stepViews[STEP_NOTICE] = findViewById(R.id.firstrun_step_notice);
        stepViews[STEP_WELCOME] = findViewById(R.id.firstrun_step_welcome);
        stepViews[STEP_DEFAULT_IME] = findViewById(R.id.firstrun_step_default);
        stepViews[STEP_CALIBRATE] = findViewById(R.id.firstrun_step_calibrate);
        stepViews[STEP_DONE] = findViewById(R.id.firstrun_step_done);
        stepIndicator = findViewById(R.id.firstrun_step_indicator);
        defaultStatus = findViewById(R.id.firstrun_default_status);
        calibrateStatus = findViewById(R.id.firstrun_calibrate_status);

        findViewById(R.id.firstrun_notice_accept).setOnClickListener(v -> showStep(STEP_WELCOME));
        findViewById(R.id.firstrun_default_btn).setOnClickListener(v ->
                startActivity(new Intent(this, ImeSetupActivity.class)));
        findViewById(R.id.firstrun_default_continue).setOnClickListener(v -> onDefaultContinue());
        findViewById(R.id.firstrun_calibrate_btn).setOnClickListener(v ->
                startActivity(new Intent(this, KeymapProfilesActivity.class)
                        .putExtra(KeymapProfilesActivity.EXTRA_CALIBRATION_PICKER, true)));
        findViewById(R.id.firstrun_calibrate_continue).setOnClickListener(v -> onCalibrateContinue());

        showStep(STEP_NOTICE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStatus();
    }

    private void showStep(int s) {
        step = s;
        for (int i = 0; i < stepViews.length; i++) {
            stepViews[i].setVisibility(i == s ? View.VISIBLE : View.GONE);
        }
        // The two configuration pages carry a "n / 2" indicator; the notice,
        // welcome and done pages do not (they are framing, not numbered steps).
        if (s == STEP_DEFAULT_IME) {
            stepIndicator.setText(getString(R.string.firstrun_step_format, 1, TOTAL_STEPS));
            stepIndicator.setVisibility(View.VISIBLE);
        } else if (s == STEP_CALIBRATE) {
            stepIndicator.setText(getString(R.string.firstrun_step_format, 2, TOTAL_STEPS));
            stepIndicator.setVisibility(View.VISIBLE);
        } else {
            stepIndicator.setVisibility(View.GONE);
        }
        View focus = null;
        switch (s) {
            case STEP_NOTICE:
                focus = findViewById(R.id.firstrun_notice_accept);
                break;
            case STEP_WELCOME:
                focus = stepViews[STEP_WELCOME];
                break;
            case STEP_DEFAULT_IME:
                focus = findViewById(R.id.firstrun_default_btn);
                break;
            case STEP_CALIBRATE:
                focus = findViewById(R.id.firstrun_calibrate_btn);
                break;
            case STEP_DONE:
                focus = stepViews[STEP_DONE];
                break;
            default:
                break;
        }
        if (focus != null) {
            focus.requestFocus();
        }
    }

    private void refreshStatus() {
        boolean active = ImeStatus.isActive(this);
        defaultStatus.setText(active
                ? R.string.firstrun_default_done
                : R.string.firstrun_default_pending);
        boolean calibrated = isCalibrated();
        calibrateStatus.setText(calibrated
                ? R.string.firstrun_calibrate_done
                : R.string.firstrun_calibrate_pending);
    }

    private boolean isCalibrated() {
        KeyMapper mapper = new KeyMapper(this);
        for (int slot = KeymapSlots.USER_MIN; slot <= KeymapSlots.USER_MAX; slot++) {
            if (mapper.isSlotConfigured(slot)) {
                return true;
            }
        }
        return false;
    }

    private void onDefaultContinue() {
        if (ImeStatus.isActive(this)) {
            showStep(STEP_CALIBRATE);
        } else {
            Toast.makeText(this, R.string.firstrun_default_toast, Toast.LENGTH_SHORT).show();
        }
    }

    private void onCalibrateContinue() {
        if (isCalibrated()) {
            showStep(STEP_DONE);
        } else {
            Toast.makeText(this, R.string.firstrun_calibrate_toast, Toast.LENGTH_SHORT).show();
        }
    }

    private void finishWizard() {
        prefs.setFirstRunCompleted(true);
        startActivity(new Intent(this, SettingsActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP));
        finish();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (step == STEP_NOTICE) {
                finish();
            } else if (step == STEP_WELCOME) {
                showStep(STEP_NOTICE);
            } else if (step == STEP_DEFAULT_IME) {
                showStep(STEP_WELCOME);
            } else if (step == STEP_CALIBRATE) {
                showStep(STEP_DEFAULT_IME);
            } else if (step == STEP_DONE) {
                showStep(STEP_CALIBRATE);
            }
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
            if (event.getRepeatCount() > 0) {
                return true;
            }
            // The notice, welcome and done pages own their OK handling here.
            // The two configuration pages have focusable buttons that consume
            // OK themselves, so this branch is normally not reached for them.
            if (step == STEP_NOTICE) {
                showStep(STEP_WELCOME);
                return true;
            }
            if (step == STEP_WELCOME) {
                showStep(STEP_DEFAULT_IME);
                return true;
            }
            if (step == STEP_DONE) {
                finishWizard();
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }
}
