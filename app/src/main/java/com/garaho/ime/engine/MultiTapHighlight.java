package com.garaho.ime.engine;

import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.graphics.Typeface;

/**
 * Builds the composing preview for Multi-tap modes, highlighting the
 * in-flight (cycling) letter so the user can see which character a further
 * tap would change (design doc §1.1 / §3 focus-anchor requirement).
 */
public final class MultiTapHighlight {

    private static final int BG = 0xFF336699;
    private static final int FG = 0xFFFFFFFF;

    private MultiTapHighlight() {
    }

    /**
     * @param text  full composing text (committed letters + pending letter)
     * @param start inclusive start index of the pending letter to highlight
     * @param end   exclusive end index
     */
    public static CharSequence apply(String text, int start, int end) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        SpannableStringBuilder ssb = new SpannableStringBuilder(text);
        int s = Math.max(0, start);
        int e = Math.min(text.length(), end);
        if (e > s) {
            ssb.setSpan(new BackgroundColorSpan(BG), s, e, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            ssb.setSpan(new ForegroundColorSpan(FG), s, e, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            ssb.setSpan(new StyleSpan(Typeface.BOLD), s, e, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return ssb;
    }
}
