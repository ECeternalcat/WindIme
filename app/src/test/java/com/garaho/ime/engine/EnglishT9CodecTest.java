package com.garaho.ime.engine;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class EnglishT9CodecTest {

    @Test
    public void encodesLettersToKeypadDigits() {
        assertEquals("43556", EnglishT9Codec.encode("hello"));
        assertEquals("96753", EnglishT9Codec.encode("world"));
        assertEquals("9688823", EnglishT9Codec.encode("youtube"));
    }

    @Test
    public void ignoresCaseAndNonLetters() {
        assertEquals("43556", EnglishT9Codec.encode("HeLLo"));
        assertEquals("43556", EnglishT9Codec.encode("he.llo"));
    }

    @Test
    public void emptyInputProducesEmptyCode() {
        assertEquals("", EnglishT9Codec.encode(""));
        assertEquals("", EnglishT9Codec.encode(null));
    }

    @Test
    public void isValidDigitBoundary() {
        assertTrue(EnglishT9Codec.isValidDigit(2));
        assertTrue(EnglishT9Codec.isValidDigit(9));
        assertTrue(!EnglishT9Codec.isValidDigit(1));
        assertTrue(!EnglishT9Codec.isValidDigit(0));
    }
}
