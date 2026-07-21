package com.garaho.ime.engine;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class T9SegmenterBestEffortTest {

    @Test
    public void fullSegmentationConsumesWholeBuffer() {
        // "64426" fully segments (greedy result may differ from the
        // dict-ranked full segmentation, but the whole buffer is consumed).
        T9Segmenter.Segment seg = T9Segmenter.bestEffort("64426");
        assertEquals("", seg.remainder);
        assertTrue("expected a multi-syllable phrase, got " + seg.phraseKey,
                seg.phraseKey.contains("'"));
    }

    @Test
    public void partialInputSegmentsPrefixLeavesRemainder() {
        // "789": 78 -> a syllable (pu/qu/ru), 9 -> no syllable yet -> remainder "9"
        T9Segmenter.Segment seg = T9Segmenter.bestEffort("789");
        assertEquals("9", seg.remainder);
        assertTrue("expected a single 2-letter syllable for 78, got " + seg.phraseKey,
                seg.phraseKey.equals("pu") || seg.phraseKey.equals("qu") || seg.phraseKey.equals("ru"));
    }

    @Test
    public void noSyllableAtAllReturnsEmptyPhrase() {
        // "1" is not a pinyin digit; nothing segments
        T9Segmenter.Segment seg = T9Segmenter.bestEffort("111");
        assertEquals("", seg.phraseKey);
        assertEquals("111", seg.remainder);
    }

    @Test
    public void emptyInput() {
        T9Segmenter.Segment seg = T9Segmenter.bestEffort("");
        assertEquals("", seg.phraseKey);
        assertEquals("", seg.remainder);
    }

    @Test
    public void prefersCommonSyllable() {
        // "64" -> ni (7 dict entries) preferred over mi (6) so 你 surfaces first
        T9Segmenter.Segment seg = T9Segmenter.bestEffort("64");
        assertEquals("ni", seg.phraseKey);
        assertEquals("", seg.remainder);
    }
}
