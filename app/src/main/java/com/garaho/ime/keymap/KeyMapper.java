package com.garaho.ime.keymap;

import android.content.Context;
import android.util.Log;

import org.json.JSONException;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Resolves physical {@link android.view.KeyEvent}s to abstract {@link InputAction}s
 * (design doc §3.1).
 *
 * <p>Strategy:
 * <ol>
 *   <li>If a user-calibrated {@code user_keymap.json} exists in filesDir, use it.</li>
 *   <li>Otherwise fall back to the bundled asset {@code garaho_keymap.json}.</li>
 * </ol>
 *
 * Lookup prefers {@code keycode} first (stable across devices that emit standard
 * Android keycodes), then falls back to {@code scan_code} for vendor-specific
 * function keys (Mail / TV / Camera on Kyocera family, etc.).
 */
public final class KeyMapper {

    private static final String TAG = "KeyMapper";

    public static final String ASSET_DEFAULT = "garaho_keymap.json";
    public static final String USER_KEYMAP_FILE = "user_keymap.json";

    /**
     * Always-on fallback for standard Android keycodes (KEYCODE_0-9, STAR,
     * POUND, DPAD_*, ENTER, DEL). A sparse {@code user_keymap.json} from the
     * calibration wizard (which only captures function/nav keys) must never
     * leave the T9 digit pad unresponsive, so these resolve regardless of what
     * the loaded config happens to contain.
     */
    private static final Map<Integer, InputAction> STANDARD_ANDROID;
    static {
        Map<Integer, InputAction> s = new HashMap<>();
        s.put(7,  InputAction.INPUT_KEY_0);
        s.put(8,  InputAction.INPUT_KEY_1);
        s.put(9,  InputAction.INPUT_KEY_2);
        s.put(10, InputAction.INPUT_KEY_3);
        s.put(11, InputAction.INPUT_KEY_4);
        s.put(12, InputAction.INPUT_KEY_5);
        s.put(13, InputAction.INPUT_KEY_6);
        s.put(14, InputAction.INPUT_KEY_7);
        s.put(15, InputAction.INPUT_KEY_8);
        s.put(16, InputAction.INPUT_KEY_9);
        s.put(17, InputAction.INPUT_KEY_STAR);
        s.put(18, InputAction.INPUT_KEY_POUND);
        s.put(19, InputAction.NAV_UP);
        s.put(20, InputAction.NAV_DOWN);
        s.put(21, InputAction.NAV_LEFT);
        s.put(22, InputAction.NAV_RIGHT);
        s.put(23, InputAction.CONFIRM_SELECTION);
        s.put(66, InputAction.CONFIRM_SELECTION);
        s.put(67, InputAction.BACKSPACE_DELETE);
        STANDARD_ANDROID = java.util.Collections.unmodifiableMap(s);
    }

    private final Context context;
    private KeyMapConfig config;
    private final Map<Integer, InputAction> keyCodeMap = new HashMap<>();
    private final Map<Integer, InputAction> scanCodeMap = new HashMap<>();

    public KeyMapper(Context context) {
        this.context = context.getApplicationContext();
        reload();
    }

    public void reload() {
        synchronized (this) {
            config = loadConfig();
            keyCodeMap.clear();
            scanCodeMap.clear();
            if (config == null) {
                return;
            }
            for (KeyMapConfig.Mapping m : config.mappings) {
                if (m.action == null || m.action == InputAction.NONE) {
                    continue;
                }
                if (m.keycode != 0) {
                    keyCodeMap.put(m.keycode, m.action);
                }
                if (m.scanCode != 0) {
                    scanCodeMap.put(m.scanCode, m.action);
                }
            }
        }
    }

    public InputAction resolve(int keyCode, int scanCode) {
        synchronized (this) {
            InputAction byKey = keyCodeMap.get(keyCode);
            if (byKey != null) {
                return byKey;
            }
            InputAction byScan = scanCodeMap.get(scanCode);
            if (byScan != null) {
                return byScan;
            }
            InputAction standard = STANDARD_ANDROID.get(keyCode);
            if (standard != null) {
                return standard;
            }
            return InputAction.NONE;
        }
    }

    public KeyMapConfig getConfig() {
        synchronized (this) {
            return config;
        }
    }

    /**
     * @return the {@link InputAction} a keyCode is hard-reserved for by the
     *         Android-standard fallback (digits, *, #, D-Pad, OK, ENTER, DEL),
     *         or {@link InputAction#NONE} if it is a free/vendor key.
     */
    public static InputAction standardActionOf(int keyCode) {
        InputAction a = STANDARD_ANDROID.get(keyCode);
        return a == null ? InputAction.NONE : a;
    }

    /**
     * A key is reserved when the fallback binds it to a <i>different</i> core
     * action than the one being calibrated - binding it would shadow input or
     * navigation (e.g. pressing "2" to set 中英切换 would break T9 typing).
     */
    public static boolean isReservedFor(int keyCode, InputAction target) {
        InputAction std = standardActionOf(keyCode);
        return std != InputAction.NONE && std != target;
    }

    /**
     * Persist a freshly calibrated mapping to {@code user_keymap.json} (doc §3.2 step 5).
     */
    public boolean saveUserConfig(KeyMapConfig newConfig) {
        File out = new File(context.getFilesDir(), USER_KEYMAP_FILE);
        try {
            String json = newConfig.toJson();
            java.io.FileOutputStream fos = new java.io.FileOutputStream(out);
            try {
                fos.write(json.getBytes("UTF-8"));
            } finally {
                fos.close();
            }
            reload();
            return true;
        } catch (IOException | JSONException e) {
            Log.e(TAG, "Failed to save user keymap", e);
            return false;
        }
    }

    /**
     * Restore the bundled factory preset (Safe Escape Hatch, doc §5.2).
     */
    public boolean resetToFactory() {
        File f = new File(context.getFilesDir(), USER_KEYMAP_FILE);
        boolean deleted = !f.exists() || f.delete();
        reload();
        return deleted;
    }

    private KeyMapConfig loadConfig() {
        File userFile = new File(context.getFilesDir(), USER_KEYMAP_FILE);
        if (userFile.exists()) {
            try {
                String json = readFully(new FileInputStream(userFile));
                return KeyMapConfig.fromJson(json);
            } catch (IOException | JSONException e) {
                Log.w(TAG, "User keymap unreadable, falling back to asset", e);
            }
        }
        try {
            InputStream in = context.getAssets().open(ASSET_DEFAULT);
            try {
                String json = readFully(in);
                return KeyMapConfig.fromJson(json);
            } finally {
                in.close();
            }
        } catch (IOException | JSONException e) {
            Log.e(TAG, "Default asset keymap missing/unreadable", e);
            return null;
        }
    }

    private static String readFully(InputStream in) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        byte[] tmp = new byte[4096];
        int n;
        while ((n = in.read(tmp)) > 0) {
            buf.write(tmp, 0, n);
        }
        return buf.toString("UTF-8");
    }
}
