package com.garaho.ime.keymap;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class KeymapSlotsTest {

    @Test
    public void validatesFactoryAndFourUserSlots() {
        assertTrue(KeymapSlots.isValid(0));
        assertTrue(KeymapSlots.isValid(4));
        assertFalse(KeymapSlots.isValid(-1));
        assertFalse(KeymapSlots.isValid(5));
        assertFalse(KeymapSlots.isUser(0));
        assertTrue(KeymapSlots.isUser(1));
        assertTrue(KeymapSlots.isUser(4));
    }

    @Test
    public void userFilenamesAreStable() {
        assertEquals("user_keymap_1.json", KeymapSlots.fileName(1));
        assertEquals("user_keymap_4.json", KeymapSlots.fileName(4));
    }

    @Test(expected = IllegalArgumentException.class)
    public void factoryHasNoWritableFilename() {
        KeymapSlots.fileName(0);
    }
}
