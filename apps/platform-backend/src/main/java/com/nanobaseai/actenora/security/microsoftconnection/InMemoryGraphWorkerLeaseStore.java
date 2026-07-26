package com.nanobaseai.actenora.security.microsoftconnection;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryGraphWorkerLeaseStore implements GraphWorkerLeaseStore {

    private final Map<String, Lease> leases = new ConcurrentHashMap<>();

    @Override
    public synchronized boolean tryAcquire(String leaseName, String ownerId, Instant now, Duration duration) {
        Lease current = leases.get(leaseName);
        if (current != null && current.lockedUntil().isAfter(now) && !current.ownerId().equals(ownerId)) {
            return false;
        }
        leases.put(leaseName, new Lease(ownerId, now.plus(duration)));
        return true;
    }

    @Override
    public synchronized void release(String leaseName, String ownerId, Instant now) {
        Lease current = leases.get(leaseName);
        if (current != null && current.ownerId().equals(ownerId)) {
            leases.put(leaseName, new Lease(ownerId, now));
        }
    }

    private record Lease(String ownerId, Instant lockedUntil) {
    }
}
