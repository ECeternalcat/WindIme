package com.garaho.ime.rime;

import android.content.Context;
import android.content.SharedPreferences;

/** Persistent status shared by the IME and deployment settings UI. */
public final class RimeRuntimeStatus {

    public enum State {
        LIGHTWEIGHT,
        PREPARING,
        READY,
        FAILED
    }

    private static final String PREFS = "rime_runtime_status";
    private static final String KEY_STATE = "state";
    private static final String KEY_DETAIL = "detail";
    private static final String KEY_UPDATED_AT = "updated_at";

    private RimeRuntimeStatus() {
    }

    public static void set(Context context, State state, String detail) {
        context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_STATE, state.name())
                .putString(KEY_DETAIL, detail == null ? "" : detail)
                .putLong(KEY_UPDATED_AT, System.currentTimeMillis())
                .apply();
    }

    public static Snapshot get(Context context) {
        SharedPreferences prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        State state;
        try {
            state = State.valueOf(prefs.getString(KEY_STATE, State.LIGHTWEIGHT.name()));
        } catch (IllegalArgumentException e) {
            state = State.LIGHTWEIGHT;
        }
        return new Snapshot(state, prefs.getString(KEY_DETAIL, ""),
                prefs.getLong(KEY_UPDATED_AT, 0L));
    }

    public static final class Snapshot {
        public final State state;
        public final String detail;
        public final long updatedAt;

        Snapshot(State state, String detail, long updatedAt) {
            this.state = state;
            this.detail = detail;
            this.updatedAt = updatedAt;
        }
    }
}
