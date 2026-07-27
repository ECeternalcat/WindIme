package com.garaho.ime.user;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.util.Locale;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Reliability coverage for {@link UserDictionary} (improvement doc §5):
 * validation, atomic writes, corrupt-file recovery and import/export.
 */
public class UserDictionaryReliabilityTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void rejectsEmpty() {
        UserDictionary d = UserDictionary.forFile(new File(tmp.getRoot(), "u.json"));
        assertEquals(StoreResult.EMPTY, d.add("", "你"));
        assertEquals(StoreResult.EMPTY, d.add("ni", ""));
        assertEquals(StoreResult.EMPTY, d.add("   ", "  "));
        assertEquals(0, d.size());
    }

    @Test
    public void rejectsTooLong() {
        UserDictionary d = UserDictionary.forFile(new File(tmp.getRoot(), "u.json"));
        StringBuilder pinyin = new StringBuilder();
        for (int i = 0; i < UserDictionary.PINYIN_MAX + 1; i++) {
            pinyin.append('a');
        }
        assertEquals(StoreResult.TOO_LONG, d.add(pinyin.toString(), "你"));
        assertEquals(StoreResult.TOO_LONG, d.add("ni", longStr(UserDictionary.WORD_MAX + 1)));
        assertEquals(0, d.size());
    }

    @Test
    public void rejectsDuplicate() {
        UserDictionary d = UserDictionary.forFile(new File(tmp.getRoot(), "u.json"));
        assertEquals(StoreResult.OK, d.add("ni", "你"));
        assertEquals(StoreResult.DUPLICATE, d.add("ni", "你"));
        assertEquals(StoreResult.DUPLICATE, d.add("NI", "你"));
        assertEquals(1, d.size());
    }

    @Test
    public void atomicWriteLeavesNoTmpFile() {
        File f = new File(tmp.getRoot(), "u.json");
        UserDictionary d = UserDictionary.forFile(f);
        d.add("ni", "你");
        assertTrue(f.exists());
        assertFalse(new File(tmp.getRoot(), "u.json.tmp").exists());
    }

    @Test
    public void corruptFileBackedUpAndStartsEmpty() throws Exception {
        File f = new File(tmp.getRoot(), "u.json");
        AtomicStore.writeAtomic(f, "{not valid json".getBytes("UTF-8"));
        UserDictionary d = UserDictionary.forFile(f);
        assertEquals(0, d.size());
        assertFalse("corrupt file moved aside", f.exists());
        assertTrue("backup preserved for recovery",
                new File(tmp.getRoot(), "u.json.corrupt").exists());
    }

    @Test
    public void loadRestoresBackupBeforeMissingFileCheck() throws Exception {
        File file = new File(tmp.getRoot(), "backup-only.json");
        AtomicStore.writeAtomic(new File(tmp.getRoot(), "backup-only.json.bak"),
                "[{\"pinyin\":\"ni\",\"word\":\"你\"}]".getBytes("UTF-8"));

        UserDictionary dictionary = UserDictionary.forFile(file);

        assertEquals(1, dictionary.size());
        assertEquals("你", dictionary.lookup("ni").get(0));
        assertTrue(file.exists());
        assertFalse(new File(tmp.getRoot(), "backup-only.json.bak").exists());
    }

    @Test
    public void exportThenImportRoundTrip() {
        File f1 = new File(tmp.getRoot(), "u1.json");
        UserDictionary d1 = UserDictionary.forFile(f1);
        d1.add("ni", "你");
        d1.add("nihao", "你好");

        File dest = new File(tmp.getRoot(), "export.json");
        assertTrue(d1.exportTo(dest));

        UserDictionary d2 = UserDictionary.forFile(new File(tmp.getRoot(), "u2.json"));
        assertEquals(0, d2.size());
        assertEquals(2, d2.importFrom(dest));
        assertEquals("你", d2.lookup("ni").get(0));
        assertEquals("你好", d2.lookup("nihao").get(0));
    }

    @Test
    public void importMergesAndDedups() {
        File f = new File(tmp.getRoot(), "u.json");
        UserDictionary d = UserDictionary.forFile(f);
        d.add("ni", "你");

        File src = new File(tmp.getRoot(), "src.json");
        UserDictionary other = UserDictionary.forFile(src);
        other.add("ni", "你");   // duplicate of existing
        other.add("hao", "好");  // new
        other.exportTo(src);

        assertEquals("only the non-duplicate entry is merged", 1, d.importFrom(src));
        assertEquals(2, d.size());
    }

    @Test
    public void importReturnsNegativeOnBadFormat() throws Exception {
        File f = new File(tmp.getRoot(), "u.json");
        UserDictionary d = UserDictionary.forFile(f);
        File src = new File(tmp.getRoot(), "bad.json");
        AtomicStore.writeAtomic(src, "not json".getBytes("UTF-8"));
        assertEquals(-1, d.importFrom(src));
    }

    @Test
    public void writeFailuresLeaveMemoryAndDiskUnchanged() {
        File file = new File(tmp.getRoot(), "u.json");
        UserDictionary d = UserDictionary.forFile(file);
        assertEquals(StoreResult.OK, d.add("ni", "你"));
        AtomicStore.setFailWritesForTests(true);
        try {
            assertEquals(StoreResult.IO_ERROR, d.add("hao", "好"));
            assertEquals(StoreResult.IO_ERROR, d.update("ni", "你", "wo", "我"));
            assertFalse(d.remove("ni", "你"));
            assertFalse(d.clear());
            assertEquals(1, d.size());
            assertEquals("你", d.lookup("ni").get(0));
        } finally {
            AtomicStore.setFailWritesForTests(false);
        }
        UserDictionary reopened = UserDictionary.forFile(file);
        assertEquals(1, reopened.size());
        assertEquals("你", reopened.lookup("ni").get(0));
    }

    @Test
    public void failedImportDoesNotCommitParsedEntries() throws Exception {
        File file = new File(tmp.getRoot(), "u.json");
        UserDictionary d = UserDictionary.forFile(file);
        assertEquals(StoreResult.OK, d.add("ni", "你"));
        File src = new File(tmp.getRoot(), "import.json");
        AtomicStore.writeAtomic(src,
                "[{\"pinyin\":\"hao\",\"word\":\"好\"}]".getBytes("UTF-8"));
        AtomicStore.setFailWritesForTests(true);
        try {
            assertEquals(-1, d.importFrom(src));
            assertEquals(1, d.size());
            assertTrue(d.lookup("hao").isEmpty());
        } finally {
            AtomicStore.setFailWritesForTests(false);
        }
    }

    @Test
    public void normalizationDoesNotDependOnDefaultLocale() {
        Locale original = Locale.getDefault();
        Locale.setDefault(new Locale("tr", "TR"));
        try {
            UserDictionary d = UserDictionary.forFile(new File(tmp.getRoot(), "locale.json"));
            assertEquals(StoreResult.OK, d.add("PINYIN", "词"));
            assertEquals("词", d.lookup("pinyin").get(0));
        } finally {
            Locale.setDefault(original);
        }
    }

    @Test
    public void legacyEntriesAreNotTruncatedByCurrentAddLimits() throws Exception {
        File file = new File(tmp.getRoot(), "legacy.json");
        String longWord = longStr(UserDictionary.WORD_MAX + 1);
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < UserDictionary.MAX_ENTRIES + 1; i++) {
            if (i > 0) json.append(',');
            json.append("{\"pinyin\":\"p").append(i)
                    .append("\",\"word\":\"w").append(i).append("\"}");
        }
        json.append(",{\"pinyin\":\"long\",\"word\":\"")
                .append(longWord).append("\"}")
                .append(",{\"pinyin\":\"p1\",\"word\":\"w1\"}]");
        AtomicStore.writeAtomic(file, json.toString().getBytes("UTF-8"));

        UserDictionary dictionary = UserDictionary.forFile(file);
        assertEquals(UserDictionary.MAX_ENTRIES + 3, dictionary.size());
        assertEquals(StoreResult.TOO_MANY, dictionary.add("new", "new"));
        assertTrue(dictionary.remove("p0", "w0"));

        UserDictionary reopened = UserDictionary.forFile(file);
        assertEquals(UserDictionary.MAX_ENTRIES + 2, reopened.size());
        assertEquals(longWord, reopened.lookup("long").get(0));
        assertEquals(2, reopened.lookup("p1").size());
    }

    private static String longStr(int n) {
        StringBuilder sb = new StringBuilder(n);
        for (int i = 0; i < n; i++) {
            sb.append('字');
        }
        return sb.toString();
    }
}
