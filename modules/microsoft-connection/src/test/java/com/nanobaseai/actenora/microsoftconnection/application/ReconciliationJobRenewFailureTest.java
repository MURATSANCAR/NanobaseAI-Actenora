package com.nanobaseai.actenora.microsoftconnection.application;

import com.nanobaseai.actenora.microsoftconnection.application.model.CalendarDeltaPage;
import com.nanobaseai.actenora.microsoftconnection.application.model.CalendarEvent;
import com.nanobaseai.actenora.microsoftconnection.application.model.CalendarSyncCursor;
import com.nanobaseai.actenora.microsoftconnection.application.model.GraphSubscription;
import com.nanobaseai.actenora.microsoftconnection.application.model.SubscriptionCreateRequest;
import com.nanobaseai.actenora.microsoftconnection.application.port.CalendarGateway;
import com.nanobaseai.actenora.microsoftconnection.application.port.SubscriptionGateway;
import com.nanobaseai.actenora.microsoftconnection.infrastructure.notification.InMemoryNotificationInbox;
import com.nanobaseai.actenora.microsoftconnection.infrastructure.persistence.InMemoryCalendarSyncCursorStore;
import com.nanobaseai.actenora.microsoftconnection.infrastructure.persistence.InMemorySubscriptionStore;
import com.nanobaseai.actenora.sharedkernel.time.InstantClock;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReconciliationJobRenewFailureTest {

    @Test
    void continuesMailboxPollAndSurfacesRenewFailures() {
        UUID tenantId = UUID.randomUUID();
        InMemorySubscriptionStore store = new InMemorySubscriptionStore();
        store.save(new GraphSubscription(
                tenantId,
                "sub-fail",
                "users/organizer@contoso.com/events",
                "created,updated",
                "https://notifications.example.test/graph",
                "client-state",
                Instant.now().plus(Duration.ofHours(1)),
                "app-id"));

        SubscriptionLifecycleService lifecycle = new SubscriptionLifecycleService(
                new FailingRenewGateway(),
                store,
                new InMemoryNotificationInbox(),
                InstantClock.systemUTC(),
                Duration.ofHours(6),
                Duration.ofHours(48)
        );
        CalendarSyncService sync = new CalendarSyncService(
                new EmptyCalendarGateway(),
                new InMemoryCalendarSyncCursorStore(),
                InstantClock.systemUTC()
        );
        ReconciliationJob job = new ReconciliationJob(lifecycle, new PollingFallbackService(sync));

        ReconciliationJob.ReconciliationFailedException ex = assertThrows(
                ReconciliationJob.ReconciliationFailedException.class,
                () -> job.run(List.of(new PollingFallbackService.MailboxRef(tenantId, "organizer@contoso.com")))
        );
        assertEquals(0, ex.result().subscriptionsRenewed());
        assertEquals(1, ex.result().subscriptionRenewFailures());
        assertEquals(0, ex.result().mailboxPollFailures());
        assertTrue(ex.result().hasFailures());
    }

    private static final class FailingRenewGateway implements SubscriptionGateway {
        @Override
        public GraphSubscription create(UUID tenantId, SubscriptionCreateRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public GraphSubscription renew(UUID tenantId, String subscriptionId, Instant newExpiration) {
            throw new IllegalStateException("renew failed");
        }

        @Override
        public Optional<GraphSubscription> get(UUID tenantId, String subscriptionId) {
            return Optional.empty();
        }

        @Override
        public void delete(UUID tenantId, String subscriptionId) {
        }
    }

    private static final class EmptyCalendarGateway implements CalendarGateway {
        @Override
        public CalendarDeltaPage syncDelta(UUID tenantId, String userId, CalendarSyncCursor cursor) {
            return new CalendarDeltaPage(List.of(), null, "delta-link");
        }

        @Override
        public Optional<CalendarEvent> getEvent(UUID tenantId, String userId, String eventId) {
            return Optional.empty();
        }
    }
}
