package com.garaho.ime.engine;

/**
 * Marker for engines that keep an in-flight Multi-tap letter (cycling, not yet
 * committed). The IME service calls {@link #flushPending()} before performing
 * an unrelated action (committing a literal, confirming, switching mode) so
 * the pending letter is finalised instead of being lost.
 */
public interface MultiTapSupport {

    /**
     * Finalise the currently-cycling letter immediately (outside of the
     * inactivity timeout). Safe to call when nothing is pending.
     */
    void flushPending();
}
