package com.garaho.ime;

/** Small, platform-independent guard for late IME key events. */
final class InputEventGate {

    private InputEventGate() {
    }

    static boolean accepts(boolean inputSessionActive, boolean inputViewActive) {
        return inputSessionActive && inputViewActive;
    }
}
