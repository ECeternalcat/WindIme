package com.garaho.ime.settings;

import com.garaho.ime.R;
import com.garaho.ime.engine.InputMode;

import android.content.Intent;
import android.view.View;
import android.widget.AdapterView;

import java.util.List;

/**
 * Candidate-strip settings (design doc §2.1). Owns the mode loop (moved here
 * from the input-settings page), the candidate-sound mode (modern vs loop
 * sound-selection) and the default cursor row (words vs readings). Rows that
 * have a value cycle in place on OK; the mode loop opens its sub-page.
 */
public class CandidateSettingsActivity extends BaseMenuActivity {

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
        String modeSummary = summarizeModes(prefs.getModeLoop());
        String soundModeSummary = getString(prefs.isLoopCandidateSound()
                ? R.string.candidate_sound_mode_loop
                : R.string.candidate_sound_mode_modern);
        String defaultLayerSummary = getString(prefs.isDefaultLayerPinyin()
                ? R.string.candidate_default_layer_pinyin
                : R.string.candidate_default_layer_candidate);

        String[] items = new String[] {
                getString(R.string.input_mode_loop) + ": " + modeSummary,
                getString(R.string.candidate_sound_mode) + ": " + soundModeSummary,
                getString(R.string.candidate_default_layer) + ": " + defaultLayerSummary,
        };
        setMenuItems(items, new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                switch (position) {
                    case 0:
                        startActivity(new Intent(CandidateSettingsActivity.this, ModeLoopActivity.class));
                        break;
                    case 1:
                        prefs.setCandidateSoundMode(prefs.isLoopCandidateSound()
                                ? GarahoPrefs.SOUND_MODE_MODERN
                                : GarahoPrefs.SOUND_MODE_LOOP);
                        rebuild();
                        break;
                    case 2:
                        prefs.setDefaultCandidateLayer(prefs.isDefaultLayerPinyin()
                                ? GarahoPrefs.DEFAULT_LAYER_CANDIDATE
                                : GarahoPrefs.DEFAULT_LAYER_PINYIN);
                        rebuild();
                        break;
                    default:
                        break;
                }
            }
        });
    }

    private String summarizeModes(List<InputMode> modes) {
        if (modes.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < modes.size(); i++) {
            if (i > 0) {
                sb.append(' ');
            }
            sb.append(modes.get(i).label());
        }
        return sb.toString();
    }

    @Override
    protected int getTitleRes() {
        return R.string.candidate_settings_title;
    }
}
