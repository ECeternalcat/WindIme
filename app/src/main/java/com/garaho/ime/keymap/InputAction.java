package com.garaho.ime.keymap;

/**
 * Abstract hardware-key actions used across the IME.
 *
 * <p>Per design doc §3.1.1: hardware keys are never hard-coded to behaviour; the
 * {@link KeyMapper} translates physical {@code ScanCode}/{@code KeyCode} values
 * into these abstract actions, decoupling per-vendor flip-phone key layouts
 * (Kyocera KYF, Sharp SH, Samsung W201x, etc.) from IME logic.
 */
public enum InputAction {
    INPUT_KEY_1,
    INPUT_KEY_2,
    INPUT_KEY_3,
    INPUT_KEY_4,
    INPUT_KEY_5,
    INPUT_KEY_6,
    INPUT_KEY_7,
    INPUT_KEY_8,
    INPUT_KEY_9,
    INPUT_KEY_0,
    INPUT_KEY_STAR,
    INPUT_KEY_POUND,

    TOGGLE_LANG_MODE,
    SHOW_SYMBOL_PANEL,
    SWITCH_RIME_SCHEMA,

    NAV_LEFT,
    NAV_RIGHT,
    NAV_UP,
    NAV_DOWN,

    CONFIRM_SELECTION,
    BACKSPACE_DELETE,

    NONE;

    public static InputAction safeValueOf(String name) {
        if (name == null) {
            return NONE;
        }
        try {
            return InputAction.valueOf(name);
        } catch (IllegalArgumentException ignored) {
            return NONE;
        }
    }
}
