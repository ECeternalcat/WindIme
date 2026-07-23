package com.garaho.ime.user;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Reliability coverage for {@link PhraseStore} (improvement doc §5):
 * validation, de-duplication, in-place edit, atomic writes, corrupt-file
 * recovery and import/export.
 */
public class PhraseStoreReliabilityTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private PhraseStore fresh() {
        return PhraseStore.forFile(new File(tmp.getRoot(), "p.json"));
    }

    @Test
    public void requiresText() {
        PhraseStore s = fresh();
        assertEquals(StoreResult.EMPTY, s.add("邮箱", "只有名称", ""));
        assertEquals(StoreResult.EMPTY, s.add("邮箱", "", "   "));
        assertEquals(0, s.size());
    }

    @Test
    public void rejectsTooLong() {
        PhraseStore s = fresh();
        StringBuilder big = new StringBuilder();
        for (int i = 0; i < PhraseStore.TEXT_MAX + 1; i++) {
            big.append('a');
        }
        assertEquals(StoreResult.TOO_LONG, s.add("邮箱", "x", big.toString()));
        assertEquals(0, s.size());
    }

    @Test
    public void rejectsDuplicateSameCategoryText() {
        PhraseStore s = fresh();
        assertEquals(StoreResult.OK, s.add("邮箱", "label1", "me@example.com"));
        assertEquals(StoreResult.DUPLICATE, s.add("邮箱", "label2", "me@example.com"));
        assertEquals(1, s.size());
    }

    @Test
    public void allowsSameTextDifferentCategory() {
        PhraseStore s = fresh();
        assertEquals(StoreResult.OK, s.add("邮箱", "a", "hello"));
        assertEquals(StoreResult.OK, s.add("问候", "b", "hello"));
        assertEquals(2, s.size());
    }

    @Test
    public void updateExcludesSelfFromDuplicateCheck() {
        PhraseStore s = fresh();
        s.add("邮箱", "我的邮箱", "me@example.com");
        // Editing the same entry in place (even changing the label) must not
        // trip the duplicate check against itself.
        assertEquals(StoreResult.OK, s.update(0, "邮箱", "新名称", "me@example.com"));
        assertEquals("新名称", s.entries().get(0).label);
    }

    @Test
    public void updateRejectsCollisionWithOtherEntry() {
        PhraseStore s = fresh();
        s.add("邮箱", "a", "dup@example.com");
        s.add("邮箱", "b", "other@example.com");
        assertEquals(StoreResult.DUPLICATE,
                s.update(1, "邮箱", "b", "dup@example.com"));
        // original entry 1 unchanged after rejected update
        assertEquals("other@example.com", s.entries().get(1).text);
    }

    @Test
    public void atomicWriteLeavesNoTmpFile() {
        File f = new File(tmp.getRoot(), "p.json");
        PhraseStore s = PhraseStore.forFile(f);
        s.add("问候", "你好", "你好吗");
        assertTrue(f.exists());
        assertFalse(new File(tmp.getRoot(), "p.json.tmp").exists());
    }

    @Test
    public void corruptFileBackedUpAndStartsEmpty() throws Exception {
        File f = new File(tmp.getRoot(), "p.json");
        AtomicStore.writeAtomic(f, "}}}broken".getBytes("UTF-8"));
        PhraseStore s = PhraseStore.forFile(f);
        assertEquals(0, s.size());
        assertFalse(f.exists());
        assertTrue(new File(tmp.getRoot(), "p.json.corrupt").exists());
    }

    @Test
    public void exportThenImportRoundTrip() {
        File f1 = new File(tmp.getRoot(), "p1.json");
        PhraseStore s1 = PhraseStore.forFile(f1);
        s1.add("邮箱", "工作", "work@example.com");
        s1.add("问候", "早", "早上好");

        File dest = new File(tmp.getRoot(), "export.json");
        assertTrue(s1.exportTo(dest));

        PhraseStore s2 = PhraseStore.forFile(new File(tmp.getRoot(), "p2.json"));
        assertEquals(0, s2.size());
        assertEquals(2, s2.importFrom(dest));
        assertEquals(2, s2.size());
    }

    @Test
    public void importMergesAndDedups() {
        File f = new File(tmp.getRoot(), "p.json");
        PhraseStore s = PhraseStore.forFile(f);
        s.add("邮箱", "a", "x@example.com");

        File src = new File(tmp.getRoot(), "src.json");
        PhraseStore other = PhraseStore.forFile(src);
        other.add("邮箱", "a", "x@example.com"); // dup
        other.add("问候", "b", "你好");           // new
        other.exportTo(src);

        assertEquals(1, s.importFrom(src));
        assertEquals(2, s.size());
    }
}
