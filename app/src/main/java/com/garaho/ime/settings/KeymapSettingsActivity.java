package com.garaho.ime.settings;

import com.garaho.ime.R;
import com.garaho.ime.keymap.KeyMapper;

import android.content.Intent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Toast;

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
        boolean backBound = mapper.isBackKeyBoundToBackspace();
        String backLongPressSummary = backBound
                ? getString(prefs.isBackKeyLongPressCollapse()
                        ? R.string.back_long_press_collapse
                        : R.string.back_long_press_fast_delete)
                : getString(R.string.back_long_press_unavailable);
        String[] items = new String[] {
                getString(R.string.keymap_wizard),
                getString(R.string.keymap_back_long_press) + ": " + backLongPressSummary,
                getString(R.string.keymap_profiles_entry) + ": "
                        + KeymapProfilesActivity.slotName(this, mapper.getActiveSlot()),
                getString(R.string.keymap_indicator) + ": "
                        + (prefs.getShowIndicator() ? getString(R.string.value_on)
                                : getString(R.string.value_off)),
        };
        final boolean backBoundFinal = backBound;
        setMenuItems(items, new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (position == 0) {
                    startActivity(new Intent(KeymapSettingsActivity.this,
                            KeymapProfilesActivity.class)
                            .putExtra(KeymapProfilesActivity.EXTRA_CALIBRATION_PICKER, true));
                } else if (position == 1) {
                    if (!backBoundFinal) {
                        Toast.makeText(KeymapSettingsActivity.this,
                                R.string.back_long_press_unavailable, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    prefs.setBackKeyLongPress(prefs.isBackKeyLongPressCollapse()
                            ? GarahoPrefs.BACK_LONG_PRESS_FAST_DELETE
                            : GarahoPrefs.BACK_LONG_PRESS_COLLAPSE);
                    rebuild();
                } else if (position == 2) {
                    startActivity(new Intent(KeymapSettingsActivity.this,
                            KeymapProfilesActivity.class));
                } else if (position == 3) {
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
