package com.garaho.ime.engine;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MultiTapCoreTest {

    @Test
    public void groupLookup() {
        assertEquals("abc", MultiTapCore.group(2));
        assertEquals("pqrs", MultiTapCore.group(7));
        assertEquals("wxyz", MultiTapCore.group(9));
        assertEquals(null, MultiTapCore.group(1));
    }

    @Test
    public void groupSizes() {
        assertEquals(3, MultiTapCore.groupSize(2));
        assertEquals(4, MultiTapCore.groupSize(7));
        assertEquals(0, MultiTapCore.groupSize(0));
    }

    @Test
    public void letterByIndex() {
        assertEquals('a', MultiTapCore.letter(2, 0));
        assertEquals('b', MultiTapCore.letter(2, 1));
        assertEquals('c', MultiTapCore.letter(2, 2));
        assertEquals('s', MultiTapCore.letter(7, 3));
        assertEquals('z', MultiTapCore.letter(9, 3));
    }

    @Test
    public void letterWrapsAround() {
        assertEquals('a', MultiTapCore.letter(2, 3));
        assertEquals('a', MultiTapCore.letter(2, 6));
        assertEquals('c', MultiTapCore.letter(2, -1));
    }

    @Test
    public void isMultiTapDigitBoundary() {
        assertTrue(MultiTapCore.isMultiTapDigit(2));
        assertTrue(MultiTapCore.isMultiTapDigit(9));
        assertFalse(MultiTapCore.isMultiTapDigit(1));
        assertFalse(MultiTapCore.isMultiTapDigit(0));
    }
}
