package com.garaho.ime.engine;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class InputModeTest {

    @Test
    public void cyclesAllFiveStates() {
        assertEquals(InputMode.ZH_MTAP, InputMode.ZH.next());
        assertEquals(InputMode.EN, InputMode.ZH_MTAP.next());
        assertEquals(InputMode.EN_MTAP, InputMode.EN.next());
        assertEquals(InputMode.NUM, InputMode.EN_MTAP.next());
        assertEquals(InputMode.ZH, InputMode.NUM.next());
    }

    @Test
    public void labelsAreStable() {
        assertEquals("中", InputMode.ZH.label());
        assertEquals("拼", InputMode.ZH_MTAP.label());
        assertEquals("En", InputMode.EN.label());
        assertEquals("Abc", InputMode.EN_MTAP.label());
        assertEquals("123", InputMode.NUM.label());
    }
}
