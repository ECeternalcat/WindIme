package com.garaho.ime.settings;

import android.content.ComponentName;
import android.content.Context;
import android.provider.Settings;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.util.Log;

/** Queries whether WindIme is enabled and currently selected. */
public final class ImeStatus {

    private static final String TAG = "ImeStatus";

    private ImeStatus() {
    }

    public static String imeId(Context context) {
        return new ComponentName(context.getPackageName(),
                "com.garaho.ime.GarahoImeService").flattenToShortString();
    }

    public static boolean isEnabled(Context context) {
        InputMethodManager imm = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm == null) {
            return false;
        }
        String id = imeId(context);
        for (InputMethodInfo info : imm.getEnabledInputMethodList()) {
            if (id.equals(info.getId())) {
                return true;
            }
        }
        return false;
    }

    public static boolean isActive(Context context) {
        try {
            String current = Settings.Secure.getString(
                    context.getContentResolver(), Settings.Secure.DEFAULT_INPUT_METHOD);
            String expected = imeId(context);
            boolean active = expected.equals(current);
            Log.d(TAG, "default_input_method=" + current + " expected=" + expected + " active=" + active);
            return active;
        } catch (Exception ignored) {
            return false;
        }
    }
}
