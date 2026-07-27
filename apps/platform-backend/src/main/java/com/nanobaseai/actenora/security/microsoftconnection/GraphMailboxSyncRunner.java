package com.nanobaseai.actenora.security.microsoftconnection;

import com.nanobaseai.actenora.sharedkernel.messaging.ExponentialBackoff;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Runs calendar sync for every subscribed mailbox (startup + periodic safety net)
 * and drains the fast-retry queue for previously failed mailboxes.
 */
final class GraphMailboxSyncRunner {

    private static final Logger log = LoggerFactory.getLogger(GraphMailboxSyncRunner.class);

    private final GraphSubscribedMailboxResolver mailboxResolver;
    private final GraphMailboxSyncService graphMailboxSyncService;
    private final MailboxSyncWorkStore workStore;
    private final ExponentialBackoff backoff;
    private final Duration staleClaimAfter;
    private final int batchSize;

    GraphMailboxSyncRunner(
            GraphSubscribedMailboxResolver mailboxResolver,
            GraphMailboxSyncService graphMailboxSyncService,
            MailboxSyncWorkStore workStore,
            ExponentialBackoff backoff,
            Duration staleClaimAfter,
            int batchSize
    ) {
        this.mailboxResolver = Objects.requireNonNull(mailboxResolver);
        this.graphMailboxSyncService = Objects.requireNonNull(graphMailboxSyncService);
        this.workStore = Objects.requireNonNull(workStore);
        this.backoff = Objects.requireNonNull(backoff);
        this.staleClaimAfter = Objects.requireNonNull(staleClaimAfter);
        if (batchSize < 1) {
            throw new IllegalArgumentException("batchSize must be positive");
        }
        this.batchSize = batchSize;
    }

    void syncAll(String trigger, boolean recoverEmptyDelta) {
        Instant now = Instant.now();
        for (GraphSubscribedMailboxResolver.MailboxRef mailbox : mailboxResolver.resolve()) {
            syncOne(trigger, mailbox.tenantId(), mailbox.userId(), recoverEmptyDelta, now);
        }
    }

    void drainDue(boolean recoverEmptyDelta) {
        Instant now = Instant.now();
        for (MailboxSyncWorkStore.WorkItem item : workStore.claimDue(now, batchSize, staleClaimAfter)) {
            int attempt = item.attemptCount() + 1;
            try {
                GraphMailboxSyncService.SyncResult result = graphMailboxSyncService.syncMailbox(
                        item.tenantId(),
                        item.mailboxUserId(),
                        recoverEmptyDelta
                );
                workStore.complete(item.tenantId(), item.mailboxUserId(), now);
                log.info(
                        "Graph mailbox sync retry succeeded tenantId={} mailbox={} eventsSynced={} recovered={} attempt={}",
                        item.tenantId(),
                        item.mailboxUserId(),
                        result.eventsSynced(),
                        result.recoveredFromEmptyDelta(),
                        attempt);
            } catch (RuntimeException ex) {
                Instant next = now.plus(backoff.delayForAttempt(attempt - 1));
                workStore.reschedule(
                        item.tenantId(),
                        item.mailboxUserId(),
                        attempt,
                        next,
                        ex.getClass().getSimpleName(),
                        now);
                log.warn(
                        "Graph mailbox sync retry failed tenantId={} mailbox={} attempt={} nextAttemptAt={}: {}",
                        item.tenantId(),
                        item.mailboxUserId(),
                        attempt,
                        next,
                        ex.getMessage());
            }
        }
    }

    private void syncOne(
            String trigger,
            java.util.UUID tenantId,
            String mailboxUserId,
            boolean recoverEmptyDelta,
            Instant now
    ) {
        try {
            GraphMailboxSyncService.SyncResult result = graphMailboxSyncService.syncMailbox(
                    tenantId,
                    mailboxUserId,
                    recoverEmptyDelta
            );
            workStore.complete(tenantId, mailboxUserId, now);
            log.info(
                    "Graph mailbox sync trigger={} tenantId={} mailbox={} eventsSynced={} recovered={}",
                    trigger,
                    tenantId,
                    mailboxUserId,
                    result.eventsSynced(),
                    result.recoveredFromEmptyDelta());
        } catch (RuntimeException ex) {
            workStore.enqueue(tenantId, mailboxUserId, now);
            log.warn(
                    "Graph mailbox sync failed trigger={} tenantId={} mailbox={}: {}",
                    trigger,
                    tenantId,
                    mailboxUserId,
                    ex.getMessage());
        }
    }
}
