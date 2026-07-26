package com.nanobaseai.actenora.security.microsoftconnection;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryTranscriptPollWorkStore implements TranscriptPollWorkStore {

    private final Map<String, MutableWork> work = new ConcurrentHashMap<>();

    @Override
    public void enqueue(UUID tenantId, UUID meetingOccurrenceId, Instant now) {
        work.putIfAbsent(key(tenantId, meetingOccurrenceId), new MutableWork(tenantId, meetingOccurrenceId, now));
    }

    @Override
    public synchronized List<WorkItem> claimDue(Instant now, int limit, Duration staleClaimAfter) {
        List<MutableWork> due = work.values().stream()
                .filter(item -> (("PENDING".equals(item.status) || "RETRY".equals(item.status))
                        && !item.nextAttemptAt.isAfter(now))
                        || ("PROCESSING".equals(item.status)
                        && item.claimedAt != null
                        && item.claimedAt.isBefore(now.minus(staleClaimAfter))))
                .sorted(Comparator.comparing(item -> item.nextAttemptAt))
                .limit(limit)
                .toList();
        List<WorkItem> claimed = new ArrayList<>(due.size());
        for (MutableWork item : due) {
            item.status = "PROCESSING";
            item.claimedAt = now;
            item.updatedAt = now;
            claimed.add(item.snapshot());
        }
        return List.copyOf(claimed);
    }

    @Override
    public void complete(UUID tenantId, UUID meetingOccurrenceId, Instant now) {
        terminal(tenantId, meetingOccurrenceId, "COMPLETED", 0, null, now);
    }

    @Override
    public void reschedule(
            UUID tenantId,
            UUID meetingOccurrenceId,
            int attemptCount,
            Instant nextAttemptAt,
            String failureCode,
            Instant now
    ) {
        MutableWork item = require(tenantId, meetingOccurrenceId);
        item.status = "RETRY";
        item.attemptCount = attemptCount;
        item.nextAttemptAt = nextAttemptAt;
        item.failureCode = failureCode;
        item.claimedAt = null;
        item.updatedAt = now;
    }

    @Override
    public void deadLetter(
            UUID tenantId,
            UUID meetingOccurrenceId,
            int attemptCount,
            String failureCode,
            Instant now
    ) {
        terminal(tenantId, meetingOccurrenceId, "DEAD_LETTER", attemptCount, failureCode, now);
    }

    @Override
    public synchronized boolean requeueDeadLetter(UUID tenantId, UUID meetingOccurrenceId, Instant now) {
        MutableWork item = work.get(key(tenantId, meetingOccurrenceId));
        if (item == null || !"DEAD_LETTER".equals(item.status)) {
            return false;
        }
        item.status = "PENDING";
        item.attemptCount = 0;
        item.nextAttemptAt = now;
        item.claimedAt = null;
        item.failureCode = null;
        item.updatedAt = now;
        return true;
    }

    @Override
    public long countPending() {
        return work.values().stream()
                .filter(item -> !"COMPLETED".equals(item.status) && !"DEAD_LETTER".equals(item.status))
                .count();
    }

    @Override
    public Optional<Instant> oldestPendingCreatedAt() {
        return work.values().stream()
                .filter(item -> !"COMPLETED".equals(item.status) && !"DEAD_LETTER".equals(item.status))
                .map(item -> item.createdAt)
                .min(Instant::compareTo);
    }

    private void terminal(
            UUID tenantId,
            UUID meetingOccurrenceId,
            String status,
            int attempts,
            String failureCode,
            Instant now
    ) {
        MutableWork item = require(tenantId, meetingOccurrenceId);
        item.status = status;
        item.attemptCount = attempts;
        item.failureCode = failureCode;
        item.claimedAt = null;
        item.updatedAt = now;
    }

    private MutableWork require(UUID tenantId, UUID meetingOccurrenceId) {
        MutableWork item = work.get(key(tenantId, meetingOccurrenceId));
        if (item == null) {
            throw new IllegalStateException("Transcript poll work does not exist");
        }
        return item;
    }

    private static String key(UUID tenantId, UUID meetingOccurrenceId) {
        return tenantId + ":" + meetingOccurrenceId;
    }

    private static final class MutableWork {
        private final UUID tenantId;
        private final UUID meetingOccurrenceId;
        private final Instant createdAt;
        private String status = "PENDING";
        private int attemptCount;
        private Instant nextAttemptAt;
        private Instant claimedAt;
        private String failureCode;
        private Instant updatedAt;

        private MutableWork(UUID tenantId, UUID meetingOccurrenceId, Instant now) {
            this.tenantId = tenantId;
            this.meetingOccurrenceId = meetingOccurrenceId;
            this.createdAt = now;
            this.nextAttemptAt = now;
            this.updatedAt = now;
        }

        private WorkItem snapshot() {
            return new WorkItem(tenantId, meetingOccurrenceId, attemptCount, createdAt);
        }
    }
}
