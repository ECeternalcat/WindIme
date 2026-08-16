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
    /**
     * Legacy fallback when no Caps key is calibrated (same rule as English
     * Multi-tap): the lowercase cycle continues into uppercase (a b c A B C),
     * the case carried by the selection itself.
     */
    private boolean mixedFallback;

    Edit press(int digit, long eventTime, int timeoutMs) {
        return press(digit, eventTime, timeoutMs, null);
    }

    /** @param caps shared case state, consulted when a new letter starts. */
    Edit press(int digit, long eventTime, int timeoutMs, CapsState caps) {
        if (!MultiTapCore.isMultiTapDigit(digit)) {
            return null;
        }
        boolean replace = digit == lastDigit
                && eventTime >= lastPressTime
                && eventTime - lastPressTime <= timeoutMs;
        MultiTapCore.MtapTable table;
        if (replace) {
            index++;
            table = currentTable();
        } else {
            lastDigit = digit;
            index = 0;
            if (caps != null) {
                letterUppercase = caps.nextLetterUppercase();
                caps.consumePendingShift();
            } else {
                letterUppercase = false;
            }
            table = currentTable();
        }
        lastPressTime = eventTime;
        char c = MultiTapCore.letter(digit, index, table);
        return new Edit(c, replace);
    }

    private MultiTapCore.MtapTable currentTable() {
        if (letterUppercase) {
            return MultiTapCore.MtapTable.UPPER;
        }
        return mixedFallback ? MultiTapCore.MtapTable.MIXED : MultiTapCore.MtapTable.LOWER;
    }

    /** No Caps key calibrated -> enable the abcABC mixed-cycle fallback. */
    void setMixedFallback(boolean fallback) {
        this.mixedFallback = fallback;
    }

    void breakCycle() {
        lastDigit = -1;
        index = 0;
        lastPressTime = 0;
    }
}
