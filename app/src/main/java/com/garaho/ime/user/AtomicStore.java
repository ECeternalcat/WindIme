package com.garaho.ime.user;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Atomic, corruption-aware file helpers shared by {@link UserDictionary} and
 * {@link PhraseStore} (improvement doc §5).
 *
 * <p>Writes go to a {@code .tmp} sibling which is fsync'd and renamed onto the
 * target, so a crash mid-write never leaves a half-written store: the previous
 * file is either fully intact or fully replaced. When a store file is corrupt
 * on load, {@link #backupCorrupt(File)} moves it aside to {@code .corrupt} so
 * the original data is preserved for manual recovery (via ADB or import) while
 * the app starts fresh.
 *
 * <p>Pure {@code java.io} so it is unit-testable on the host JVM.
 */
public final class AtomicStore {

    /** Maximum import file size: 10 MB. Guards against OOM from malicious files. */
    static final long MAX_IMPORT_BYTES = 10L * 1024 * 1024;

    private AtomicStore() {
    }

    private static volatile boolean failWritesForTests;
    private static final ConcurrentHashMap<String, Object> PATH_LOCKS = new ConcurrentHashMap<>();

    public static void writeAtomic(File target, byte[] bytes) throws IOException {
        File normalized = normalize(target);
        synchronized (lockFor(normalized)) {
            if (failWritesForTests) {
                throw new IOException("Injected write failure");
            }
            File dir = normalized.getParentFile();
            if (dir == null) {
                throw new IOException("No parent directory for " + normalized);
            }
            if (!dir.exists() && !dir.mkdirs()) {
                throw new IOException("Cannot create directory " + dir);
            }
            recoverBackupLocked(normalized);
            File tmp = new File(dir, normalized.getName() + ".tmp");
            FileOutputStream out = new FileOutputStream(tmp);
            try {
                out.write(bytes);
                out.flush();
                try {
                    out.getFD().sync();
                } catch (IOException ignored) {
                    // sync is best-effort; some filesystems/devices reject it.
                }
            } finally {
                out.close();
            }
            if (tmp.renameTo(normalized)) {
                return;
            }

            // Some File.renameTo implementations do not replace an existing file.
            // Preserve it until the prepared replacement has been installed.
            File backup = new File(dir, normalized.getName() + ".bak");
            if (backup.exists() && !backup.delete()) {
                throw new IOException("Cannot remove stale backup " + backup);
            }
            if (!normalized.exists() || !normalized.renameTo(backup)) {
                throw new IOException("Cannot preserve existing " + normalized);
            }
            if (!tmp.renameTo(normalized)) {
                if (!backup.renameTo(normalized)) {
                    throw new IOException("Cannot install " + tmp + " or restore " + normalized);
                }
                throw new IOException("Cannot rename " + tmp + " to " + normalized);
            }
            if (!backup.delete()) {
                backup.deleteOnExit();
            }
        }
    }

    static void setFailWritesForTests(boolean fail) {
        failWritesForTests = fail;
    }

    static String readUtf8(File file) throws IOException {
        return readUtf8(file, Long.MAX_VALUE);
    }

    /**
     * Read a file as UTF-8, rejecting files larger than {@code maxBytes}.
     * Used by import paths to guard against OOM from oversized files.
     */
    static String readUtf8(File file, long maxBytes) throws IOException {
        File normalized = normalize(file);
        synchronized (lockFor(normalized)) {
            recoverBackupLocked(normalized);
            if (normalized.length() > maxBytes) {
                throw new IOException("File too large: " + normalized.length()
                        + " bytes (limit " + maxBytes + ")");
            }
            try (InputStream in = new FileInputStream(normalized)) {
                ByteArrayOutputStream buf = new ByteArrayOutputStream();
                byte[] tmp = new byte[4096];
                int n;
                while ((n = in.read(tmp)) > 0) {
                    buf.write(tmp, 0, n);
                }
                return buf.toString("UTF-8");
            }
        }
    }

    /** Restore an interrupted replacement before callers inspect target existence. */
    public static void recover(File target) throws IOException {
        File normalized = normalize(target);
        synchronized (lockFor(normalized)) {
            recoverBackupLocked(normalized);
        }
    }

    /**
     * Preserve a corrupt store file aside as {@code <name>.corrupt} for manual
     * recovery. Any previous backup is replaced.
     *
     * @return the backup file, or {@code null} if there was nothing to back up
     *         or the rename failed.
     */
    static File backupCorrupt(File file) {
        if (file == null) {
            return null;
        }
        File normalized = normalize(file);
        synchronized (lockFor(normalized)) {
            if (!normalized.exists()) {
                return null;
            }
            File backup = new File(normalized.getParentFile(), normalized.getName() + ".corrupt");
            if (backup.exists() && !backup.delete()) {
                return null;
            }
            return normalized.renameTo(backup) ? backup : null;
        }
    }

    private static void recoverBackupLocked(File target) throws IOException {
        File backup = new File(target.getParentFile(), target.getName() + ".bak");
        if (!backup.exists()) {
            return;
        }
        if (target.exists()) {
            backup.delete();
        } else if (!backup.renameTo(target)) {
            throw new IOException("Cannot restore backup " + backup);
        }
    }

    private static File normalize(File file) {
        try {
            return file.getCanonicalFile();
        } catch (IOException ignored) {
            return file.getAbsoluteFile();
        }
    }

    private static Object lockFor(File normalized) {
        String path = normalized.getPath();
        Object candidate = new Object();
        Object existing = PATH_LOCKS.putIfAbsent(path, candidate);
        return existing == null ? candidate : existing;
    }
}
