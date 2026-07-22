package com.garaho.ime.settings;

import com.garaho.ime.R;
import com.garaho.ime.engine.InputMode;

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
        final String modeSummary = summarizeModes(prefs.getModeLoop());
        final String feedbackSummary = feedbackLabel(prefs.getFeedback());
        final String capsSummary = prefs.getAutoCapitalize()
                ? getString(R.string.value_on) : getString(R.string.value_off);
        final String mtapSummary = prefs.getMultiTapTimeout() + " ms";

        String[] items = new String[] {
                getString(R.string.input_default_ime),
                getString(R.string.input_mode_loop) + ": " + modeSummary,
                getString(R.string.input_key_feedback) + ": " + feedbackSummary,
                getString(R.string.input_auto_caps) + ": " + capsSummary,
                getString(R.string.input_mtap_interval) + ": " + mtapSummary,
        };
        setMenuItems(items, new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                switch (position) {
                    case 0:
                        startActivity(new Intent(InputSettingsActivity.this, ImeSetupActivity.class));
                        break;
                    case 1:
                        startActivity(new Intent(InputSettingsActivity.this, ModeLoopActivity.class));
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
