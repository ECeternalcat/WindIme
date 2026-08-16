package com.garaho.ime.settings.update;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Light markdown clean-up for release notes shown in the update dialog.
 *
 * <p>Renders markdown to readable plain text (no WebView / MD library - the
 * target is a 1GB flip phone): headings become 【bracketed】 lines, bullets
 * become {@code ·}, emphasis / inline-code markers are stripped, links keep
 * their label only, horizontal rules become a solid line, and paragraph
 * spacing (blank lines) is preserved. Also normalises CRLF.
 */
public final class ReleaseNotesFormatter {

    private static final Pattern HEADING = Pattern.compile("^#{1,6}\\s+(.*?)\\s*#*\\s*$");
    private static final Pattern BULLET = Pattern.compile("^(\\s*)[-*+]\\s+(.*)$");
    private static final Pattern HR = Pattern.compile("^ {0,3}([-*_]) *(?:\\1 *){2,}$");
    private static final Pattern LINK = Pattern.compile("\\[([^\\]]+)\\]\\([^)]*\\)");
    private static final Pattern BOLD = Pattern.compile("\\*\\*(.+?)\\*\\*");
    private static final Pattern ITALIC = Pattern.compile("(?<![\\w*])\\*([^*\\n]+)\\*(?![\\w*])");
    private static final Pattern CODE = Pattern.compile("`([^`]*)`");
    private static final Pattern IMAGE = Pattern.compile("!\\[([^\\]]*)\\]\\([^)]*\\)");

    private ReleaseNotesFormatter() {
    }

    public static String format(String markdown) {
        if (markdown == null || markdown.isEmpty()) {
            return "";
        }
        String normalized = markdown.replace("\r\n", "\n").replace('\r', '\n');
        String[] lines = normalized.split("\n", -1);
        StringBuilder out = new StringBuilder(markdown.length());
        for (int i = 0; i < lines.length; i++) {
            out.append(formatLine(lines[i]));
            if (i < lines.length - 1) {
                out.append('\n');
            }
        }
        return out.toString();
    }

    private static String formatLine(String rawLine) {
        String line = rawLine;
        Matcher image = IMAGE.matcher(line);
        if (image.find()) {
            line = image.replaceAll("$1");
        }
        Matcher hr = HR.matcher(line);
        if (hr.matches()) {
            return "————————————";
        }
        Matcher heading = HEADING.matcher(line);
        if (heading.matches()) {
            return "【" + stripInline(heading.group(1)).trim() + "】";
        }
        Matcher bullet = BULLET.matcher(line);
        if (bullet.matches()) {
            return bullet.group(1) + "· " + stripInline(bullet.group(2));
        }
        return stripInline(line);
    }

    /** Strip inline markdown markers from already-split line content. */
    static String stripInline(String text) {
        String s = text;
        s = LINK.matcher(s).replaceAll("$1");
        s = BOLD.matcher(s).replaceAll("$1");
        s = ITALIC.matcher(s).replaceAll("$1");
        s = CODE.matcher(s).replaceAll("$1");
        return s;
    }
}
