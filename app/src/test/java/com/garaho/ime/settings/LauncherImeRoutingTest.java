package com.garaho.ime.settings;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class LauncherImeRoutingTest {

    @Test
    public void disabledImeOpensSetup() {
        assertEquals(LauncherImeRouting.Route.SETUP,
                LauncherImeRouting.decide(false, false));
    }

    @Test
    public void enabledButInactiveImeOpensPicker() {
        assertEquals(LauncherImeRouting.Route.PICKER,
                LauncherImeRouting.decide(true, false));
    }

    @Test
    public void activeImeOpensFullSettings() {
        assertEquals(LauncherImeRouting.Route.SETTINGS,
                LauncherImeRouting.decide(true, true));
    }
}
