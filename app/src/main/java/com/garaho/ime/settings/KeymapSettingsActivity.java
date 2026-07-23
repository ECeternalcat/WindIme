package com.garaho.ime.settings;

import com.garaho.ime.R;
import com.garaho.ime.keymap.KeyMapper;

import android.content.Intent;
import android.view.View;
import android.widget.AdapterView;

/** Compact keymap settings entry; profile details live on a dedicated page. */
public class KeymapSettingsActivity extends BaseMenuActivity {

    private GarahoPrefs prefs;

    @Override
    protected void onCreate(android.os.Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = new GarahoPrefs(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        rebuild();
    }

    private void rebuild() {
        KeyMapper mapper = new KeyMapper(this);
        String[] items = new String[] {
                getString(R.string.keymap_wizard),
                getString(R.string.keymap_profiles_entry) + ": "
                        + KeymapProfilesActivity.slotName(this, mapper.getActiveSlot()),
                getString(R.string.keymap_indicator) + ": "
                        + (prefs.getShowIndicator() ? getString(R.string.value_on)
                                : getString(R.string.value_off)),
        };
        setMenuItems(items, new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (position == 0) {
                    startActivity(new Intent(KeymapSettingsActivity.this,
                            KeymapProfilesActivity.class)
                            .putExtra(KeymapProfilesActivity.EXTRA_CALIBRATION_PICKER, true));
                } else if (position == 1) {
                    startActivity(new Intent(KeymapSettingsActivity.this,
                            KeymapProfilesActivity.class));
                } else if (position == 2) {
                    prefs.setShowIndicator(!prefs.getShowIndicator());
                    rebuild();
                }
            }
        });
    }

    @Override
    protected int getTitleRes() {
        return R.string.settings_keymap;
    }
}
