package com.garaho.ime;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class InputEventGateTest {

    @Test
    public void rejectsEventsAfterInputViewFinishes() {
        assertFalse(InputEventGate.accepts(true, false));
    }

    @Test
    public void rejectsEventsWithoutAnInputSession() {
        assertFalse(InputEventGate.accepts(false, true));
    }

    @Test
    public void acceptsEventsOnlyWhenSessionAndViewAreActive() {
        assertTrue(InputEventGate.accepts(true, true));
    }
}
