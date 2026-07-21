package com.garaho.ime.settings;

import android.content.Context;
import android.content.SharedPreferences;

import com.garaho.ime.engine.InputMode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Persistent IME preferences (design doc §2 SettingsPage).
 *
 * <p>Backed by a single {@link SharedPreferences} file. Read by the IME
 * service at runtime (mode loop, key feedback, status indicator) and written
 * by the 0-Touch settings activities.
 */
public final class GarahoPrefs {

    public static final String FILE_NAME = "garaho_prefs";

    public static final String KEY_MODE_LOOP = "mode_loop";
    public static final String KEY_FEEDBACK = "key_feedback";
    public static final String KEY_SHOW_INDICATOR = "show_indicator";
    public static final String KEY_AUTO_CAPS = "auto_caps";

    public static final String FEEDBACK_VIBRATE = "vibrate";
    public static final String FEEDBACK_SOUND = "sound";
    public static final String FEEDBACK_NONE = "none";

    private static final String MODE_LOOP_DEFAULT = "ZH,EN,NUM";
    private static final String FEEDBACK_DEFAULT = FEEDBACK_VIBRATE;

    private final SharedPreferences sp;

    public GarahoPrefs(Context context) {
        sp = context.getApplicationContext().getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE);
    }

    /**
     * @return the ordered subset of {@link InputMode}s that the TOGGLE action
     *         cycles through. Defaults to ZH &rarr; EN &rarr; NUM.
     */
    public List<InputMode> getModeLoop() {
        String raw = sp.getString(KEY_MODE_LOOP, MODE_LOOP_DEFAULT);
        List<InputMode> out = new ArrayList<>();
        Set<InputMode> seen = new LinkedHashSet<>();
        for (String token : raw.split(",")) {
            InputMode m = parseMode(token);
            if (m != null && seen.add(m)) {
                out.add(m);
            }
        }
        if (out.isEmpty()) {
            out.add(InputMode.ZH);
            out.add(InputMode.EN);
            out.add(InputMode.NUM);
        }
        return out;
    }

    public void setModeLoop(List<InputMode> modes) {
        if (modes == null || modes.isEmpty()) {
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < modes.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(modes.get(i).name());
        }
        sp.edit().putString(KEY_MODE_LOOP, sb.toString()).apply();
    }

    public String getFeedback() {
        return sp.getString(KEY_FEEDBACK, FEEDBACK_DEFAULT);
    }

    public void setFeedback(String value) {
        sp.edit().putString(KEY_FEEDBACK, value).apply();
    }

    public boolean getShowIndicator() {
        return sp.getBoolean(KEY_SHOW_INDICATOR, true);
    }

    public void setShowIndicator(boolean value) {
        sp.edit().putBoolean(KEY_SHOW_INDICATOR, value).apply();
    }

    public boolean getAutoCapitalize() {
        return sp.getBoolean(KEY_AUTO_CAPS, false);
    }

    public void setAutoCapitalize(boolean value) {
        sp.edit().putBoolean(KEY_AUTO_CAPS, value).apply();
    }

    public static List<String> feedbackOptions() {
        return Arrays.asList(FEEDBACK_VIBRATE, FEEDBACK_SOUND, FEEDBACK_NONE);
    }

    private static InputMode parseMode(String token) {
        if (token == null) {
            return null;
        }
        try {
            return InputMode.valueOf(token.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
