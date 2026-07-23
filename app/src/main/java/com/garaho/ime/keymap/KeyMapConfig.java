package com.garaho.ime.keymap;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * POJO representation of {@code garaho_keymap.json} (design doc §3.1.2).
 *
 * <p>The file maps vendor-specific physical key codes to abstract
 * {@link InputAction}s, so the same IME binary can run on every flip-phone by
 * simply shipping a different JSON preset.
 */
public final class KeyMapConfig {

    public static final int DEFAULT_VERSION = 1;
    /** Maximum valid key/scan code value (Linux input subsystem uses 16-bit). */
    private static final int MAX_CODE_VALUE = 65535;

    public String deviceProfile;
    public int version = DEFAULT_VERSION;
    public final List<Mapping> mappings = new ArrayList<>();

    public static final class Mapping {
        public int scanCode;
        public int keycode;
        public InputAction action = InputAction.NONE;

        public Mapping() {
        }

        public Mapping(int scanCode, int keycode, InputAction action) {
            this.scanCode = scanCode;
            this.keycode = keycode;
            this.action = action;
        }
    }

    public static KeyMapConfig fromJson(String json) throws JSONException {
        KeyMapConfig config = new KeyMapConfig();
        JSONObject root = new JSONObject(json);
        config.deviceProfile = root.optString("device_profile", "Unknown");
        config.version = root.optInt("version", DEFAULT_VERSION);
        JSONArray arr = root.optJSONArray("mappings");
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                Mapping m = new Mapping();
                m.scanCode = clampCode(o.optInt("scan_code", 0));
                m.keycode = clampCode(o.optInt("keycode", 0));
                m.action = InputAction.safeValueOf(o.optString("action", "NONE"));
                config.mappings.add(m);
            }
        }
        return config;
    }

    /** Reject out-of-range key/scan codes from crafted config files (audit M-5). */
    private static int clampCode(int value) {
        if (value < 0 || value > MAX_CODE_VALUE) {
            return 0;
        }
        return value;
    }

    public String toJson() throws JSONException {
        JSONObject root = new JSONObject();
        root.put("device_profile", deviceProfile == null ? "Unknown" : deviceProfile);
        root.put("version", version);
        JSONArray arr = new JSONArray();
        for (Mapping m : mappings) {
            JSONObject o = new JSONObject();
            o.put("scan_code", m.scanCode);
            o.put("keycode", m.keycode);
            o.put("action", m.action.name());
            arr.put(o);
        }
        root.put("mappings", arr);
        return root.toString(2);
    }

    public KeyMapConfig copy() {
        KeyMapConfig copy = new KeyMapConfig();
        copy.deviceProfile = deviceProfile;
        copy.version = version;
        for (Mapping mapping : mappings) {
            copy.mappings.add(new Mapping(mapping.scanCode, mapping.keycode, mapping.action));
        }
        return copy;
    }

    public static KeyMapConfig merge(KeyMapConfig base, java.util.Map<InputAction, Mapping> replacements) {
        KeyMapConfig result = base == null ? new KeyMapConfig() : base.copy();
        if (replacements == null || replacements.isEmpty()) {
            return result;
        }
        java.util.Iterator<Mapping> iterator = result.mappings.iterator();
        while (iterator.hasNext()) {
            Mapping existing = iterator.next();
            for (Mapping replacement : replacements.values()) {
                boolean sameAction = existing.action == replacement.action;
                boolean sameKey = replacement.keycode != 0 && existing.keycode == replacement.keycode;
                boolean sameScan = replacement.scanCode != 0 && existing.scanCode == replacement.scanCode;
                if (sameAction || sameKey || sameScan) {
                    iterator.remove();
                    break;
                }
            }
        }
        for (Mapping replacement : replacements.values()) {
            result.mappings.add(new Mapping(replacement.scanCode, replacement.keycode,
                    replacement.action));
        }
        return result;
    }
}
