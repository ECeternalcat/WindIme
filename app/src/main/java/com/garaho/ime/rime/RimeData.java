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
        ensureInstallationYaml(context);
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
            writeMarker(sharedDir);
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
        try (InputStream in = new java.io.FileInputStream(marker)) {
            byte[] buf = new byte[64];
            int n = in.read(buf);
            String s = new String(buf, 0, Math.max(0, n), "UTF-8").trim();
            return DATA_VERSION.equals(s);
        } catch (IOException e) {
            return false;
        }
    }

    static void writeMarker(File directory) throws IOException {
        File marker = new File(directory, VERSION_MARKER);
        try (OutputStream out = new FileOutputStream(marker)) {
            out.write(DATA_VERSION.getBytes("UTF-8"));
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
        try (InputStream in = am.open(assetPath);
             OutputStream out = new FileOutputStream(dest)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
        }
    }

    /**
     * Create {@code installation.yaml} in the user dir if absent. librime
     * requires this file for user-dict sync; without it the sync_dir resolves
     * to an empty string, producing the "Error opening db '.temp'" failure.
     */
    private void ensureInstallationYaml(Context context) {
        File inst = new File(userDir, "installation.yaml");
        if (inst.exists()) {
            return;
        }
        File syncDir = new File(userDir, "sync");
        if (!syncDir.exists()) syncDir.mkdirs();
        String deviceId = android.provider.Settings.Secure.getString(
                context.getContentResolver(),
                android.provider.Settings.Secure.ANDROID_ID);
        if (deviceId == null || deviceId.isEmpty()) {
            deviceId = "unknown_" + Long.toHexString(System.currentTimeMillis());
        }
        String content = "distribution_code_name: WindIme\n"
                + "distribution_name: WindIme\n"
                + "distribution_version: " + DATA_VERSION + "\n"
                + "install_time: " + (System.currentTimeMillis() / 1000) + "\n"
                + "installation_id: windime_" + deviceId + "\n"
                + "sync_dir: " + syncDir.getAbsolutePath() + "\n";
        try (OutputStream out = new FileOutputStream(inst)) {
            out.write(content.getBytes("UTF-8"));
            Log.i(TAG, "Created installation.yaml, sync_dir=" + syncDir);
        } catch (IOException e) {
            Log.w(TAG, "Could not write installation.yaml", e);
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
