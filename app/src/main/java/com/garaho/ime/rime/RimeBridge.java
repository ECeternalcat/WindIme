package com.garaho.ime.rime;

/**
 * JNI bridge to {@code librime.so} (design doc §3.3).
 *
 * <p>Native counterparts live in {@code app/src/main/cpp/native-lib.cpp}. The C++
 * side is a stub during Phase 1; real librime linking lands in Phase 4.
 */
public final class RimeBridge {

    private static volatile boolean sLoaded;
    private static volatile boolean sAvailable;

    private RimeBridge() {
    }

    public static boolean isLoaded() {
        return sAvailable;
    }

    private static void ensureLoaded() {
        if (sLoaded) {
            return;
        }
        synchronized (RimeBridge.class) {
            if (sLoaded) {
                return;
            }
            try {
                System.loadLibrary("garaho_rime");
                sAvailable = true;
            } catch (UnsatisfiedLinkError e) {
                sAvailable = false;
            }
            sLoaded = true;
        }
    }

    public static boolean init(String sharedDir, String userDir) {
        ensureLoaded();
        if (!sAvailable) {
            return false;
        }
        try {
            rimeInit(sharedDir, userDir);
            return true;
        } catch (UnsatisfiedLinkError e) {
            return false;
        }
    }

    public static boolean processKey(int keycode, int mask) {
        if (!sAvailable) {
            return false;
        }
        try {
            return rimeProcessKey(keycode, mask);
        } catch (UnsatisfiedLinkError e) {
            return false;
        }
    }

    public static String[] getCandidates() {
        if (!sAvailable) {
            return new String[0];
        }
        try {
            return rimeGetCandidates();
        } catch (UnsatisfiedLinkError e) {
            return new String[0];
        }
    }

    public static void commit() {
        if (!sAvailable) {
            return;
        }
        try {
            rimeCommit();
        } catch (UnsatisfiedLinkError ignored) {
        }
    }

    private static native void rimeInit(String sharedDir, String userDir);

    private static native boolean rimeProcessKey(int keycode, int mask);

    private static native String[] rimeGetCandidates();

    private static native void rimeCommit();
}
