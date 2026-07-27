package com.nanobaseai.actenora.security.microsoftconnection;

import com.nanobaseai.actenora.sharedkernel.messaging.ExponentialBackoff;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GraphMailboxSyncRunnerRetryTest {

    @Test
    void syncAllFailureEnqueuesAndDrainRetriesUntilSuccess() {
        UUID tenantId = UUID.randomUUID();
        String mailbox = "user-1";
        GraphSubscribedMailboxResolver resolver = mock(GraphSubscribedMailboxResolver.class);
        when(resolver.resolve()).thenReturn(Set.of(new GraphSubscribedMailboxResolver.MailboxRef(tenantId, mailbox)));

        GraphMailboxSyncService syncService = mock(GraphMailboxSyncService.class);
        AtomicInteger calls = new AtomicInteger();
        when(syncService.syncMailbox(eq(tenantId), eq(mailbox), anyBoolean())).thenAnswer(inv -> {
            if (calls.getAndIncrement() == 0) {
                throw new IllegalStateException("Graph unavailable");
            }
            return new GraphMailboxSyncService.SyncResult(mailbox, 2, false);
        });

        MailboxSyncWorkStore workStore = new InMemoryMailboxSyncWorkStore();
        GraphMailboxSyncRunner runner = new GraphMailboxSyncRunner(
                resolver,
                syncService,
                workStore,
                new ExponentialBackoff(Duration.ofMinutes(1), Duration.ofMinutes(15), 0.0),
                Duration.ofMinutes(5),
                20
        );

        runner.syncAll("periodic", true);
        assertEquals(1, workStore.countPending());

        Instant now = Instant.parse("2026-07-27T12:00:00Z");
        // claimDue uses Instant.now() inside drainDue — enqueue already due at now
        runner.drainDue(true);

        assertEquals(0, workStore.countPending());
        verify(syncService, times(2)).syncMailbox(eq(tenantId), eq(mailbox), anyBoolean());
    }

    @Test
    void drainReschedulesOnRepeatedFailure() {
        UUID tenantId = UUID.randomUUID();
        String mailbox = "user-1";
        GraphSubscribedMailboxResolver resolver = mock(GraphSubscribedMailboxResolver.class);
        when(resolver.resolve()).thenReturn(new LinkedHashSet<>());

        GraphMailboxSyncService syncService = mock(GraphMailboxSyncService.class);
        when(syncService.syncMailbox(any(), any(), anyBoolean()))
                .thenThrow(new IllegalStateException("still down"));

        MailboxSyncWorkStore workStore = new InMemoryMailboxSyncWorkStore();
        Instant enqueueAt = Instant.now();
        workStore.enqueue(tenantId, mailbox, enqueueAt);

        GraphMailboxSyncRunner runner = new GraphMailboxSyncRunner(
                resolver,
                syncService,
                workStore,
                new ExponentialBackoff(Duration.ofMinutes(1), Duration.ofMinutes(15), 0.0),
                Duration.ofMinutes(5),
                20
        );

        Instant beforeDrain = Instant.now();
        runner.drainDue(false);

        assertEquals(1, workStore.countPending());
        assertEquals(0, workStore.claimDue(beforeDrain.plusSeconds(30), 10, Duration.ofMinutes(5)).size());
        assertEquals(1, workStore.claimDue(
                beforeDrain.plus(Duration.ofMinutes(1)).plusSeconds(5),
                10,
                Duration.ofMinutes(5)).size());
    }
}
