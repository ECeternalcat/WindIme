package com.garaho.ime.user;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class UserDictionaryTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private UserDictionary fresh() {
        return UserDictionary.forFile(new File(tmp.getRoot(), "ud.json"));
    }

    @Test
    public void addAndLookup() {
        UserDictionary d = fresh();
        d.add("nihao", "你好");
        d.add("ni", "你");
        List<String> r = d.lookup("nihao");
        assertEquals(1, r.size());
        assertEquals("你好", r.get(0));
    }

    @Test
    public void normalizesPinyin() {
        UserDictionary d = fresh();
        d.add("ni'hao", "你好");
        // engine phrase keys use apostrophes; user may type without
        List<String> r = d.lookup("nihao");
        assertEquals("你好", r.get(0));
        assertEquals("你好", d.lookup("NI'HAO").get(0));
    }

    @Test
    public void multipleWordsSharePinyin() {
        UserDictionary d = fresh();
        d.add("ni", "你");
        d.add("ni", "拟");
        List<String> r = d.lookup("ni");
        assertEquals(2, r.size());
        assertTrue(r.contains("你"));
        assertTrue(r.contains("拟"));
    }

    @Test
    public void removeDeletesEntry() {
        UserDictionary d = fresh();
        d.add("ni", "你");
        assertTrue(d.remove("ni", "你"));
        assertEquals(0, d.lookup("ni").size());
        assertFalse(d.remove("ni", "你"));
    }

    @Test
    public void persistsAcrossInstances() {
        File f = new File(tmp.getRoot(), "persist.json");
        UserDictionary d1 = UserDictionary.forFile(f);
        d1.add("wo", "我");
        UserDictionary d2 = UserDictionary.forFile(f);
        assertEquals("我", d2.lookup("wo").get(0));
    }

    @Test
    public void entriesListsAll() {
        UserDictionary d = fresh();
        d.add("ni", "你");
        d.add("nihao", "你好");
        assertEquals(2, d.entries().size());
    }
}
