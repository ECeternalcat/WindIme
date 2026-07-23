package com.garaho.ime.rime;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.io.File;

/** Queues destructive Rime maintenance for the next safe Service startup. */
public final class RimeMaintenance {

    public enum Action {
        REDEPLOY,
        CLEAR_BUILD_CACHE,
        CLEAR_LEARNING
    }

    private static final String TAG = "RimeMaintenance";
    private static final String PREFS = "rime_maintenance";
    private static final String KEY_REDEPLOY = "redeploy";
    private static final String KEY_CLEAR_BUILD = "clear_build";
    private static final String KEY_CLEAR_LEARNING = "clear_learning";

    private RimeMaintenance() {
    }

    public static void enqueue(Context context, Action action) {
        SharedPreferences.Editor edit = prefs(context).edit();
        switch (action) {
            case REDEPLOY:
                edit.putBoolean(KEY_REDEPLOY, true).putBoolean(KEY_CLEAR_BUILD, true);
                break;
            case CLEAR_BUILD_CACHE:
                edit.putBoolean(KEY_CLEAR_BUILD, true);
                break;
            case CLEAR_LEARNING:
                edit.putBoolean(KEY_CLEAR_LEARNING, true);
                break;
            default:
                break;
        }
        edit.apply();
    }

    public static boolean hasPending(Context context) {
        SharedPreferences p = prefs(context);
        return p.getBoolean(KEY_REDEPLOY, false)
                || p.getBoolean(KEY_CLEAR_BUILD, false)
                || p.getBoolean(KEY_CLEAR_LEARNING, false);
    }

    public static String pendingSummary(Context context) {
        SharedPreferences p = prefs(context);
        StringBuilder out = new StringBuilder();
        append(out, p.getBoolean(KEY_REDEPLOY, false), "重新部署");
        append(out, p.getBoolean(KEY_CLEAR_BUILD, false), "清编译缓存");
        append(out, p.getBoolean(KEY_CLEAR_LEARNING, false), "清用户学习");
        return out.toString();
    }

    /** Must run before native Rime starts. Flags clear only after successful deletion. */
    public static boolean applyPending(Context context, File sharedDir, File userDir) {
        SharedPreferences p = prefs(context);
        boolean redeploy = p.getBoolean(KEY_REDEPLOY, false);
        boolean clearBuild = p.getBoolean(KEY_CLEAR_BUILD, false);
        boolean clearLearning = p.getBoolean(KEY_CLEAR_LEARNING, false);
        if (!redeploy && !clearBuild && !clearLearning) {
            return true;
        }
        boolean ok = true;
        if (redeploy) {
            ok &= deleteRecursively(sharedDir);
        }
        if (clearBuild || redeploy) {
            ok &= deleteRecursively(new File(userDir, "build"));
        }
        if (clearLearning) {
            ok &= deleteLearningFiles(userDir);
        }
        if (ok) {
            p.edit().clear().apply();
        }
        Log.i(TAG, "Applied pending maintenance redeploy=" + redeploy
                + " clearBuild=" + clearBuild + " clearLearning=" + clearLearning
                + " ok=" + ok);
        return ok;
    }

    public static long sizeOf(File file) {
        if (file == null || !file.exists()) {
            return 0L;
        }
        if (file.isFile()) {
            return file.length();
        }
        long total = 0L;
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                total += sizeOf(child);
            }
        }
        return total;
    }

    public static long learningSize(File userDir) {
        if (userDir == null || !userDir.exists()) {
            return 0L;
        }
        long total = 0L;
        File[] children = userDir.listFiles();
        if (children != null) {
            for (File child : children) {
                if (isLearningEntry(child)) {
                    total += sizeOf(child);
                }
            }
        }
        return total;
    }

    private static void append(StringBuilder out, boolean include, String label) {
        if (!include) {
            return;
        }
        if (out.length() > 0) {
            out.append(" / ");
        }
        out.append(label);
    }

    static boolean deleteLearningFiles(File root) {
        if (root == null || !root.exists()) {
            return true;
        }
        boolean ok = true;
        File[] children = root.listFiles();
        if (children == null) {
            return true;
        }
        for (File child : children) {
            if (isLearningEntry(child)) {
                ok &= deleteRecursively(child);
            }
        }
        return ok;
    }

    static boolean isLearningEntry(File file) {
        if (file == null) {
            return false;
        }
        String name = file.getName();
        return name.endsWith(".userdb") || "sync".equals(name);
    }

    private static boolean deleteRecursively(File file) {
        if (file == null || !file.exists()) {
            return true;
        }
        boolean ok = true;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    ok &= deleteRecursively(child);
                }
            }
        }
        return file.delete() && ok;
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
