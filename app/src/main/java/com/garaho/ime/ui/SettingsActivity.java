package com.garaho.ime.ui;

import com.garaho.ime.R;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.view.inputmethod.InputMethodManager;
import android.view.View;
import android.widget.Button;

/**
 * Minimal launcher that lets the user open the system IME picker or jump into
 * the {@link SetupWizardActivity}. Both buttons are reachable via D-Pad focus.
 */
public class SettingsActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        Button enableIme = findViewById(R.id.btn_enable_ime);
        Button pickIme = findViewById(R.id.btn_pick_ime);
        Button startWizard = findViewById(R.id.btn_start_wizard);

        enableIme.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS));
            }
        });
        pickIme.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.showInputMethodPicker();
                }
            }
        });
        startWizard.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(SettingsActivity.this, SetupWizardActivity.class));
            }
        });
    }
}
