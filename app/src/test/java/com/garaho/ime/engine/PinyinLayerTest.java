package com.garaho.ime.engine;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class PinyinLayerTest {

    @Test
    public void twoDigitsYieldSyllablesPlusLetters() {
        // user's example: 2,4 -> ai, bi, ci, a, b, c
        PinyinLayer.LayerSegment s = PinyinLayer.segmentForLayer("24");
        assertEquals("24", s.tailDigits);
        assertEquals(java.util.Arrays.asList("ai", "bi", "ci", "a", "b", "c"), s.tailOptions);
    }

    @Test
    public void niExampleHasNiMiAndLetters() {
        PinyinLayer.LayerSegment s = PinyinLayer.segmentForLayer("64");
        assertTrue(s.tailOptions.contains("ni"));
        assertTrue(s.tailOptions.contains("mi"));
        assertTrue(s.tailOptions.contains("n"));
        assertTrue(s.tailOptions.contains("m"));
    }

    @Test
    public void multiSyllableLocksPrefixExposesLastTail() {
        // 64426 -> prefix [ni], tail = digits of "hao" = 426
        PinyinLayer.LayerSegment s = PinyinLayer.segmentForLayer("64426");
        assertEquals(1, s.prefix.size());
        assertEquals("ni", s.prefix.get(0));
        assertTrue("expected hao in options: " + s.tailOptions, s.tailOptions.contains("hao"));
    }

    @Test
    public void composeBuildsPhraseKey() {
        PinyinLayer.LayerSegment s = PinyinLayer.segmentForLayer("64426");
        String composed = PinyinLayer.compose(s.prefix, "hao");
        assertEquals("ni'hao", composed);
    }

    @Test
    public void emptyBufferIsEmpty() {
        PinyinLayer.LayerSegment s = PinyinLayer.segmentForLayer("");
        assertTrue(s.prefix.isEmpty());
        assertTrue(s.tailOptions.isEmpty());
    }
}
