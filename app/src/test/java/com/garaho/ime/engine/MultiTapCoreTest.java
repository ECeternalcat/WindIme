package com.garaho.ime.engine;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class MultiTapCoreTest {

    @Test
    public void lowerTableCyclesTheBaseGroup() {
        assertEquals('a', MultiTapCore.letter(2, 0, MultiTapCore.MtapTable.LOWER));
        assertEquals('b', MultiTapCore.letter(2, 1, MultiTapCore.MtapTable.LOWER));
        assertEquals('c', MultiTapCore.letter(2, 2, MultiTapCore.MtapTable.LOWER));
        // wraps
        assertEquals('a', MultiTapCore.letter(2, 3, MultiTapCore.MtapTable.LOWER));
    }

    @Test
    public void upperTableIsIsolatedUppercase() {
        assertEquals('A', MultiTapCore.letter(2, 0, MultiTapCore.MtapTable.UPPER));
        assertEquals('B', MultiTapCore.letter(2, 1, MultiTapCore.MtapTable.UPPER));
        assertEquals('C', MultiTapCore.letter(2, 2, MultiTapCore.MtapTable.UPPER));
        // never falls back into lowercase
        assertEquals('A', MultiTapCore.letter(2, 3, MultiTapCore.MtapTable.UPPER));
    }

    @Test
    public void mixedTableContinuesLowercaseIntoUppercase() {
        // Legacy abcABC fallback for digit 2.
        assertEquals('a', MultiTapCore.letter(2, 0, MultiTapCore.MtapTable.MIXED));
        assertEquals('b', MultiTapCore.letter(2, 1, MultiTapCore.MtapTable.MIXED));
        assertEquals('c', MultiTapCore.letter(2, 2, MultiTapCore.MtapTable.MIXED));
        assertEquals('A', MultiTapCore.letter(2, 3, MultiTapCore.MtapTable.MIXED));
        assertEquals('B', MultiTapCore.letter(2, 4, MultiTapCore.MtapTable.MIXED));
        assertEquals('C', MultiTapCore.letter(2, 5, MultiTapCore.MtapTable.MIXED));
        assertEquals('a', MultiTapCore.letter(2, 6, MultiTapCore.MtapTable.MIXED));
    }

    @Test
    public void mixedTableOnFourLetterGroups() {
        // digit 7 = pqrs: p q r s P Q R S, then wraps.
        assertEquals('s', MultiTapCore.letter(7, 3, MultiTapCore.MtapTable.MIXED));
        assertEquals('P', MultiTapCore.letter(7, 4, MultiTapCore.MtapTable.MIXED));
        assertEquals('S', MultiTapCore.letter(7, 7, MultiTapCore.MtapTable.MIXED));
        assertEquals('p', MultiTapCore.letter(7, 8, MultiTapCore.MtapTable.MIXED));
    }

    @Test
    public void negativeIndexWrapsForward() {
        assertEquals('c', MultiTapCore.letter(2, -1, MultiTapCore.MtapTable.LOWER));
    }
}
