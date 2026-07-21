package com.garaho.ime.engine;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PinyinDictionaryTest {

    @Test
    public void singleSyllableLookup() {
        assertEquals("你", PinyinDictionary.lookup("ni").get(0));
        assertTrue(PinyinDictionary.lookup("shi").contains("是"));
    }

    @Test
    public void phraseLookup() {
        assertEquals("你好", PinyinDictionary.lookup("ni'hao").get(0));
        assertTrue(PinyinDictionary.has("shi'jie"));
    }

    @Test
    public void unknownLookupIsEmpty() {
        assertTrue(PinyinDictionary.lookup("qqq").isEmpty());
        assertFalse(PinyinDictionary.has("qqq"));
    }
}
