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
                m.scanCode = o.optInt("scan_code", 0);
                m.keycode = o.optInt("keycode", 0);
                m.action = InputAction.safeValueOf(o.optString("action", "NONE"));
                config.mappings.add(m);
            }
        }
        return config;
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
}
