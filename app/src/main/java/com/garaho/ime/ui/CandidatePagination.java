package com.garaho.ime.ui;

/**
 * Candidate window pagination math (improvement doc §3 候选翻页).
 *
 * <p>Pure logic so the page alignment and labels can be unit-tested on the
 * host JVM independently of the Android {@code CandidateBar} view.
 *
 * <p>Page-aligned pagination: the visible window always starts at a page
 * boundary ({@code page * window}) and the focus selects a page by integer
 * division. This turns D-pad left/right at the window edge into a true
 * "next group of candidates" jump instead of a one-item scroll, which matters
 * for long candidate lists on a D-pad-only flip phone.
 */
public final class CandidatePagination {

    private CandidatePagination() {
    }

    /** Number of pages needed to cover {@code total} items at {@code window} size (min 1). */
    public static int pageCount(int total, int window) {
        if (window <= 0 || total <= 0) {
            return 1;
        }
        return (total + window - 1) / window;
    }

    /** Zero-based page index that contains {@code focus}, clamped to a valid page. */
    public static int currentPage(int focus, int total, int window) {
        int pages = pageCount(total, window);
        int page = (window <= 0) ? 0 : focus / window;
        if (page < 0) {
            page = 0;
        } else if (page > pages - 1) {
            page = pages - 1;
        }
        return page;
    }

    /** Index of the first visible item in the page containing {@code focus}. */
    public static int visibleStart(int focus, int total, int window) {
        return currentPage(focus, total, window) * window;
    }

    /**
     * Compact position label "{@code n/total}" (1-based) shown only when the
     * list spans more than one window, so the user knows more candidates exist
     * and where the focus is. Returns empty string when everything fits.
     */
    public static String positionLabel(int focus, int total, int window) {
        if (window <= 0 || total <= 0 || total <= window) {
            return "";
        }
        int f = focus;
        if (f < 0) {
            f = 0;
        } else if (f > total - 1) {
            f = total - 1;
        }
        return (f + 1) + "/" + total;
    }
}
