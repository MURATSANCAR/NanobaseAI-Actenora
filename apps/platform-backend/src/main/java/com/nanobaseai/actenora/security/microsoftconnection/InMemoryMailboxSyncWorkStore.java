package com.nanobaseai.actenora.security.microsoftconnection;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryMailboxSyncWorkStore implements MailboxSyncWorkStore {

    private final Map<String, MutableWork> work = new ConcurrentHashMap<>();

    @Override
    public void enqueue(UUID tenantId, String mailboxUserId, Instant now) {
        work.compute(key(tenantId, mailboxUserId), (ignored, existing) -> {
            if (existing == null || "COMPLETED".equals(existing.status)) {
                return new MutableWork(tenantId, mailboxUserId, now);
            }
            return existing;
        });
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
    public void complete(UUID tenantId, String mailboxUserId, Instant now) {
        MutableWork item = work.get(key(tenantId, mailboxUserId));
        if (item == null) {
            return;
        }
        item.status = "COMPLETED";
        item.claimedAt = null;
        item.failureCode = null;
        item.updatedAt = now;
    }

    @Override
    public void reschedule(
            UUID tenantId,
            String mailboxUserId,
            int attemptCount,
            Instant nextAttemptAt,
            String failureCode,
            Instant now
    ) {
        MutableWork item = work.computeIfAbsent(
                key(tenantId, mailboxUserId),
                ignored -> new MutableWork(tenantId, mailboxUserId, now));
        item.status = "RETRY";
        item.attemptCount = attemptCount;
        item.nextAttemptAt = nextAttemptAt;
        item.failureCode = failureCode;
        item.claimedAt = null;
        item.updatedAt = now;
    }

    @Override
    public long countPending() {
        return work.values().stream()
                .filter(item -> !"COMPLETED".equals(item.status))
                .count();
    }

    private static String key(UUID tenantId, String mailboxUserId) {
        return tenantId + ":" + mailboxUserId;
    }

    private static final class MutableWork {
        private final UUID tenantId;
        private final String mailboxUserId;
        private final Instant createdAt;
        private String status = "PENDING";
        private int attemptCount;
        private Instant nextAttemptAt;
        private Instant claimedAt;
        private String failureCode;
        private Instant updatedAt;

        private MutableWork(UUID tenantId, String mailboxUserId, Instant now) {
            this.tenantId = Objects.requireNonNull(tenantId);
            this.mailboxUserId = Objects.requireNonNull(mailboxUserId);
            this.createdAt = now;
            this.nextAttemptAt = now;
            this.updatedAt = now;
        }

        private WorkItem snapshot() {
            return new WorkItem(tenantId, mailboxUserId, attemptCount, createdAt);
        }
    }
}
