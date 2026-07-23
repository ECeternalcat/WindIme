package com.garaho.ime.keymap;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class KeyMapperReservedTest {

    @Test
    public void digitsAndNavAreReserved() {
        // digits 0-9 (7-16) must stay bound to T9 input
        assertTrue(KeyMapper.isReservedFor(9, InputAction.TOGGLE_LANG_MODE));
        assertTrue(KeyMapper.isReservedFor(16, InputAction.SHOW_SYMBOL_PANEL));
        // D-Pad + OK + ENTER stay reserved
        assertTrue(KeyMapper.isReservedFor(19, InputAction.TOGGLE_LANG_MODE));
        assertTrue(KeyMapper.isReservedFor(22, InputAction.TOGGLE_LANG_MODE));
        assertTrue(KeyMapper.isReservedFor(23, InputAction.SHOW_SYMBOL_PANEL));
        assertTrue(KeyMapper.isReservedFor(66, InputAction.SHOW_SYMBOL_PANEL));
    }

    @Test
    public void starAndPoundAreBindable() {
        // * and # double as symbol / enter on Japanese flip-phones -> free
        assertFalse(KeyMapper.isReservedFor(17, InputAction.SHOW_SYMBOL_PANEL));
        assertFalse(KeyMapper.isReservedFor(18, InputAction.TOGGLE_LANG_MODE));
        assertFalse(KeyMapper.isReservedFor(17, InputAction.TOGGLE_LANG_MODE));
    }

    @Test
    public void standardDelAcceptedForBackspaceStep() {
        // pressing the standard DEL to confirm the backspace step is allowed
        assertFalse(KeyMapper.isReservedFor(67, InputAction.BACKSPACE_DELETE));
    }

    @Test
    public void standardConfirmKeysAcceptedForConfirmStep() {
        assertFalse(KeyMapper.isReservedFor(23, InputAction.CONFIRM_SELECTION));
        assertFalse(KeyMapper.isReservedFor(66, InputAction.CONFIRM_SELECTION));
    }

    @Test
    public void vendorKeysAreFree() {
        // F1 (131) etc. are genuine function keys - never reserved
        assertFalse(KeyMapper.isReservedFor(131, InputAction.TOGGLE_LANG_MODE));
        assertFalse(KeyMapper.isReservedFor(133, InputAction.SHOW_SYMBOL_PANEL));
        assertFalse(KeyMapper.isReservedFor(131, InputAction.SHOW_QUICK_MENU));
    }
}
