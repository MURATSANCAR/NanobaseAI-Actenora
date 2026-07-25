package com.nanobaseai.actenora.sharedkernel.messaging.support;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Coordinates graceful shutdown for outbox relay and consumers.
 */
public final class GracefulShutdownGate {

    private final AtomicBoolean accepting = new AtomicBoolean(true);
    private final ReentrantLock inflight = new ReentrantLock();
    private int active;

    public boolean tryEnter() {
        if (!accepting.get()) {
            return false;
        }
        inflight.lock();
        try {
            if (!accepting.get()) {
                return false;
            }
            active++;
            return true;
        } finally {
            inflight.unlock();
        }
    }

    public void leave() {
        inflight.lock();
        try {
            if (active == 0) {
                throw new IllegalStateException("leave without enter");
            }
            active--;
        } finally {
            inflight.unlock();
        }
    }

    public void beginShutdown() {
        accepting.set(false);
    }

    public boolean isAccepting() {
        return accepting.get();
    }

    public int activeCount() {
        inflight.lock();
        try {
            return active;
        } finally {
            inflight.unlock();
        }
    }

    public boolean awaitQuiescent(long timeoutMillis) throws InterruptedException {
        long deadline = System.currentTimeMillis() + Math.max(0L, timeoutMillis);
        while (true) {
            if (activeCount() == 0) {
                return true;
            }
            long remaining = deadline - System.currentTimeMillis();
            if (remaining <= 0) {
                return false;
            }
            Thread.sleep(Math.min(25L, remaining));
        }
    }
}
