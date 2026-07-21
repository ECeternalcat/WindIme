package com.garaho.ime.engine;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Telephone keypad letter groups for Multi-tap modes (design doc §1.1 states
 * [2] 中文 Multi-tap and [4] 英文 Multi-tap). Pure lookup, no Android
 * dependencies, so it is unit-testable.
 */
public final class MultiTapCore {

    private static final Map<Integer, String> GROUPS;

    static {
        Map<Integer, String> m = new HashMap<>();
        m.put(2, "abc");
        m.put(3, "def");
        m.put(4, "ghi");
        m.put(5, "jkl");
        m.put(6, "mno");
        m.put(7, "pqrs");
        m.put(8, "tuv");
        m.put(9, "wxyz");
        GROUPS = Collections.unmodifiableMap(m);
    }

    private MultiTapCore() {
    }

    /** @return the letter group for a digit (e.g. {@code 2 -> "abc"}), or {@code null}. */
    public static String group(int digit) {
        return GROUPS.get(digit);
    }

    public static int groupSize(int digit) {
        String g = group(digit);
        return g == null ? 0 : g.length();
    }

    /** @return the letter at {@code index} (mod group size) for {@code digit}, or {@code '\0'}. */
    public static char letter(int digit, int index) {
        String g = group(digit);
        if (g == null || g.isEmpty()) {
            return '\0';
        }
        int len = g.length();
        return g.charAt(((index % len) + len) % len);
    }

    public static boolean isMultiTapDigit(int d) {
        return d >= 2 && d <= 9;
    }
}
