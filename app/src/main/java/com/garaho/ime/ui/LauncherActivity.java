package com.garaho.ime.ui;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.inputmethod.InputMethodManager;

import com.garaho.ime.settings.ImeSetupActivity;
import com.garaho.ime.settings.ImeStatus;
import com.garaho.ime.settings.LauncherImeRouting;

/** Transparent launcher router that avoids showing full settings unnecessarily. */
public final class LauncherActivity extends Activity {

    private boolean pickerPending;
    private boolean pickerRequested;
    private boolean pickerTookFocus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LauncherImeRouting.Route route = LauncherImeRouting.decide(
                ImeStatus.isEnabled(this), ImeStatus.isActive(this));
        switch (route) {
            case SETUP:
                startActivity(new Intent(this, ImeSetupActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                finish();
                break;
            case PICKER:
                // Old Android builds can ignore picker requests before this
                // transparent activity owns a focused window.
                pickerPending = true;
                break;
            case SETTINGS:
            default:
                startActivity(new Intent(this, SettingsActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                finish();
                break;
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (!hasFocus && pickerRequested) {
            pickerTookFocus = true;
            return;
        }
        if (hasFocus && pickerRequested && pickerTookFocus) {
            // The system picker has closed and returned focus to our routing
            // window. Finish now; finishing immediately after requesting the
            // picker makes some Japanese Android builds dismiss it at once.
            finish();
            return;
        }
        if (!hasFocus || !pickerPending || pickerRequested) {
            return;
        }
        pickerRequested = true;
        getWindow().getDecorView().postDelayed(new Runnable() {
            @Override
            public void run() {
                InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.showInputMethodPicker();
                }
            }
        }, 100);
    }
}
