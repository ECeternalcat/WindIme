package com.garaho.ime.engine;

/**
 * The one and only case-shift state machine for letter input (English
 * Multi-tap and private/password fields - deliberately not English T9, where
 * prediction handles capitalisation).
 *
 * <p>Two layers:
 * <ul>
 *   <li><b>Global case</b> ({@code globalUpper}), toggled by a long press of
 *       the Caps key (or the {@code #} key in private fields): all following
 *       letters are upper / lower case.</li>
 *   <li><b>One-shot shift</b> ({@code pendingShift}), armed by a short press:
 *       exactly the next letter gets the opposite case of the global state,
 *       then the shift is consumed. In the global-upper state a short press
 *       makes the next letter <i>lower</i> case (reverse shift).</li>
 * </ul>
 *
 * <p>The shift is consumed when a letter <b>starts</b> (first press of its
 * Multi-tap cycle): the cycling table is locked for that letter so repeated
 * presses keep cycling in the same case. Pure Java, host-JVM testable.
 */
public final class CapsState {

    private boolean globalUpper;
    private boolean pendingShift;

    /** Case of the next letter's cycling table (and its preview). */
    public boolean nextLetterUppercase() {
        return globalUpper ^ pendingShift;
    }

    public boolean isGlobalUpper() {
        return globalUpper;
    }

    public boolean isPendingShift() {
        return pendingShift;
    }

    /** Short press: the next letter only gets the opposite case. */
    public void shortPress() {
        pendingShift = true;
    }

    /** Long press: flip the global case; a pending shift is discarded. */
    public void toggleGlobal() {
        globalUpper = !globalUpper;
        pendingShift = false;
    }

    /**
     * Consume the one-shot shift. Called when a letter starts (its cycling
     * table was locked with {@link #nextLetterUppercase()}), so later presses
     * of the same cycle and the committed letter keep that table.
     */
    public void consumePendingShift() {
        pendingShift = false;
    }

    public void reset() {
        globalUpper = false;
        pendingShift = false;
    }

    /**
     * Indicator label shown in the candidate strip. The base reflects the
     * global state ("Abc" / "ABC"); a pending shift shows as a shifted second
     * letter ("aBc" when the next letter will be upper, "AbC" when it will be
     * lower while the global state stays upper).
     */
    public String label() {
        if (globalUpper) {
            return pendingShift ? "AbC" : "ABC";
        }
        return pendingShift ? "aBc" : "Abc";
    }
}
