package com.garaho.ime.user;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;

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
}
