package com.garaho.ime.rime;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Process-wide owner of native Rime initialization observability and
 * serialization (improvement doc §7).
 *
 * <p>Native librime state lives for the whole OS process, independent of
 * {@code InputMethodService} create/destroy events. This class therefore keeps
 * the only process-wide state about initialization:
 * <ul>
 *   <li>A monotonically increasing <b>session id</b> assigned to each init
 *       attempt, so logcat output from overlapping attempts (Service recreate,
 *       retries) can be told apart.</li>
 *   <li>A single-slot <b>concurrency guard</b> so at most one init/deploy
 *       session runs at a time. A Service recreate or concurrent retry that
 *       arrives while a session is running is refused rather than starting a
 *       second, conflicting maintenance session.</li>
 *   <li>A structured log line format.</li>
 * </ul>
 *
 * <p>Pure {@code java.util.concurrent} so it is unit-testable on the host JVM.
 */
public final class RimeLifecycle {

    public enum NativeState {
        NOT_STARTED,
        DEPLOYING,
        READY
    }

    private static final AtomicInteger SESSION = new AtomicInteger(0);
    private static final AtomicBoolean RUNNING = new AtomicBoolean(false);
    private static final AtomicReference<NativeState> NATIVE_STATE =
            new AtomicReference<>(NativeState.NOT_STARTED);

    private RimeLifecycle() {
    }

    /** Assign the next session id for a new init attempt (1-based, monotonic). */
    public static int nextSessionId() {
        return SESSION.incrementAndGet();
    }

    /**
     * Try to begin an init session.
     *
     * @return {@code true} if this caller acquired the single slot;
     *         {@code false} if another session is already running (the caller
     *         must then refrain from touching native Rime).
     */
    public static boolean beginSession() {
        return RUNNING.compareAndSet(false, true);
    }

    /** Release the session slot. Always called from a {@code finally} block. */
    public static void endSession() {
        RUNNING.set(false);
    }

    public static boolean isRunning() {
        return RUNNING.get();
    }

    /** Record that native startup completed and asynchronous schema deployment began. */
    public static void markNativeStarted() {
        NATIVE_STATE.compareAndSet(NativeState.NOT_STARTED, NativeState.DEPLOYING);
    }

    /** Publish schema readiness. The process state is monotonic until process death. */
    public static void markSchemaReady() {
        NATIVE_STATE.compareAndSet(NativeState.DEPLOYING, NativeState.READY);
    }

    public static NativeState getNativeState() {
        return NATIVE_STATE.get();
    }

    public static boolean hasNativeStarted() {
        return NATIVE_STATE.get() != NativeState.NOT_STARTED;
    }

    static void resetNativeStateForTests() {
        NATIVE_STATE.set(NativeState.NOT_STARTED);
    }

    /**
     * Structured log line: {@code Rime[#id] event} or {@code Rime[#id] event: detail}.
     * A null/empty detail is omitted.
     */
    public static String format(int sessionId, String event, String detail) {
        StringBuilder sb = new StringBuilder("Rime[#").append(sessionId).append("] ").append(event);
        if (detail != null && !detail.isEmpty()) {
            sb.append(": ").append(detail);
        }
        return sb.toString();
    }
}
