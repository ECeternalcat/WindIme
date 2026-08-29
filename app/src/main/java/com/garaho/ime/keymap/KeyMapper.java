package com.garaho.ime.keymap;

import android.content.Context;
import android.util.Log;

import com.garaho.ime.settings.GarahoPrefs;
import com.garaho.ime.user.AtomicStore;

import org.json.JSONException;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/** Resolves physical keys through an immutable factory map or one of four user slots. */
public final class KeyMapper {

    private static final String TAG = "KeyMapper";

    public static final String ASSET_DEFAULT = "garaho_keymap.json";
    public static final String USER_KEYMAP_FILE = "user_keymap.json";

    private static final Map<Integer, InputAction> STANDARD_ANDROID;
    static {
        Map<Integer, InputAction> s = new HashMap<>();
        s.put(7, InputAction.INPUT_KEY_0);
        s.put(8, InputAction.INPUT_KEY_1);
        s.put(9, InputAction.INPUT_KEY_2);
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
        STANDARD_ANDROID = Collections.unmodifiableMap(s);
    }

    private final Context context;
    private final GarahoPrefs prefs;
    private KeyMapConfig config;
    private final Map<Integer, InputAction> keyCodeMap = new HashMap<>();
    private final Map<Integer, InputAction> scanCodeMap = new HashMap<>();

    public KeyMapper(Context context) {
        this.context = context.getApplicationContext();
        prefs = new GarahoPrefs(this.context);
        migrateLegacyConfig();
        reload();
    }

