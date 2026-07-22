package com.garaho.ime.user;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class PhraseStoreTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private PhraseStore fresh() {
        return PhraseStore.forFile(new File(tmp.getRoot(), "ph.json"));
    }

    @Test
    public void addUpdateRemove() {
        PhraseStore s = fresh();
        s.add("邮箱", "我的邮箱", "me@example.com");
        assertEquals(1, s.size());
        assertEquals("me@example.com", s.entries().get(0).text);

        s.update(0, "邮箱", "邮箱2", "two@example.com");
        assertEquals("two@example.com", s.entries().get(0).text);

        s.remove(0);
        assertEquals(0, s.size());
    }

    @Test
    public void persistsAcrossInstances() {
        File f = new File(tmp.getRoot(), "p.json");
        PhraseStore s1 = PhraseStore.forFile(f);
        s1.add("问候", "你好", "你好，最近怎么样？");
        PhraseStore s2 = PhraseStore.forFile(f);
        assertEquals(1, s2.size());
        assertTrue(s2.entries().get(0).text.contains("你好"));
    }

    @Test
    public void blankEntriesIgnored() {
        PhraseStore s = fresh();
        s.add("cat", "", "");
        assertEquals(0, s.size());
    }
}
