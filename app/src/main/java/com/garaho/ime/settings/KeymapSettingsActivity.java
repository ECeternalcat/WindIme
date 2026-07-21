package com.garaho.ime.settings;

import com.garaho.ime.R;
import com.garaho.ime.keymap.KeyMapper;
import com.garaho.ime.ui.SetupWizardActivity;

import android.content.Intent;
import android.view.View;
import android.widget.AdapterView;

/**
 * Input-method & keymap settings (design doc §2.2). Launches the 0-Touch
 * calibration wizard and toggles the on-candidate mode indicator.
 */
public class KeymapSettingsActivity extends BaseMenuActivity {

    private GarahoPrefs prefs;

    @Override
    protected void onCreate(android.os.Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = new GarahoPrefs(this);
        rebuild();
    }

    @Override
    protected void onResume() {
        super.onResume();
        rebuild();
    }

    private void rebuild() {
        final KeyMapper km = new KeyMapper(this);
        String profile = km.getConfig() == null ? "?" : km.getConfig().deviceProfile;

        String[] items = new String[] {
                getString(R.string.keymap_wizard),
                getString(R.string.keymap_indicator) + ": "
                        + (prefs.getShowIndicator() ? getString(R.string.value_on) : getString(R.string.value_off)),
                getString(R.string.keymap_current_preset) + ": " + profile,
        };
        setMenuItems(items, new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                switch (position) {
                    case 0:
                        startActivity(new Intent(KeymapSettingsActivity.this, SetupWizardActivity.class));
                        break;
                    case 1:
                        prefs.setShowIndicator(!prefs.getShowIndicator());
                        rebuild();
                        break;
                    default:
                        break;
                }
            }
        });
    }

    @Override
    protected int getTitleRes() {
        return R.string.settings_keymap;
    }
}
