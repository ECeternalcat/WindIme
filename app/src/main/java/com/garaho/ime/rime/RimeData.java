package com.garaho.ime.rime;

import android.content.Context;
import android.content.res.AssetManager;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Stages the bundled rime data (schema + dictionary + default config) from
 * {@code assets/rime/} onto the filesystem so librime can read it via
 * {@code fopen}. Re-extracts only when the bundled {@link #DATA_VERSION}
 * changes.
 *
 * <p>Per design doc §3.3.2: the shared dir holds the built-in rime preset
 * ({@code default.yaml}, {@code *.schema.yaml}, {@code *.dict.yaml}); the user
 * dir holds compiled {@code *.table.bin}/{@code *.prism.bin} and per-user data.
 */
public final class RimeData {

    private static final String TAG = "RimeData";
    private static final String ASSET_ROOT = "rime";
    private static final String SHARED_DIR_NAME = "rime";
    private static final String USER_DIR_NAME = "rime_user";
    private static final String VERSION_MARKER = ".data_version";
    public static final String DATA_VERSION = "4-rime-ice-clean-2026-07";

    private final File sharedDir;
    private final File userDir;

    public RimeData(Context context) {
        File files = context.getFilesDir();
        sharedDir = new File(files, SHARED_DIR_NAME);
        userDir = new File(files, USER_DIR_NAME);
    }

    public File getSharedDir() {
        return sharedDir;
    }

    public File getUserDir() {
        return userDir;
    }

    /** Ensure shared/user dirs exist and the shared preset is up to date. */
    public boolean ensureExtracted(Context context) {
        if (!userDir.exists()) userDir.mkdirs();
        if (!sharedDir.exists() && !sharedDir.mkdirs()) {
            Log.w(TAG, "Could not create shared dir " + sharedDir);
            return false;
        }
        if (isUpToDate()) {
            return true;
        }
        AssetManager am = context.getAssets();
        try {
            // The shared directory contains bundled, reproducible data only.
            // Clear stale schemas (for example the old luna_pinyin starter)
            // before installing a new snapshot; user learning lives separately.
            deleteContents(sharedDir);
            copyAssets(am, ASSET_ROOT, sharedDir);
            writeMarker();
            Log.i(TAG, "Rime data extracted to " + sharedDir);
            return true;
        } catch (IOException e) {
            Log.e(TAG, "Failed extracting rime data", e);
            return false;
        }
    }

    private boolean isUpToDate() {
        File marker = new File(sharedDir, VERSION_MARKER);
        if (!marker.exists()) {
            return false;
        }
        try {
            InputStream in = new java.io.FileInputStream(marker);
            try {
                byte[] buf = new byte[64];
                int n = in.read(buf);
                String s = new String(buf, 0, Math.max(0, n), "UTF-8").trim();
                return DATA_VERSION.equals(s);
            } finally {
                in.close();
            }
        } catch (IOException e) {
            return false;
        }
    }

    private void writeMarker() {
        File marker = new File(sharedDir, VERSION_MARKER);
        try {
            OutputStream out = new FileOutputStream(marker);
            try {
                out.write(DATA_VERSION.getBytes("UTF-8"));
            } finally {
                out.close();
            }
        } catch (IOException e) {
            Log.w(TAG, "Could not write version marker", e);
        }
    }

    private static void copyAssets(AssetManager am, String assetPath, File destDir) throws IOException {
        String[] entries = am.list(assetPath);
        if (entries == null) {
            return;
        }
        if (entries.length == 0) {
            return;
        }
        if (!destDir.exists() && !destDir.mkdirs()) {
            throw new IOException("Cannot mkdirs " + destDir);
        }
        for (String name : entries) {
            String childAsset = assetPath + "/" + name;
            File dest = new File(destDir, name);
            String[] sub = am.list(childAsset);
            if (sub != null && sub.length > 0) {
                copyAssets(am, childAsset, dest);
            } else {
                copyFile(am, childAsset, dest);
            }
        }
    }

    private static void copyFile(AssetManager am, String assetPath, File dest) throws IOException {
        InputStream in = am.open(assetPath);
        try {
            OutputStream out = new FileOutputStream(dest);
            try {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) {
                    out.write(buf, 0, n);
                }
            } finally {
                out.close();
            }
        } finally {
            in.close();
        }
    }

    private static void deleteContents(File dir) throws IOException {
        File[] children = dir.listFiles();
        if (children == null) {
            return;
        }
        for (File child : children) {
            deleteRecursive(child);
        }
    }

    private static void deleteRecursive(File file) throws IOException {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursive(child);
                }
            }
        }
        if (!file.delete()) {
            throw new IOException("Cannot delete stale Rime data " + file);
        }
    }
}
