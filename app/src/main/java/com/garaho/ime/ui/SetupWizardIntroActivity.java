package com.garaho.ime.ui;

import com.garaho.ime.R;
import com.garaho.ime.compat.SoftkeyGuideHelper;
import com.garaho.ime.keymap.KeymapSlots;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;

/**
 * Pre-calibration notice page (design doc §3.2).
 *
 * <p>Shown before {@link SetupWizardActivity}. Presents the calibration
 * controls and the backspace / soft-key binding rules as formatted text (so
 * they are readable instead of crammed into a single highlighted line). The
 * Kyocera soft-key section appears only where the vendor Softkey Guide is
 * present. OK / ENTER proceeds to the calibration wizard; BACK cancels.
 */
public class SetupWizardIntroActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_setup_wizard_intro);
        int targetSlot = getIntent().getIntExtra(SetupWizardActivity.EXTRA_TARGET_SLOT, -1);
        if (!KeymapSlots.isUser(targetSlot)) {
            finish();
            return;
        }
        // The soft-key section is only relevant on Kyocera devices that have
        // the physical left/right soft keys below the screen.
        if (SoftkeyGuideHelper.create(this) != null) {
            View softkeySection = findViewById(R.id.wizard_intro_softkey);
            if (softkeySection != null) {
                softkeySection.setVisibility(View.VISIBLE);
            }
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
            int targetSlot = getIntent().getIntExtra(SetupWizardActivity.EXTRA_TARGET_SLOT, -1);
            startActivity(new Intent(this, SetupWizardActivity.class)
                    .putExtra(SetupWizardActivity.EXTRA_TARGET_SLOT, targetSlot));
            finish();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }
}
