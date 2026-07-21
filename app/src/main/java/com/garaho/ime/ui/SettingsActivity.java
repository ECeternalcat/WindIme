package com.garaho.ime.ui;

import com.garaho.ime.R;
import com.garaho.ime.settings.BaseMenuActivity;
import com.garaho.ime.settings.InputSettingsActivity;
import com.garaho.ime.settings.KeymapSettingsActivity;
import com.garaho.ime.settings.PhraseActivity;
import com.garaho.ime.settings.ResetSettingsActivity;
import com.garaho.ime.settings.UserDictActivity;

import android.content.Intent;
import android.view.View;
import android.widget.AdapterView;

/**
 * Main settings menu (design doc §2). Five categories, all reachable via
 * D-Pad + OK. This activity is the app's launcher entry, so it opens both
 * from the home-screen icon and from the system IME settings gear.
 */
public class SettingsActivity extends BaseMenuActivity {

    @Override
    protected void onCreate(android.os.Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final String[] items = new String[] {
                getString(R.string.settings_input),
                getString(R.string.settings_keymap),
                getString(R.string.settings_user_dict),
                getString(R.string.settings_phrase),
                getString(R.string.settings_reset),
        };
        setMenuItems(items, new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Class<?> target;
                switch (position) {
                    case 0: target = InputSettingsActivity.class; break;
                    case 1: target = KeymapSettingsActivity.class; break;
                    case 2: target = UserDictActivity.class; break;
                    case 3: target = PhraseActivity.class; break;
                    case 4: target = ResetSettingsActivity.class; break;
                    default: return;
                }
                startActivity(new Intent(SettingsActivity.this, target));
            }
        });
    }

    @Override
    protected int getTitleRes() {
        return R.string.title_settings;
    }
}
