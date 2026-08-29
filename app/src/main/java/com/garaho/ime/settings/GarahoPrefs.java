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
    public static final String KEY_MTAP_TIMEOUT = "mtap_timeout";
    public static final String KEY_ACTIVE_KEYMAP_SLOT = "active_keymap_slot";
    public static final String KEY_KEYMAP_LEGACY_MIGRATED = "keymap_legacy_migrated";
    private static final String KEY_KEYMAP_SLOT_NAME_PREFIX = "keymap_slot_name_";
    private static final String KEY_KEYMAP_PROMPT_DISMISSED = "keymap_prompt_dismissed";
    private static final String KEY_FULLSCREEN_COMPAT = "fullscreen_compat_packages";
    private static final String KEY_LAST_HOST_PACKAGE = "last_host_package";
    private static final String KEY_FIRSTRUN_COMPLETED = "firstrun_completed";
    private static final String KEY_CANDIDATE_SOUND_MODE = "candidate_sound_mode";
    private static final String KEY_DEFAULT_CANDIDATE_LAYER = "default_candidate_layer";
    private static final String KEY_LAST_UPDATE_CHECK = "last_update_check_epoch";
    private static final String KEY_BACK_LONG_PRESS = "back_key_long_press";

    public static final String FEEDBACK_VIBRATE = "vibrate";
    public static final String FEEDBACK_SOUND = "sound";
    public static final String FEEDBACK_NONE = "none";

    public static final String SOUND_MODE_MODERN = "modern";
    public static final String SOUND_MODE_LOOP = "loop";

    public static final String DEFAULT_LAYER_CANDIDATE = "candidate";
    public static final String DEFAULT_LAYER_PINYIN = "pinyin";

    /** Return key bound as backspace: long press collapses the IME (iWnn-style). */
    public static final String BACK_LONG_PRESS_COLLAPSE = "collapse";
    /** Return key bound as backspace: long press keeps rapid-deleting. */
    public static final String BACK_LONG_PRESS_FAST_DELETE = "fast_delete";

    // English defaults to Multi-tap: the target audience is Chinese users,
    // whose typical English input is passwords/abbreviations rather than
    // vocabulary (prediction is therefore unwanted by default).
    private static final String MODE_LOOP_DEFAULT = "ZH,EN_MTAP,NUM";
    private static final String FEEDBACK_DEFAULT = FEEDBACK_VIBRATE;
    private static final int MTAP_TIMEOUT_DEFAULT = 600;

    public static final int[] MTAP_TIMEOUT_OPTIONS = { 300, 500, 600, 800, 1000 };

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
            out.add(InputMode.EN_MTAP);
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

    public int getMultiTapTimeout() {
        return sp.getInt(KEY_MTAP_TIMEOUT, MTAP_TIMEOUT_DEFAULT);
    }

    public void setMultiTapTimeout(int ms) {
        sp.edit().putInt(KEY_MTAP_TIMEOUT, ms).apply();
    }

    public int getActiveKeymapSlot() {
        int slot = sp.getInt(KEY_ACTIVE_KEYMAP_SLOT, 0);
        return slot >= 0 && slot <= 4 ? slot : 0;
    }

    public void setActiveKeymapSlot(int slot) {
        sp.edit().putInt(KEY_ACTIVE_KEYMAP_SLOT, slot >= 0 && slot <= 4 ? slot : 0).apply();
    }

    public String getKeymapSlotName(int slot) {
        return sp.getString(KEY_KEYMAP_SLOT_NAME_PREFIX + slot, null);
    }

    public void setKeymapSlotName(int slot, String name) {
        sp.edit().putString(KEY_KEYMAP_SLOT_NAME_PREFIX + slot, name).apply();
    }

    public void clearKeymapSlotName(int slot) {
        sp.edit().remove(KEY_KEYMAP_SLOT_NAME_PREFIX + slot).apply();
    }

    public boolean isLegacyKeymapMigrated() {
        return sp.getBoolean(KEY_KEYMAP_LEGACY_MIGRATED, false);
    }

    public boolean markLegacyKeymapMigrated() {
        return sp.edit().putBoolean(KEY_KEYMAP_LEGACY_MIGRATED, true).commit();
    }

    public boolean isKeymapPromptDismissed() {
        return sp.getBoolean(KEY_KEYMAP_PROMPT_DISMISSED, false);
    }

    public void setKeymapPromptDismissed(boolean dismissed) {
        sp.edit().putBoolean(KEY_KEYMAP_PROMPT_DISMISSED, dismissed).apply();
    }

    /**
     * Host packages for which the IME opts into the framework's fullscreen/
     * extract layout (np701kc.md §15). Defaults to Notepad, whose editor the
     * framework blanks in extract mode.
     */
    public Set<String> getFullscreenCompatPackages() {
        Set<String> raw = sp.getStringSet(KEY_FULLSCREEN_COMPAT, null);
        if (raw == null) {
            // Defaults: Kyocera apps whose editor the framework blanks in
            // extract mode (np701kc.md §15).
            return new LinkedHashSet<>(Arrays.asList(
                    "jp.kyocera.memo",
                    "jp.kyocera.charactercheck"));
        }
        return new LinkedHashSet<>(raw);
    }

    public void setFullscreenCompatPackages(Set<String> packages) {
        sp.edit().putStringSet(KEY_FULLSCREEN_COMPAT, packages).apply();
    }

    public boolean isFullscreenCompatPackage(String pkg) {
        return pkg != null && getFullscreenCompatPackages().contains(pkg);
    }

    /** Most recent host editor package (updated by the IME on each onStartInput). */
    public String getLastHostPackage() {
        return sp.getString(KEY_LAST_HOST_PACKAGE, null);
    }

    public void setLastHostPackage(String pkg) {
        if (pkg != null && !pkg.isEmpty()) {
            sp.edit().putString(KEY_LAST_HOST_PACKAGE, pkg).apply();
        }
    }

    /** Whether the first-run wizard has been completed. Reset by {@link #clearAll()}. */
    public boolean isFirstRunCompleted() {
        return sp.getBoolean(KEY_FIRSTRUN_COMPLETED, false);
    }

    public void setFirstRunCompleted(boolean done) {
        sp.edit().putBoolean(KEY_FIRSTRUN_COMPLETED, done).apply();
    }

    /**
     * How the candidate-sound row behaves in Chinese T9: {@link #SOUND_MODE_MODERN}
     * (confirm a reading jumps to the word row) or {@link #SOUND_MODE_LOOP}
     * (confirm a reading advances to the next syllable, wrapping at the end).
     */
    public String getCandidateSoundMode() {
        String v = sp.getString(KEY_CANDIDATE_SOUND_MODE, SOUND_MODE_MODERN);
        return SOUND_MODE_LOOP.equals(v) ? SOUND_MODE_LOOP : SOUND_MODE_MODERN;
    }

    public void setCandidateSoundMode(String mode) {
        sp.edit().putString(KEY_CANDIDATE_SOUND_MODE, mode).apply();
    }

    public boolean isLoopCandidateSound() {
        return SOUND_MODE_LOOP.equals(getCandidateSoundMode());
    }

    /**
     * Which row the candidate cursor lands on after a digit is typed:
     * {@link #DEFAULT_LAYER_CANDIDATE} (words) or {@link #DEFAULT_LAYER_PINYIN}
     * (sound readings).
     */
    public String getDefaultCandidateLayer() {
        String v = sp.getString(KEY_DEFAULT_CANDIDATE_LAYER, DEFAULT_LAYER_CANDIDATE);
        return DEFAULT_LAYER_PINYIN.equals(v) ? DEFAULT_LAYER_PINYIN : DEFAULT_LAYER_CANDIDATE;
    }

    public void setDefaultCandidateLayer(String layer) {
        sp.edit().putString(KEY_DEFAULT_CANDIDATE_LAYER, layer).apply();
    }

    public boolean isDefaultLayerPinyin() {
        return DEFAULT_LAYER_PINYIN.equals(getDefaultCandidateLayer());
    }

    /** Epoch ms of the last completed update check (0 = never). */
    public long getLastUpdateCheck() {
        return sp.getLong(KEY_LAST_UPDATE_CHECK, 0);
    }

    public void setLastUpdateCheck(long epochMs) {
        sp.edit().putLong(KEY_LAST_UPDATE_CHECK, epochMs).apply();
    }

    /**
     * Behaviour of the return key when it is calibrated as backspace:
     * {@link #BACK_LONG_PRESS_COLLAPSE} (default, iWnn-style: tap deletes,
     * long press collapses the IME) or {@link #BACK_LONG_PRESS_FAST_DELETE}
     * (holding rapid-deletes, no collapse). Only meaningful while the system
     * back key is mapped to BACKSPACE_DELETE.
     */
    public String getBackKeyLongPress() {
        String v = sp.getString(KEY_BACK_LONG_PRESS, BACK_LONG_PRESS_COLLAPSE);
        return BACK_LONG_PRESS_FAST_DELETE.equals(v)
                ? BACK_LONG_PRESS_FAST_DELETE : BACK_LONG_PRESS_COLLAPSE;
    }

    public void setBackKeyLongPress(String mode) {
        sp.edit().putString(KEY_BACK_LONG_PRESS, mode).apply();
    }

    public boolean isBackKeyLongPressCollapse() {
        return BACK_LONG_PRESS_COLLAPSE.equals(getBackKeyLongPress());
    }

    /** Wipe every persisted preference, returning to compiled defaults. */
    public void clearAll() {
        sp.edit().clear().apply();
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
