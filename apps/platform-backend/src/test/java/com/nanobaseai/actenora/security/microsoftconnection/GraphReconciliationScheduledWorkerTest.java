package com.nanobaseai.actenora.security.microsoftconnection;

import com.nanobaseai.actenora.microsoftconnection.api.MicrosoftConnectionApi;
import com.nanobaseai.actenora.microsoftconnection.application.model.GraphSubscription;
import com.nanobaseai.actenora.microsoftconnection.infrastructure.persistence.InMemorySubscriptionStore;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class GraphReconciliationScheduledWorkerTest {

    @Test
    void reconcilesDistinctSubscribedMailboxesUnderLease() {
        MicrosoftConnectionApi api = mock(MicrosoftConnectionApi.class);
        InMemorySubscriptionStore subscriptions = new InMemorySubscriptionStore();
        UUID tenantId = UUID.randomUUID();
        subscriptions.save(new GraphSubscription(
                tenantId,
                "sub-1",
                "users/organizer@contoso.com/events",
                "created,updated",
                "https://notifications.example.test/graph",
                "client-state",
                Instant.now().plusSeconds(7200),
                "app-id"));
        GraphObservability observability = observability();
        var worker = new MicrosoftConnectionPlatformConfiguration.GraphReconciliationScheduledWorker(
                api,
                subscriptions,
                new InMemoryGraphWorkerLeaseStore(),
                observability,
                mock(CalendarMeetingUpsertAdapter.class),
                "test-owner");

        worker.reconcile();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<java.util.List<com.nanobaseai.actenora.microsoftconnection.application.PollingFallbackService.MailboxRef>>
                captor = ArgumentCaptor.forClass(java.util.List.class);
        verify(api).reconcile(captor.capture(), org.mockito.ArgumentMatchers.any());
        assertEquals(1, captor.getValue().size());
        assertEquals("organizer@contoso.com", captor.getValue().getFirst().userId());
    }

    private static GraphObservability observability() {
        return new GraphObservability(
                new SimpleMeterRegistry(),
                3,
                java.time.Duration.ofSeconds(30));
    }
}
