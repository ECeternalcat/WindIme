package com.osfans.trime.core;

import android.util.Log;

/**
 * Java-side mirror of Trime's {@code com.osfans.trime.core.Rime} JNI surface.
 *
 * <p>The prebuilt {@code librime_jni.so} exports its native methods against the
 * {@code com.osfans.trime.core.Rime} class name (see
 * {@code trimelib/trime-develop/.../librime_jni/rime_jni.cc}), so this class
 * must live in exactly that package for {@link System#loadLibrary} to resolve
 * symbols. The accompanying {@code *Proto} classes are required because the
 * native {@code JNI_OnLoad} eagerly resolves them via {@code FindClass}.
 *
 * <p>Unlike Trime, the library is <b>not</b> loaded in a static initializer:
 * the prebuilt ships only for {@code armeabi-v7a}, so loading is lazy and
 * fallible ({@link #loadLibrary()}) so callers can fall back to a pure-Java
 * engine on other ABIs.
 */
public final class Rime {

    private static final String TAG = "RimeJNI";
    private static volatile boolean sLoaded = false;
    private static volatile boolean sLoadAttempted = false;

    private Rime() {
    }

    public static boolean loadLibrary() {
        if (sLoaded) {
            return true;
        }
        if (sLoadAttempted) {
            return false;
        }
        synchronized (Rime.class) {
            if (sLoaded) {
                return true;
            }
            if (sLoadAttempted) {
                return false;
            }
            sLoadAttempted = true;
            try {
                System.loadLibrary("rime_jni");
                sLoaded = true;
                Log.i(TAG, "librime_jni.so loaded");
                return true;
            } catch (UnsatisfiedLinkError | SecurityException e) {
                Log.w(TAG, "librime_jni.so unavailable: " + e.getMessage());
                return false;
            }
        }
    }

    public static boolean isLoaded() {
        return sLoaded;
    }

    public static native void startupRime(String sharedDir, String userDir, String versionName, boolean fullCheck);

    public static native void exitRime();

    public static native boolean deployRimeSchemaFile(String schemaFile);

    public static native boolean deployRimeConfigFile(String fileName, String versionKey);

    public static native boolean syncRimeUserData();

    public static native boolean processRimeKey(int keycode, int mask);

    public static native boolean commitRimeComposition();

    public static native void clearRimeComposition();

    public static native CommitProto getRimeCommit();

    public static native ContextProto getRimeContext();

    public static native StatusProto getRimeStatus();

    public static native void setRimeOption(String option, boolean value);

    public static native boolean getRimeOption(String option);

    public static native SchemaItem[] getRimeSchemaList();

    public static native String getCurrentRimeSchema();

    public static native boolean selectRimeSchema(String schemaId);

    public static native boolean simulateRimeKeySequence(String keySequence);

    public static native String getRimeRawInput();

    public static native int getRimeCaretPos();

    public static native void setRimeCaretPos(int caretPos);

    public static native boolean selectRimeCandidate(int index, boolean global);

    public static native boolean deleteRimeCandidate(int index, boolean global);

    public static native boolean changeRimeCandidatePage(boolean backward);

    public static native CandidateProto[] getRimeCandidates(int startIndex, int limit);

    public static native Object[] getRimeBulkCandidates();

    /**
     * No-op sink for the native notification callback. Trime's rime_jni
     * installs a handler that calls back into this static method; we poll the
     * engine state instead, so notifications are intentionally ignored.
     */
    public static void handleRimeMessage(int type, Object[] params) {
    }
}
