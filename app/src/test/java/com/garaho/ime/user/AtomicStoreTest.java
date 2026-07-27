package com.garaho.ime.user;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class AtomicStoreTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void writeAtomicCreatesFileAndLeavesNoTmp() throws Exception {
        File target = new File(tmp.getRoot(), "a.json");
        AtomicStore.writeAtomic(target, "hello".getBytes("UTF-8"));
        assertTrue(target.exists());
        assertEquals("hello", AtomicStore.readUtf8(target));
        assertFalse(new File(tmp.getRoot(), "a.json.tmp").exists());
    }

    @Test
    public void writeAtomicOverwritesExisting() throws Exception {
        File target = new File(tmp.getRoot(), "a.json");
        AtomicStore.writeAtomic(target, "v1".getBytes("UTF-8"));
        AtomicStore.writeAtomic(target, "v2".getBytes("UTF-8"));
        assertEquals("v2", AtomicStore.readUtf8(target));
        assertFalse(new File(tmp.getRoot(), "a.json.tmp").exists());
    }

    @Test
    public void writeAtomicCreatesParentDir() throws Exception {
        File target = new File(tmp.getRoot(), "sub/dir/a.json");
        AtomicStore.writeAtomic(target, "x".getBytes("UTF-8"));
        assertEquals("x", AtomicStore.readUtf8(target));
    }

    @Test
    public void backupCorruptMovesFileAside() throws IOException {
        File target = new File(tmp.getRoot(), "a.json");
        AtomicStore.writeAtomic(target, "garbage".getBytes("UTF-8"));
        File backup = AtomicStore.backupCorrupt(target);
        assertTrue(backup.exists());
        assertEquals("a.json.corrupt", backup.getName());
        assertFalse(target.exists());
    }

    @Test
    public void backupCorruptReplacesPriorBackup() throws IOException {
        File target = new File(tmp.getRoot(), "a.json");
        File prior = new File(tmp.getRoot(), "a.json.corrupt");
        AtomicStore.writeAtomic(target, "new".getBytes("UTF-8"));
        AtomicStore.writeAtomic(prior, "old".getBytes("UTF-8"));
        File backup = AtomicStore.backupCorrupt(target);
        assertTrue(backup.exists());
        assertEquals("new", AtomicStore.readUtf8(backup));
    }

    @Test
    public void backupCorruptNullWhenMissing() {
        assertNull(AtomicStore.backupCorrupt(new File(tmp.getRoot(), "missing.json")));
    }

    @Test
    public void injectedWriteFailurePreservesExistingFile() throws Exception {
        File target = new File(tmp.getRoot(), "a.json");
        AtomicStore.writeAtomic(target, "old".getBytes("UTF-8"));
        AtomicStore.setFailWritesForTests(true);
        try {
            try {
                AtomicStore.writeAtomic(target, "new".getBytes("UTF-8"));
            } catch (IOException expected) {
                assertEquals("old", AtomicStore.readUtf8(target));
                return;
            }
        } finally {
            AtomicStore.setFailWritesForTests(false);
        }
        throw new AssertionError("Expected injected IOException");
    }

    @Test
    public void readRestoresInterruptedReplacementBackup() throws Exception {
        File target = new File(tmp.getRoot(), "a.json");
        File backup = new File(tmp.getRoot(), "a.json.bak");
        AtomicStore.writeAtomic(backup, "old".getBytes("UTF-8"));
        assertEquals("old", AtomicStore.readUtf8(target));
        assertTrue(target.exists());
        assertFalse(backup.exists());
    }

    @Test
    public void concurrentWritesToSameCanonicalPathAreCompleteAndLeaveNoResidue() throws Exception {
        final File target = new File(tmp.getRoot(), "stress.json");
        final File alias = new File(tmp.getRoot(), "." + File.separator + "stress.json");
        final int workers = 8;
        final int writesPerWorker = 40;
        final CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(workers);
        List<Future<?>> futures = new ArrayList<>();
        Set<String> inputs = new HashSet<>();
        for (int worker = 0; worker < workers; worker++) {
            for (int write = 0; write < writesPerWorker; write++) {
                inputs.add(payload(worker, write));
            }
            final int id = worker;
            futures.add(pool.submit(new Runnable() {
                @Override
                public void run() {
                    try {
                        start.await();
                        for (int write = 0; write < writesPerWorker; write++) {
                            File path = (write & 1) == 0 ? target : alias;
                            AtomicStore.writeAtomic(path, payload(id, write).getBytes("UTF-8"));
                        }
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }
            }));
        }
        start.countDown();
        for (Future<?> future : futures) {
            future.get();
        }
        pool.shutdown();

        assertTrue(inputs.contains(AtomicStore.readUtf8(target)));
        assertFalse(new File(tmp.getRoot(), "stress.json.tmp").exists());
        assertFalse(new File(tmp.getRoot(), "stress.json.bak").exists());
    }

    private static String payload(int worker, int write) {
        StringBuilder value = new StringBuilder();
        value.append(worker).append(':').append(write).append(':');
        for (int i = 0; i < 2048; i++) {
            value.append((char) ('a' + worker));
        }
        return value.toString();
    }
}
