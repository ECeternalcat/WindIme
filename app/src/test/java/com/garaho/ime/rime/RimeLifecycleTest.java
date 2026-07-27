package com.garaho.ime.rime;

import org.junit.Test;

import java.util.concurrent.CountDownLatch;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RimeLifecycleTest {

    private void resetNativeState() {
        RimeLifecycle.resetNativeStateForTests();
    }

    @Test
    public void sessionIdsAreMonotonic() {
        int a = RimeLifecycle.nextSessionId();
        int b = RimeLifecycle.nextSessionId();
        int c = RimeLifecycle.nextSessionId();
        assertTrue("session ids must increase", b > a);
        assertEquals(b + 1, c);
    }

    @Test
    public void beginSessionIsSingleSlot() {
        RimeLifecycle.endSession(); // clean slate regardless of prior tests
        try {
            assertTrue(RimeLifecycle.beginSession());
            assertTrue(RimeLifecycle.isRunning());
            assertFalse("a second concurrent session must be refused",
                    RimeLifecycle.beginSession());
        } finally {
            RimeLifecycle.endSession();
        }
        assertFalse(RimeLifecycle.isRunning());
        // the slot is reusable once released
        assertTrue(RimeLifecycle.beginSession());
        RimeLifecycle.endSession();
    }

    @Test
    public void formatOmitsEmptyDetail() {
        assertEquals("Rime[#3] schema-ready", RimeLifecycle.format(3, "schema-ready", ""));
        assertEquals("Rime[#3] schema-ready", RimeLifecycle.format(3, "schema-ready", null));
    }

    @Test
    public void formatAppendsDetail() {
        assertEquals("Rime[#7] await-schema: rime_ice",
                RimeLifecycle.format(7, "await-schema", "rime_ice"));
    }

    @Test
    public void nativeStartupIsDeployingUntilSchemaIsReady() {
        resetNativeState();

        assertEquals(RimeLifecycle.NativeState.NOT_STARTED,
                RimeLifecycle.getNativeState());
        assertFalse(RimeLifecycle.hasNativeStarted());

        RimeLifecycle.markNativeStarted();

        assertEquals(RimeLifecycle.NativeState.DEPLOYING,
                RimeLifecycle.getNativeState());
        assertTrue(RimeLifecycle.hasNativeStarted());

        RimeLifecycle.markSchemaReady();

        assertEquals(RimeLifecycle.NativeState.READY,
                RimeLifecycle.getNativeState());
    }

    @Test
    public void interruptedDeploymentRemainsAvailableForReattachment() throws Exception {
        resetNativeState();
        RimeLifecycle.endSession();
        CountDownLatch deploying = new CountDownLatch(1);

        Thread oldService = new Thread(new Runnable() {
            @Override
            public void run() {
                assertTrue(RimeLifecycle.beginSession());
                try {
                    RimeLifecycle.markNativeStarted();
                    deploying.countDown();
                    Thread.sleep(Long.MAX_VALUE);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    RimeLifecycle.endSession();
                }
            }
        });
        oldService.start();
        deploying.await();
        oldService.interrupt();
        oldService.join();

        // Interruption releases ownership but makes no native state transition.
        // A recreated Service acquires the slot, reattaches without startup,
        // and can publish readiness after probing.
        assertTrue(RimeLifecycle.hasNativeStarted());
        assertEquals(RimeLifecycle.NativeState.DEPLOYING,
                RimeLifecycle.getNativeState());
        assertTrue(RimeLifecycle.beginSession());

        try {
            RimeLifecycle.markSchemaReady();
        } finally {
            RimeLifecycle.endSession();
        }

        assertEquals(RimeLifecycle.NativeState.READY,
                RimeLifecycle.getNativeState());
    }

    @Test
    public void startupStateCannotRegressAfterReady() throws Exception {
        resetNativeState();
        RimeLifecycle.markNativeStarted();
        RimeLifecycle.markSchemaReady();

        int threadCount = 8;
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threadCount);
        for (int i = 0; i < threadCount; i++) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        start.await();
                        RimeLifecycle.markNativeStarted();
                        RimeLifecycle.markSchemaReady();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        done.countDown();
                    }
                }
            }).start();
        }
        start.countDown();
        done.await();

        assertEquals(RimeLifecycle.NativeState.READY,
                RimeLifecycle.getNativeState());
    }
}
