package com.garaho.ime.engine;

/**
 * Standard telephone keypad encoding (2=abc, 3=def, ..., 9=wxyz) for the
 * English T9 engine. Letters outside a-z are skipped; non-letter input has
 * no code (returns empty), so digit buffers always consist of 2-9.
 */
public final class EnglishT9Codec {

    private static final int[] DIGIT = new int[26];

    static {
        String[] groups = {"abc", "def", "ghi", "jkl", "mno", "pqrs", "tuv", "wxyz"};
        for (int i = 0; i < groups.length; i++) {
            int digit = '2' + i;
            for (int j = 0; j < groups[i].length(); j++) {
                DIGIT[groups[i].charAt(j) - 'a'] = digit;
            }
        }
    }

    private EnglishT9Codec() {
    }

    public static String encode(String word) {
        if (word == null || word.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder(word.length());
        for (int i = 0; i < word.length(); i++) {
            char c = word.charAt(i);
            if (c >= 'a' && c <= 'z') {
                sb.append((char) DIGIT[c - 'a']);
            } else if (c >= 'A' && c <= 'Z') {
                sb.append((char) DIGIT[c - 'A']);
            }
        }
        return sb.toString();
    }

    public static boolean isValidDigit(int d) {
        return d >= 2 && d <= 9;
    }
}
