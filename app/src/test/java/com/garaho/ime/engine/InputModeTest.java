package com.garaho.ime.engine;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class InputModeTest {

    @Test
    public void cyclesZhEnNum() {
        assertEquals(InputMode.EN, InputMode.ZH.next());
        assertEquals(InputMode.NUM, InputMode.EN.next());
        assertEquals(InputMode.ZH, InputMode.NUM.next());
    }

    @Test
    public void labelsAreStable() {
        assertEquals("中", InputMode.ZH.label());
        assertEquals("En", InputMode.EN.label());
        assertEquals("123", InputMode.NUM.label());
    }
}
