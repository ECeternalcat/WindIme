package com.garaho.ime.engine;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CapsStateTest {

    @Test
    public void startsLowercaseWithAbcLabel() {
        CapsState caps = new CapsState();
        assertFalse(caps.nextLetterUppercase());
        assertFalse(caps.isGlobalUpper());
        assertEquals("Abc", caps.label());
    }

    @Test
    public void shortPressShiftsExactlyOneLetter() {
        CapsState caps = new CapsState();
        caps.shortPress();

        assertTrue(caps.isPendingShift());
        assertTrue(caps.nextLetterUppercase());
        assertEquals("aBc", caps.label());

        caps.consumePendingShift();
        assertFalse(caps.nextLetterUppercase());
        assertEquals("Abc", caps.label());
    }

    @Test
    public void longPressTogglesGlobalCaseAndDropsPendingShift() {
        CapsState caps = new CapsState();
        caps.shortPress();
        caps.toggleGlobal();

        assertTrue(caps.isGlobalUpper());
        assertFalse(caps.isPendingShift());
        assertTrue(caps.nextLetterUppercase());
        assertEquals("ABC", caps.label());

        caps.toggleGlobal();
        assertFalse(caps.isGlobalUpper());
        assertEquals("Abc", caps.label());
    }

    @Test
    public void reverseShiftInsideGlobalUpper() {
        CapsState caps = new CapsState();
        caps.toggleGlobal();
        caps.shortPress();

        assertFalse(caps.nextLetterUppercase());
        assertEquals("AbC", caps.label());

        caps.consumePendingShift();
        assertTrue(caps.nextLetterUppercase());
        assertEquals("ABC", caps.label());
    }

    @Test
    public void resetReturnsToLowercase() {
        CapsState caps = new CapsState();
        caps.toggleGlobal();
        caps.shortPress();
        caps.reset();

        assertFalse(caps.isGlobalUpper());
        assertFalse(caps.isPendingShift());
        assertEquals("Abc", caps.label());
    }
}
