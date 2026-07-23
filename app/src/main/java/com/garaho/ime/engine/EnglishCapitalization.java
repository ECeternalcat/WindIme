package com.garaho.ime.engine;

/**
 * English sentence-initial capitalization (design doc §2.1 / SettingsPage
 * {@code 首字母自动大写}).
 *
 * <p>Pure logic so it can be unit-tested on the host JVM. The IME service
 * feeds the text immediately before the cursor and, when the cursor sits at a
 * sentence boundary, asks the active English engine's committed text to be
 * capitalized.
 *
 * <p>A "sentence boundary" is the start of the editor or any position whose
 * last non-whitespace character is one of {@code . ! ?} or a line break. This
 * intentionally matches the classic feature-phone/iWnn behavior rather than a
 * full NLP sentence detector; abbreviations such as "Mr." will therefore also
 * trigger capitalization, which is an accepted trade-off for this target.
 */
public final class EnglishCapitalization {

    private EnglishCapitalization() {
    }

    /**
     * @param before text immediately preceding the cursor, or {@code null} when
     *               it cannot be read (treated as the start of the editor).
     * @return {@code true} when a new English sentence should begin with a
     *         capital letter.
     */
    public static boolean atSentenceStart(CharSequence before) {
        if (before == null || before.length() == 0) {
            return true;
        }
        for (int i = before.length() - 1; i >= 0; i--) {
            char c = before.charAt(i);
            if (!Character.isWhitespace(c)) {
                return isSentenceEnd(c);
            }
        }
        return true;
    }

    /**
     * Upper-case the first letter of {@code word} when it is a lowercase letter.
     * Non-letter initials, already-capitalized text, empty and {@code null}
     * inputs are returned unchanged (empty for {@code null}).
     */
    public static String capitalize(String word) {
        if (word == null) {
            return "";
        }
        if (word.length() == 0) {
            return word;
        }
        char first = word.charAt(0);
        if (!Character.isLetter(first) || Character.isUpperCase(first)) {
            return word;
        }
        return Character.toUpperCase(first) + word.substring(1);
    }

    private static boolean isSentenceEnd(char c) {
        return c == '.' || c == '!' || c == '?' || c == '\n' || c == '\r';
    }
}
