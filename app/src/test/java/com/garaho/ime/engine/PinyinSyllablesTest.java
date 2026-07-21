package com.garaho.ime.engine;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PinyinSyllablesTest {

    @Test
    public void t9Encode_mapsLettersToDigitGroups() {
        assertEquals("64", PinyinSyllables.t9Encode("ni"));
        assertEquals("426", PinyinSyllables.t9Encode("hao"));
        assertEquals("948", PinyinSyllables.t9Encode("xiu"));
        assertEquals("58", PinyinSyllables.t9Encode("lv"));
        assertEquals("58", PinyinSyllables.t9Encode("lu"));
    }

    @Test
    public void syllablesForT9_returnsAllValidSyllables() {
        assertTrue(PinyinSyllables.syllablesForT9("64").contains("ni"));
        assertTrue(PinyinSyllables.syllablesForT9("64").contains("mi"));
    }

    @Test
    public void isSyllable_recognisesCommonSyllables() {
        assertTrue(PinyinSyllables.isSyllable("zhuang"));
        assertTrue(PinyinSyllables.isSyllable("ni"));
        assertFalse(PinyinSyllables.isSyllable("xyz"));
    }
}
