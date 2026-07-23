package com.garaho.ime.settings;

/** Pure decision table for opening WindIme from its launcher icon. */
public final class LauncherImeRouting {

    public enum Route {
        SETUP,
        PICKER,
        SETTINGS
    }

    private LauncherImeRouting() {
    }

    public static Route decide(boolean enabled, boolean active) {
        if (!enabled) {
            return Route.SETUP;
        }
        return active ? Route.SETTINGS : Route.PICKER;
    }
}
