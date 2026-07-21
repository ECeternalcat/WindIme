package com.garaho.ime.engine;

/**
 * Internal input mode (design doc §3.1.1: 中/英/数字/符号).
 *
 * <p>Cycled by the {@code TOGGLE_LANG_MODE} action entirely within WindIme -
 * the old behaviour deferred to {@code switchToNextInputMethod}, which jumped
 * to a different IME. SYM is reached via {@code SHOW_SYMBOL_PANEL}, not here.
 */
public enum InputMode {
    ZH,
    EN,
    NUM;

    public InputMode next() {
        switch (this) {
            case ZH:
                return EN;
            case EN:
                return NUM;
            case NUM:
            default:
                return ZH;
        }
    }

    /** Short indicator shown in the candidate bar (doc §4.1 layout). */
    public String label() {
        switch (this) {
            case ZH:
                return "中";
            case EN:
                return "En";
            case NUM:
            default:
                return "123";
        }
    }
}
