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
    ZH_MTAP,
    EN,
    EN_MTAP,
    NUM;

    public InputMode next() {
        switch (this) {
            case ZH:
                return ZH_MTAP;
            case ZH_MTAP:
                return EN;
            case EN:
                return EN_MTAP;
            case EN_MTAP:
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
            case ZH_MTAP:
                return "拼";
            case EN:
                return "En";
            case EN_MTAP:
                return "Abc";
            case NUM:
            default:
                return "123";
        }
    }
}
