package com.garaho.ime;

import com.garaho.ime.engine.CapsState;
import com.garaho.ime.engine.MultiTapCore;

/**
 * Pure state for private ASCII multi-tap input; it never retains completed
 * text. The cycling table is locked per letter: it is taken from the shared
 * {@link CapsState} when a new letter starts (consuming any one-shot shift)
 * and stays fixed while the same letter is cycled.
 */
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
    private boolean letterUppercase;

    Edit press(int digit, long eventTime, int timeoutMs) {
        return press(digit, eventTime, timeoutMs, null);
    }

    /**
     * @param caps shared case state; consulted only when a new letter starts
     *             (its one-shot shift is consumed there).
     */
    Edit press(int digit, long eventTime, int timeoutMs, CapsState caps) {
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
            if (caps != null) {
                letterUppercase = caps.nextLetterUppercase();
                caps.consumePendingShift();
            } else {
                letterUppercase = false;
            }
        }
        lastPressTime = eventTime;
        char c = MultiTapCore.letter(digit, index, letterUppercase
                ? MultiTapCore.MtapTable.UPPER : MultiTapCore.MtapTable.LOWER);
        return new Edit(c, replace);
    }

    void breakCycle() {
        lastDigit = -1;
        index = 0;
        lastPressTime = 0;
    }
}
