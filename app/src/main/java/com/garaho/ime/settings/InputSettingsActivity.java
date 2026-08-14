package com.garaho.ime.settings;

import com.garaho.ime.R;

import android.content.Intent;
import android.view.View;
import android.widget.AdapterView;

import java.util.List;

/**
 * Input-settings menu (design doc §2.1). Each row shows the current value and
 * is activated with OK: the mode loop opens a checkbox sub-page, the others
 * cycle/toggle in place. The list rebuilds on resume so changes made in the
 * sub-page are reflected when the user navigates back.
 */
public class InputSettingsActivity extends BaseMenuActivity {

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
        final String feedbackSummary = feedbackLabel(prefs.getFeedback());
        final String capsSummary = prefs.getAutoCapitalize()
                ? getString(R.string.value_on) : getString(R.string.value_off);
        final String mtapSummary = prefs.getMultiTapTimeout() + " ms";

        java.util.List<String> items = new java.util.ArrayList<>();
        items.add(getString(R.string.input_default_ime));
        items.add(getString(R.string.candidate_settings_entry));
        items.add(getString(R.string.input_key_feedback) + ": " + feedbackSummary);
        items.add(getString(R.string.input_auto_caps) + ": " + capsSummary);
        items.add(getString(R.string.input_mtap_interval) + ": " + mtapSummary);

        // Kyocera-only fullscreen-extract compatibility list (np701kc.md §15).
        // Shown only where the vendor Softkey Guide framework is present.
        final int fullscreenPos;
        if (com.garaho.ime.compat.SoftkeyGuideHelper.create(this) != null) {
            fullscreenPos = items.size();
            items.add(getString(R.string.input_fullscreen_compat));
        } else {
            fullscreenPos = -1;
        }

        setMenuItems(items.toArray(new String[0]), new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (position == fullscreenPos) {
                    startActivity(new Intent(InputSettingsActivity.this, FullscreenCompatActivity.class));
                    return;
                }
                switch (position) {
                    case 0:
                        startActivity(new Intent(InputSettingsActivity.this, ImeSetupActivity.class));
                        break;
                    case 1:
                        startActivity(new Intent(InputSettingsActivity.this, CandidateSettingsActivity.class));
                        break;
                    case 2:
                        cycleFeedback();
                        rebuild();
                        break;
                    case 3:
                        prefs.setAutoCapitalize(!prefs.getAutoCapitalize());
                        rebuild();
                        break;
                    case 4:
                        cycleMultiTapTimeout();
                        rebuild();
                        break;
                    default:
                        break;
                }
            }
        });
    }

    private void cycleFeedback() {
        List<String> options = GarahoPrefs.feedbackOptions();
        int idx = options.indexOf(prefs.getFeedback());
        idx = (idx + 1) % options.size();
        prefs.setFeedback(options.get(idx));
    }

    private void cycleMultiTapTimeout() {
        int[] opts = GarahoPrefs.MTAP_TIMEOUT_OPTIONS;
        int cur = prefs.getMultiTapTimeout();
        int idx = 0;
        for (int i = 0; i < opts.length; i++) {
            if (opts[i] == cur) {
                idx = i;
                break;
            }
        }
        idx = (idx + 1) % opts.length;
        prefs.setMultiTapTimeout(opts[idx]);
    }

    private String feedbackLabel(String value) {
        if (GarahoPrefs.FEEDBACK_SOUND.equals(value)) {
            return getString(R.string.feedback_sound);
        }
        if (GarahoPrefs.FEEDBACK_NONE.equals(value)) {
            return getString(R.string.feedback_none);
        }
        return getString(R.string.feedback_vibrate);
    }

    @Override
    protected int getTitleRes() {
        return R.string.settings_input;
    }
}
