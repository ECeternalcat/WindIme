package com.garaho.ime.rime;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RimeLifecycleTest {

    @Test
    public void sessionIdsAreMonotonic() {
        int a = RimeLifecycle.nextSessionId();
        int b = RimeLifecycle.nextSessionId();
        int c = RimeLifecycle.nextSessionId();
        assertTrue("session ids must increase", b > a);
        assertEquals(b + 1, c);
    }

    @Test
    public void beginSessionIsSingleSlot() {
        RimeLifecycle.endSession(); // clean slate regardless of prior tests
        try {
            assertTrue(RimeLifecycle.beginSession());
            assertTrue(RimeLifecycle.isRunning());
            assertFalse("a second concurrent session must be refused",
                    RimeLifecycle.beginSession());
        } finally {
            RimeLifecycle.endSession();
        }
        assertFalse(RimeLifecycle.isRunning());
        // the slot is reusable once released
        assertTrue(RimeLifecycle.beginSession());
        RimeLifecycle.endSession();
    }

    @Test
    public void formatOmitsEmptyDetail() {
        assertEquals("Rime[#3] schema-ready", RimeLifecycle.format(3, "schema-ready", ""));
        assertEquals("Rime[#3] schema-ready", RimeLifecycle.format(3, "schema-ready", null));
    }

    @Test
    public void formatAppendsDetail() {
        assertEquals("Rime[#7] await-schema: rime_ice",
                RimeLifecycle.format(7, "await-schema", "rime_ice"));
    }
}
