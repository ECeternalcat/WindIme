package com.garaho.ime.engine;

/**
 * Core algorithm-engine abstraction (design doc §2 layer 3).
 *
 * <p>The IME service talks exclusively to this interface, so the concrete
 * backend can be swapped without touching UI or key-handling code:
 * <ul>
 *   <li>{@link T9PinyinEngine} - lightweight pure-Java engine, ships now.</li>
 *   <li>{@code RimeEngine} over {@link com.garaho.ime.rime.RimeBridge} -
 *       native {@code librime.so} backend, Phase 4.</li>
 * </ul>
 */
public interface ImeEngine {

    /**
     * Feed a single T9 digit ({@code 0}-{@code 9}) into the composing buffer.
     *
     * @return {@code true} if the engine consumed the digit and updated its
     *         composing/candidate state.
     */
    boolean processDigit(int digit);

    /**
     * Remove the last digit from the composing buffer.
     *
     * @return {@code true} if there was something to delete.
     */
    boolean backspace();

    /**
     * Commit the candidate at {@code index} and reset the composing buffer.
     * Implementations fire {@link EngineListener#onCommit(String)}.
     *
     * @return {@code true} if a candidate was committed.
     */
    boolean selectCandidate(int index);

    /** Drop the in-progress composing buffer without committing anything. */
    void reset();

    /** @return current number of available candidates. */
    int candidateCount();

    void setListener(EngineListener listener);
}