    public void reload() {
        synchronized (this) {
            int slot = prefs.getActiveKeymapSlot();
            config = loadSlot(slot);
            if (config == null) {
                prefs.setActiveKeymapSlot(KeymapSlots.FACTORY);
                config = loadFactory();
            }
            keyCodeMap.clear();
            scanCodeMap.clear();
            if (config == null) {
                return;
            }
            for (KeyMapConfig.Mapping mapping : config.mappings) {
                if (mapping.action == null || mapping.action == InputAction.NONE
                        || isReservedFor(mapping.keycode, mapping.action)) {
                    continue;
                }
                if (mapping.keycode != 0) {
                    keyCodeMap.put(mapping.keycode, mapping.action);
                }
                if (mapping.scanCode != 0) {
                    scanCodeMap.put(mapping.scanCode, mapping.action);
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
            return standard == null ? InputAction.NONE : standard;
        }
    }

    public int getActiveSlot() {
        return prefs.getActiveKeymapSlot();
    }

    /** @return true when some physical key is mapped (user or factory) to {@code action}. */
    public boolean isActionBound(InputAction action) {
        synchronized (this) {
            return keyCodeMap.containsValue(action) || scanCodeMap.containsValue(action);
        }
    }

    /**
     * @return true when the system back key (KEYCODE_BACK) is explicitly
     *         mapped to BACKSPACE_DELETE - the precondition for the
     *         return-key long-press behaviour setting.
     */
    public boolean isBackKeyBoundToBackspace() {
        synchronized (this) {
            return keyCodeMap.get(android.view.KeyEvent.KEYCODE_BACK)
                    == InputAction.BACKSPACE_DELETE;
        }
    }

    public KeyMapConfig getConfig() {
        synchronized (this) {
            return config == null ? null : config.copy();
        }
    }

    public KeyMapConfig loadSlotConfig(int slot) {
        KeyMapConfig loaded = loadSlot(slot);
        return loaded == null ? null : loaded.copy();
    }

    public KeyMapConfig baseConfigForSlot(int slot) {
        KeyMapConfig loaded = loadSlot(slot);
        if (loaded == null && KeymapSlots.isUser(slot)) {
            loaded = loadFactory();
        }
        return loaded == null ? null : loaded.copy();
    }

    public boolean isSlotConfigured(int slot) {
        return KeymapSlots.isUser(slot) && loadUserSlot(slot) != null;
    }

    public boolean activateSlot(int slot) {
        if (!KeymapSlots.isValid(slot) || (KeymapSlots.isUser(slot) && !isSlotConfigured(slot))) {
            return false;
        }
        prefs.setActiveKeymapSlot(slot);
        reload();
        return true;
    }

    public boolean saveUserSlot(int slot, KeyMapConfig newConfig) {
        if (!KeymapSlots.isUser(slot) || newConfig == null) {
            return false;
        }
        try {
            String json = newConfig.toJson();
            File target = userSlotFile(slot);
            KeyMapConfig.fromJson(json);
            AtomicStore.writeAtomic(target, json.getBytes("UTF-8"));
            if (getActiveSlot() == slot) {
                reload();
            }
            return true;
        } catch (IOException | JSONException e) {
            Log.e(TAG, "Failed to save keymap slot " + slot, e);
            return false;
        }
    }

    public boolean clearUserSlot(int slot) {
        if (!KeymapSlots.isUser(slot)) {
            return false;
        }
        if (getActiveSlot() == slot) {
            prefs.setActiveKeymapSlot(KeymapSlots.FACTORY);
        }
        File file = userSlotFile(slot);
        boolean deleted = !file.exists() || file.delete();
        if (deleted) {
            prefs.clearKeymapSlotName(slot);
            reload();
        }
        return deleted;
    }

    /** Select the read-only factory map without deleting saved user slots. */
    public boolean resetToFactory() {
        prefs.setActiveKeymapSlot(KeymapSlots.FACTORY);
        reload();
        return config != null;
    }

    public boolean hasUserConfig() {
        return getActiveSlot() != KeymapSlots.FACTORY;
    }

    /** Returns true if at least one user keymap slot (1-4) has a saved configuration. */
    public boolean hasAnyUserSlot() {
        for (int slot = KeymapSlots.USER_MIN; slot <= KeymapSlots.USER_MAX; slot++) {
            if (isSlotConfigured(slot)) {
                return true;
            }
        }
        return false;
    }

    /** Returns true if the active keymap has at least one key bound to the given action. */
    public boolean hasActionBound(InputAction action) {
        synchronized (this) {
            return keyCodeMap.containsValue(action) || scanCodeMap.containsValue(action);
        }
    }

    static InputAction standardActionOf(int keyCode) {
        InputAction action = STANDARD_ANDROID.get(keyCode);
        return action == null ? InputAction.NONE : action;
    }

    public static boolean isReservedFor(int keyCode, InputAction target) {
        if (keyCode == android.view.KeyEvent.KEYCODE_STAR
                || keyCode == android.view.KeyEvent.KEYCODE_POUND) {
            return false;
        }
        InputAction standard = standardActionOf(keyCode);
        return standard != InputAction.NONE && standard != target;
    }

    private KeyMapConfig loadSlot(int slot) {
        if (slot == KeymapSlots.FACTORY) {
            return loadFactory();
        }
        return KeymapSlots.isUser(slot) ? loadUserSlot(slot) : null;
    }

    private KeyMapConfig loadFactory() {
        try (InputStream input = context.getAssets().open(ASSET_DEFAULT)) {
            return KeyMapConfig.fromJson(readFully(input));
        } catch (IOException | JSONException e) {
            Log.e(TAG, "Default keymap missing or unreadable", e);
            return null;
        }
    }

    private KeyMapConfig loadUserSlot(int slot) {
        File file = userSlotFile(slot);
        try {
            return loadUserFile(file);
        } catch (IOException | JSONException e) {
            Log.w(TAG, "User keymap slot unreadable: " + slot, e);
            return null;
        }
    }

    static KeyMapConfig loadUserFile(File file) throws IOException, JSONException {
        AtomicStore.recover(file);
        if (!file.exists()) {
            return null;
        }
        try (InputStream input = new FileInputStream(file)) {
            return KeyMapConfig.fromJson(readFully(input));
        }
    }

    private File userSlotFile(int slot) {
        return new File(context.getFilesDir(), KeymapSlots.fileName(slot));
    }

    private void migrateLegacyConfig() {
        if (prefs.isLegacyKeymapMigrated()) {
            return;
        }
        File legacy = new File(context.getFilesDir(), USER_KEYMAP_FILE);
        File firstSlot = userSlotFile(1);
        try {
            AtomicStore.recover(firstSlot);
        } catch (IOException e) {
            Log.w(TAG, "Cannot recover keymap slot 1 before migration", e);
            return;
        }
        if (!legacy.exists() || firstSlot.exists()) {
            prefs.markLegacyKeymapMigrated();
            return;
        }
        try (InputStream input = new FileInputStream(legacy)) {
            KeyMapConfig old = KeyMapConfig.fromJson(readFully(input));
            if (!saveUserSlot(1, old)) {
                return;
            }
            prefs.setKeymapSlotName(1,
                    context.getString(com.garaho.ime.R.string.keymap_user_slot_default, 1));
            prefs.setActiveKeymapSlot(1);
            legacy.delete();
        } catch (IOException | JSONException e) {
            Log.w(TAG, "Legacy keymap is unreadable; keeping factory map", e);
        }
        prefs.markLegacyKeymapMigrated();
    }

    private static String readFully(InputStream input) throws IOException {
        try (InputStream in = input) {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[4096];
            int count;
            while ((count = in.read(chunk)) > 0) {
                buffer.write(chunk, 0, count);
            }
            return buffer.toString("UTF-8");
        }
    }
}
