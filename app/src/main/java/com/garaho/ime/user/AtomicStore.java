package com.garaho.ime.user;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

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
final class AtomicStore {

    /** Maximum import file size: 10 MB. Guards against OOM from malicious files. */
    static final long MAX_IMPORT_BYTES = 10L * 1024 * 1024;

    private AtomicStore() {
    }

    static void writeAtomic(File target, byte[] bytes) throws IOException {
        File dir = target.getAbsoluteFile().getParentFile();
        if (dir == null) {
            throw new IOException("No parent directory for " + target);
        }
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException("Cannot create directory " + dir);
        }
        File tmp = new File(dir, target.getName() + ".tmp");
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
        if (!tmp.renameTo(target)) {
            // Rename can fail if the target already exists on some platforms.
            // Delete-then-rename has a small TOCTOU window (audit M-3); retry
            // once to tolerate a concurrent recreate in the private dir.
            if (target.exists() && !target.delete()) {
                throw new IOException("Cannot remove existing " + target);
            }
            if (!tmp.renameTo(target)) {
                // Second attempt after a brief yield.
                Thread.yield();
                if (target.exists() && !target.delete()) {
                    throw new IOException("Cannot remove existing " + target);
                }
                if (!tmp.renameTo(target)) {
                    throw new IOException("Cannot rename " + tmp + " to " + target);
                }
            }
        }
    }

    static String readUtf8(File file) throws IOException {
        return readUtf8(file, Long.MAX_VALUE);
    }

    /**
     * Read a file as UTF-8, rejecting files larger than {@code maxBytes}.
     * Used by import paths to guard against OOM from oversized files.
     */
    static String readUtf8(File file, long maxBytes) throws IOException {
        if (file.length() > maxBytes) {
            throw new IOException("File too large: " + file.length() + " bytes (limit " + maxBytes + ")");
        }
        try (InputStream in = new FileInputStream(file)) {
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            byte[] tmp = new byte[4096];
            int n;
            while ((n = in.read(tmp)) > 0) {
                buf.write(tmp, 0, n);
            }
            return buf.toString("UTF-8");
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
        if (file == null || !file.exists()) {
            return null;
        }
        File backup = new File(file.getParentFile(), file.getName() + ".corrupt");
        if (backup.exists() && !backup.delete()) {
            return null;
        }
        return file.renameTo(backup) ? backup : null;
    }
}
