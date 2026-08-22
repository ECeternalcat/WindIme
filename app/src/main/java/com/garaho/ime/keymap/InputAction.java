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
    SHOW_QUICK_MENU,
    SWITCH_RIME_SCHEMA,

    NAV_LEFT,
    NAV_RIGHT,
    NAV_UP,
    NAV_DOWN,

    CONFIRM_SELECTION,
    BACKSPACE_DELETE,

    /** Pure newline / enter (for formatted text). Bindable e.g. to the '*' key. */
    ENTER,
    /** Hide the IME / exit fullscreen, keeping committed text. */
    DISMISS_IME,
    /** Hide the IME only after the mapped key has been held for a while. */
    COLLAPSE_IME,
    /**
     * Caps toggle for English Multi-tap (and private fields). Short press:
     * reverse the case of the next letter only. Long press: toggle global
     * upper/lower case. Optional binding - when unbound, English Multi-tap
     * falls back to the legacy abcABC mixed cycle.
     */
    TOGGLE_CAPS,
    /** Left soft key -> opens the quick menu. Kyocera Softkey Guide label "菜单". */
    SOFTKEY_LEFT,
    /** Right soft key -> opens the symbol/phrase panel. Softkey Guide label "符号". */
    SOFTKEY_RIGHT,

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

    /**
     * @return the digit 0-9 this action represents, or {@code -1} if it is not
     *         a T9 digit key. Resolved explicitly (not via ordinal) so it is
     *         immune to enum declaration order - {@code INPUT_KEY_0} is listed
     *         after {@code INPUT_KEY_1..9}, which previously broke the math.
     */
    public int digit() {
        switch (this) {
            case INPUT_KEY_0: return 0;
            case INPUT_KEY_1: return 1;
            case INPUT_KEY_2: return 2;
            case INPUT_KEY_3: return 3;
            case INPUT_KEY_4: return 4;
            case INPUT_KEY_5: return 5;
            case INPUT_KEY_6: return 6;
            case INPUT_KEY_7: return 7;
            case INPUT_KEY_8: return 8;
            case INPUT_KEY_9: return 9;
            default: return -1;
        }
    }
}
