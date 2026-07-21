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
            return InputAction.NONE;
        }
    }

    public KeyMapConfig getConfig() {
        synchronized (this) {
            return config;
        }
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
