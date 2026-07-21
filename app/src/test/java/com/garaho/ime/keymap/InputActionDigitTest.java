package com.garaho.ime.keymap;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class InputActionDigitTest {

    @Test
    public void digitMapsAllKeysCorrectly() {
        assertEquals(0, InputAction.INPUT_KEY_0.digit());
        assertEquals(1, InputAction.INPUT_KEY_1.digit());
        assertEquals(2, InputAction.INPUT_KEY_2.digit());
        assertEquals(3, InputAction.INPUT_KEY_3.digit());
        assertEquals(4, InputAction.INPUT_KEY_4.digit());
        assertEquals(5, InputAction.INPUT_KEY_5.digit());
        assertEquals(6, InputAction.INPUT_KEY_6.digit());
        assertEquals(7, InputAction.INPUT_KEY_7.digit());
        assertEquals(8, InputAction.INPUT_KEY_8.digit());
        assertEquals(9, InputAction.INPUT_KEY_9.digit());
    }

    @Test
    public void nonDigitActionsReturnMinusOne() {
        assertEquals(-1, InputAction.INPUT_KEY_STAR.digit());
        assertEquals(-1, InputAction.INPUT_KEY_POUND.digit());
        assertEquals(-1, InputAction.CONFIRM_SELECTION.digit());
        assertEquals(-1, InputAction.NAV_UP.digit());
        assertEquals(-1, InputAction.NONE.digit());
    }

    @Test
    public void digitIsImmuneToEnumOrder() {
        // INPUT_KEY_0 is declared AFTER INPUT_KEY_1..9 in the enum, so naive
        // ordinal math (action.ordinal() - INPUT_KEY_0.ordinal()) yields
        // negatives for keys 1-9. digit() must not depend on that.
        for (int d = 1; d <= 9; d++) {
            assertEquals(d, InputAction.valueOf("INPUT_KEY_" + d).digit());
        }
    }
}
