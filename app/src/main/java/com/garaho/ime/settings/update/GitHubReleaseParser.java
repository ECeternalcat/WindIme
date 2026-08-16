package com.garaho.ime.settings.update;

/**
 * Minimal reader for the GitHub {@code /releases/latest} JSON response.
 *
 * <p>Hand-rolled string scanning instead of org.json so the parsing is pure
 * Java and unit-testable on the host JVM (Android's org.json throws in local
 * unit tests). Only the four fields the update flow needs are extracted:
 * {@code tag_name}, {@code body}, {@code html_url} and the first
 * {@code browser_download_url} that points at an {@code .apk} asset.
 */
public final class GitHubReleaseParser {

    private GitHubReleaseParser() {
    }

    /** @return the release, or null if the JSON is unusable (no tag). */
    public static UpdateInfo parse(String json) {
        if (json == null || json.isEmpty()) {
            return null;
        }
        String tag = findStringField(json, "tag_name");
        if (tag == null || tag.isEmpty()) {
            return null;
        }
        String body = findStringField(json, "body");
        String htmlUrl = findStringField(json, "html_url");
        String apkUrl = findApkDownloadUrl(json);
        return new UpdateInfo(tag, body == null ? "" : body, apkUrl, htmlUrl);
    }

    /** Value of the first {@code "name":"value"} string field, unescaped. */
    static String findStringField(String json, String field) {
        String needle = "\"" + field + "\"";
        int key = json.indexOf(needle);
        if (key < 0) {
            return null;
        }
        int colon = json.indexOf(':', key + needle.length());
        if (colon < 0) {
            return null;
        }
        int open = json.indexOf('"', colon + 1);
        if (open < 0) {
            return null;
        }
        StringBuilder out = new StringBuilder();
        int i = open + 1;
        while (i < json.length()) {
            char c = json.charAt(i);
            if (c == '"') {
                return out.toString();
            }
            if (c == '\\' && i + 1 < json.length()) {
                i = appendEscaped(out, json, i);
                continue;
            }
            out.append(c);
            i++;
        }
        return out.toString();
    }

    /** First {@code browser_download_url} whose value ends with {@code .apk}. */
    static String findApkDownloadUrl(String json) {
        String needle = "\"browser_download_url\"";
        int from = 0;
        while (true) {
            int key = json.indexOf(needle, from);
            if (key < 0) {
                return null;
            }
            String url = findStringFieldAt(json, key + needle.length());
            if (url != null && url.toLowerCase().endsWith(".apk")) {
                return url;
            }
            from = key + needle.length();
        }
    }

    /** findStringField for an already-located key (scan starts after the key). */
    private static String findStringFieldAt(String json, int searchFrom) {
        int colon = json.indexOf(':', searchFrom);
        if (colon < 0) {
            return null;
        }
        int open = json.indexOf('"', colon + 1);
        if (open < 0) {
            return null;
        }
        StringBuilder out = new StringBuilder();
        int i = open + 1;
        while (i < json.length()) {
            char c = json.charAt(i);
            if (c == '"') {
                return out.toString();
            }
            if (c == '\\' && i + 1 < json.length()) {
                i = appendEscaped(out, json, i);
                continue;
            }
            out.append(c);
            i++;
        }
        return out.toString();
    }

    /**
     * Append one escaped character starting at the backslash; returns the
     * index just past the consumed escape sequence.
     */
    private static int appendEscaped(StringBuilder out, String json, int backslashAt) {
        char esc = json.charAt(backslashAt + 1);
        switch (esc) {
            case 'n': out.append('\n'); return backslashAt + 2;
            case 'r': out.append('\r'); return backslashAt + 2;
            case 't': out.append('\t'); return backslashAt + 2;
            case 'b': out.append('\b'); return backslashAt + 2;
            case 'f': out.append('\f'); return backslashAt + 2;
            case '"': out.append('"'); return backslashAt + 2;
            case '\\': out.append('\\'); return backslashAt + 2;
            case '/': out.append('/'); return backslashAt + 2;
            case 'u':
                if (backslashAt + 6 <= json.length()) {
                    try {
                        out.append((char) Integer.parseInt(
                                json.substring(backslashAt + 2, backslashAt + 6), 16));
                        return backslashAt + 6;
                    } catch (NumberFormatException ignored) {
                        // fall through to literal output
                    }
                }
                out.append('\\').append(esc);
                return backslashAt + 2;
            default:
                out.append('\\').append(esc);
                return backslashAt + 2;
        }
    }
}
