package com.garaho.ime;

import com.garaho.ime.engine.MultiTapCore;

/** Pure state for private ASCII multi-tap input; it never retains completed text. */
final class PrivateMultiTapState {

    static final class Edit {
        final char character;
        final boolean replacePrevious;

        Edit(char character, boolean replacePrevious) {
            this.character = character;
            this.replacePrevious = replacePrevious;
        }
    }

    private int lastDigit = -1;
    private int index;
    private long lastPressTime;

    Edit press(int digit, long eventTime, int timeoutMs) {
        if (!MultiTapCore.isMultiTapDigit(digit)) {
            return null;
        }
        boolean replace = digit == lastDigit
                && eventTime >= lastPressTime
                && eventTime - lastPressTime <= timeoutMs;
        if (replace) {
            index++;
        } else {
            lastDigit = digit;
            index = 0;
        }
        lastPressTime = eventTime;
        return new Edit(MultiTapCore.letter(digit, index), replace);
    }

    void breakCycle() {
        lastDigit = -1;
        index = 0;
        lastPressTime = 0;
    }
}
