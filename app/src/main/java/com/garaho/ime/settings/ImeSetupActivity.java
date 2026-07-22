package com.garaho.ime.settings;

import com.garaho.ime.R;

import android.content.ComponentName;
import android.content.Intent;
import android.provider.Settings;
import android.view.InputDevice;
import android.view.View;
import android.widget.AdapterView;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;

import java.util.List;

/**
 * Classic IME enable + switch flow (design doc §1.2 target-environment onboarding).
 * Step 1 opens the system on-screen-keyboard settings so the user can tick
 * WindIme; step 2 opens the input-method picker to switch to it. Status markers
 * refresh on resume so the user sees their progress. Pure D-Pad operation.
 */
public class ImeSetupActivity extends BaseMenuActivity {

    @Override
    protected void onCreate(android.os.Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    protected void onResume() {
        super.onResume();
        rebuild();
    }

    private void rebuild() {
        boolean enabled = isImeEnabled();
        boolean active = isImeActive();
        String done = getString(R.string.ime_setup_done);
        String pending = getString(R.string.ime_setup_pending);
        String[] items = new String[] {
                "1. " + getString(R.string.ime_setup_step1)
                        + "  [" + (enabled ? done : pending) + "]",
                "2. " + getString(R.string.ime_setup_step2)
                        + "  [" + (active ? done : pending) + "]",
        };
        setMenuItems(items, new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                switch (position) {
                    case 0:
                        try {
                            startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS));
                        } catch (Exception ignored) {
                        }
                        break;
                    case 1:
                        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                        if (imm != null) {
                            imm.showInputMethodPicker();
                        }
                        break;
                    default:
                        break;
                }
            }
        });
    }

    private String imeId() {
        return new ComponentName(getPackageName(), "com.garaho.ime.GarahoImeService").flattenToShortString();
    }

    private boolean isImeEnabled() {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm == null) {
            return false;
        }
        String id = imeId();
        for (InputMethodInfo imi : imm.getEnabledInputMethodList()) {
            if (id.equals(imi.getId())) {
                return true;
            }
        }
        return false;
    }

    private boolean isImeActive() {
        try {
            String current = Settings.Secure.getString(getContentResolver(), Settings.Secure.DEFAULT_INPUT_METHOD);
            return imeId().equals(current);
        } catch (Exception ignored) {
            return false;
        }
    }

    @Override
    protected int getTitleRes() {
        return R.string.ime_setup_title;
    }
}
