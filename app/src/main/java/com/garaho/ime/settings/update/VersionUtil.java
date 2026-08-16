package com.garaho.ime.settings.update;

/**
 * Numeric comparison of dotted version strings such as {@code 0.5.6} or
 * {@code v0.5.10}. A leading {@code v}/{@code V} and any non-numeric suffix
 * on a part are ignored, so {@code v0.5.6} equals {@code 0.5.6} and
 * {@code 1.2.3-beta} compares as {@code 1.2.3}.
 */
public final class VersionUtil {

    private static final int MAX_PARTS = 5;

    private VersionUtil() {
    }

    /** @return negative if {@code a < b}, positive if {@code a > b}, 0 if equal. */
    public static int compare(String a, String b) {
        int[] va = parse(a);
        int[] vb = parse(b);
        for (int i = 0; i < MAX_PARTS; i++) {
            if (va[i] != vb[i]) {
                return va[i] < vb[i] ? -1 : 1;
            }
        }
        return 0;
    }

    public static boolean isNewer(String candidate, String current) {
        return compare(candidate, current) > 0;
    }

    /** Strip a leading v/V, for display and comparison normalisation. */
    public static String stripPrefix(String version) {
        if (version == null) {
            return "";
        }
        String v = version.trim();
        if (!v.isEmpty() && (v.charAt(0) == 'v' || v.charAt(0) == 'V')) {
            return v.substring(1);
        }
        return v;
    }

    private static int[] parse(String version) {
        int[] out = new int[MAX_PARTS];
        String v = stripPrefix(version);
        if (v.isEmpty()) {
            return out;
        }
        String[] parts = v.split("\\.");
        for (int i = 0; i < parts.length && i < MAX_PARTS; i++) {
            out[i] = leadingDigits(parts[i]);
        }
        return out;
    }

    private static int leadingDigits(String part) {
        int end = 0;
        while (end < part.length() && Character.isDigit(part.charAt(end))) {
            end++;
        }
        if (end == 0) {
            return 0;
        }
        try {
            return Integer.parseInt(part.substring(0, end));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
