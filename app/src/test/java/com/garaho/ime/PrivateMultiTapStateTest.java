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
}
