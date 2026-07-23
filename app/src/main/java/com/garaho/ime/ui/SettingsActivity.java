package com.garaho.ime.ui;

import com.garaho.ime.R;
import com.garaho.ime.settings.AboutActivity;
import com.garaho.ime.settings.BaseMenuActivity;
import com.garaho.ime.settings.InputSettingsActivity;
import com.garaho.ime.settings.KeymapSettingsActivity;
import com.garaho.ime.settings.PhraseActivity;
import com.garaho.ime.settings.ResetSettingsActivity;
import com.garaho.ime.settings.RimeSettingsActivity;
import com.garaho.ime.settings.UserDictActivity;

import android.content.Intent;
import android.view.View;
import android.widget.AdapterView;

/**
 * Main settings menu (design doc §2). Five categories, all reachable via
 * D-Pad + OK. The system IME settings gear opens this activity directly;
 * the launcher icon first passes through {@link LauncherActivity}.
 */
public class SettingsActivity extends BaseMenuActivity {

    @Override
    protected void onCreate(android.os.Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Older launchers can retain a shortcut targeting the former launcher
        // component. Handle that stale MAIN/LAUNCHER intent here as a fallback.
        if (isLauncherIntent(getIntent())) {
            startActivity(new Intent(this, LauncherActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            finish();
            return;
        }
        final String[] items = new String[] {
                getString(R.string.settings_input),
                getString(R.string.settings_keymap),
                getString(R.string.settings_rime),
                getString(R.string.settings_user_dict),
                getString(R.string.settings_phrase),
                getString(R.string.settings_reset),
                getString(R.string.settings_about),
        };
        setMenuItems(items, new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Class<?> target;
                switch (position) {
                    case 0: target = InputSettingsActivity.class; break;
                    case 1: target = KeymapSettingsActivity.class; break;
                    case 2: target = RimeSettingsActivity.class; break;
                    case 3: target = UserDictActivity.class; break;
                    case 4: target = PhraseActivity.class; break;
                    case 5: target = ResetSettingsActivity.class; break;
                    case 6: target = AboutActivity.class; break;
                    default: return;
                }
                startActivity(new Intent(SettingsActivity.this, target));
            }
        });
    }

    private static boolean isLauncherIntent(Intent intent) {
        return intent != null
                && Intent.ACTION_MAIN.equals(intent.getAction())
                && intent.hasCategory(Intent.CATEGORY_LAUNCHER);
    }

    @Override
    protected int getTitleRes() {
        return R.string.title_settings;
    }
}
