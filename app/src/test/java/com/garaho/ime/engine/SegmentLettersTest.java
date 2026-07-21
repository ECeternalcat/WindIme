package com.garaho.ime.engine;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SegmentLettersTest {

    @Test
    public void segmentsContinuousPinyin() {
        List<String> s = T9Segmenter.segmentLetters("nihao");
        assertTrue("expected ni first, got " + s, s.get(0).equals("ni"));
        assertTrue("expected hao second, got " + s, s.size() >= 2 && s.get(1).equals("hao"));
    }

    @Test
    public void singleSyllable() {
        List<String> s = T9Segmenter.segmentLetters("ni");
        assertEquals(1, s.size());
        assertEquals("ni", s.get(0));
    }

    @Test
    public void emptyAndNull() {
        assertTrue(T9Segmenter.segmentLetters("").isEmpty());
        assertTrue(T9Segmenter.segmentLetters(null).isEmpty());
    }

    @Test
    public void skipsNonSyllablePrefix() {
        // "xx" is not a syllable; "ni" is -> skips x's, yields [ni]
        List<String> s = T9Segmenter.segmentLetters("xxni");
        assertEquals("ni", s.get(s.size() - 1));
    }
}
