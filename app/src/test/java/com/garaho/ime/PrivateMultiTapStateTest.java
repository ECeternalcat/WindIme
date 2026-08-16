package com.garaho.ime;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class PrivateMultiTapStateTest {

    @Test
    public void cyclesAsciiLettersByReplacingPreviousCharacter() {
        PrivateMultiTapState state = new PrivateMultiTapState();

        PrivateMultiTapState.Edit first = state.press(2, 100, 600);
        PrivateMultiTapState.Edit second = state.press(2, 200, 600);
        PrivateMultiTapState.Edit third = state.press(2, 300, 600);

        assertEquals('a', first.character);
        assertFalse(first.replacePrevious);
        assertEquals('b', second.character);
        assertTrue(second.replacePrevious);
        assertEquals('c', third.character);
        assertTrue(third.replacePrevious);
    }

    @Test
    public void differentDigitAndTimeoutAppendNewLetters() {
        PrivateMultiTapState state = new PrivateMultiTapState();
        state.press(2, 100, 600);

        PrivateMultiTapState.Edit different = state.press(3, 200, 600);
        PrivateMultiTapState.Edit timedOut = state.press(3, 801, 600);

        assertEquals('d', different.character);
        assertFalse(different.replacePrevious);
        assertEquals('d', timedOut.character);
        assertFalse(timedOut.replacePrevious);
    }

    @Test
    public void breakCycleMakesNextSameDigitAppend() {
        PrivateMultiTapState state = new PrivateMultiTapState();
        state.press(7, 100, 600);
        state.breakCycle();

        PrivateMultiTapState.Edit edit = state.press(7, 200, 600);

        assertEquals('p', edit.character);
        assertFalse(edit.replacePrevious);
    }

    @Test
    public void ignoresNonLetterDigits() {
        PrivateMultiTapState state = new PrivateMultiTapState();
        assertNull(state.press(0, 100, 600));
        assertNull(state.press(1, 100, 600));
    }

    @Test
    public void tableLockedPerLetterFromCapsState() {
        PrivateMultiTapState state = new PrivateMultiTapState();
        com.garaho.ime.engine.CapsState caps = new com.garaho.ime.engine.CapsState();

        // Pending shift: the new letter starts on the upper table and the
        // shift is consumed immediately (one-shot semantics).
        caps.shortPress();
        PrivateMultiTapState.Edit first = state.press(2, 100, 600, caps);
        assertEquals('A', first.character);
        assertFalse(caps.isPendingShift());

        // Cycling the same letter keeps the locked upper table even though the
        // global state is still lowercase.
        PrivateMultiTapState.Edit second = state.press(2, 200, 600, caps);
        PrivateMultiTapState.Edit third = state.press(2, 300, 600, caps);
        assertEquals('B', second.character);
        assertEquals('C', third.character);

        // A new letter (different digit) uses the global lowercase table.
        PrivateMultiTapState.Edit next = state.press(3, 400, 600, caps);
        assertEquals('d', next.character);
        assertFalse(next.replacePrevious);
    }

    @Test
    public void globalUpperUsesUpperTable() {
        PrivateMultiTapState state = new PrivateMultiTapState();
        com.garaho.ime.engine.CapsState caps = new com.garaho.ime.engine.CapsState();
        caps.toggleGlobal();

        assertEquals('A', state.press(2, 100, 600, caps).character);
        assertEquals('B', state.press(2, 200, 600, caps).character);

        // Reverse shift inside global-upper: exactly the next letter lower.
        caps.shortPress();
        assertEquals('d', state.press(3, 300, 600, caps).character);
        // Cycling the same letter keeps the locked lower table...
        assertEquals('e', state.press(3, 400, 600, caps).character);
        // ...and the next new letter is upper again (shift was one-shot).
        assertEquals('M', state.press(6, 500, 600, caps).character);
    }

    @Test
    public void mixedFallbackCyclesLowercaseIntoUppercase() {
        PrivateMultiTapState state = new PrivateMultiTapState();
        state.setMixedFallback(true);

        // No Caps key: same legacy abcABC cycle as English Multi-tap.
        assertEquals('a', state.press(2, 100, 600).character);
        assertEquals('b', state.press(2, 200, 600).character);
        assertEquals('c', state.press(2, 300, 600).character);
        assertEquals('A', state.press(2, 400, 600).character);
        assertEquals('B', state.press(2, 500, 600).character);

        // Global-upper via a CapsState still isolates the upper table.
        com.garaho.ime.engine.CapsState caps = new com.garaho.ime.engine.CapsState();
        caps.toggleGlobal();
        PrivateMultiTapState fresh = new PrivateMultiTapState();
        fresh.setMixedFallback(true);
        assertEquals('P', fresh.press(7, 100, 600, caps).character);
        assertEquals('Q', fresh.press(7, 200, 600, caps).character);
    }
}
