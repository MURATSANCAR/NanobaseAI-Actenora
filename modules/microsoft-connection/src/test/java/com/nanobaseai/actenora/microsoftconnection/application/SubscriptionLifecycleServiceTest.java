package com.nanobaseai.actenora.microsoftconnection.application;

import com.nanobaseai.actenora.microsoftconnection.application.model.GraphSubscription;
import com.nanobaseai.actenora.microsoftconnection.application.model.LifecycleNotification;
import com.nanobaseai.actenora.microsoftconnection.application.model.SubscriptionCreateRequest;
import com.nanobaseai.actenora.microsoftconnection.application.port.SubscriptionGateway;
import com.nanobaseai.actenora.microsoftconnection.infrastructure.notification.InMemoryNotificationInbox;
import com.nanobaseai.actenora.microsoftconnection.infrastructure.persistence.InMemorySubscriptionStore;
import com.nanobaseai.actenora.sharedkernel.time.InstantClock;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SubscriptionLifecycleServiceTest {

    @Test
    void claimIsIdempotentAndSurvivesAcrossServiceInstancesSharingInbox() {
        InMemoryNotificationInbox inbox = new InMemoryNotificationInbox();
        InMemorySubscriptionStore store = new InMemorySubscriptionStore();
        SubscriptionLifecycleService first = service(store, inbox, new RecordingGateway());
        SubscriptionLifecycleService second = service(store, inbox, new RecordingGateway());

        LifecycleNotification notification = new LifecycleNotification(
                "sub::lifecycle::reauthorizationRequired",
                "sub-1",
                "reauthorizationRequired",
                "client-state",
                UUID.randomUUID().toString()
        );

        assertTrue(first.handleLifecycleNotification(notification, n -> { }));
        assertFalse(second.handleLifecycleNotification(notification, n -> { }));
    }

    @Test
    void reauthorizationTriggersRenewExpiring() {
        InMemoryNotificationInbox inbox = new InMemoryNotificationInbox();
        InMemorySubscriptionStore store = new InMemorySubscriptionStore();
        RecordingGateway gateway = new RecordingGateway();
        SubscriptionLifecycleService svc = service(store, inbox, gateway);

        UUID tenantId = UUID.randomUUID();
        store.save(new GraphSubscription(
                tenantId,
                "sub-expiring",
                "users/u/events",
                "updated",
                "https://example.test/hook",
                "cs",
                Instant.now().plus(Duration.ofHours(1)),
                "app"
        ));

        LifecycleNotification notification = new LifecycleNotification(
                "sub-expiring::lifecycle::reauthorizationRequired",
                "sub-expiring",
                "reauthorizationRequired",
                "cs",
                tenantId.toString()
        );

        assertTrue(svc.handleLifecycleNotification(notification, n -> { }));
        assertEquals(1, gateway.renewCalls.get());
    }

    private static SubscriptionLifecycleService service(
            InMemorySubscriptionStore store,
            InMemoryNotificationInbox inbox,
            SubscriptionGateway gateway
    ) {
        return new SubscriptionLifecycleService(
                gateway,
                store,
                inbox,
                InstantClock.systemUTC(),
                Duration.ofHours(6),
                Duration.ofHours(48)
        );
    }

    private static final class RecordingGateway implements SubscriptionGateway {
        private final AtomicInteger renewCalls = new AtomicInteger();

        @Override
        public GraphSubscription create(UUID tenantId, SubscriptionCreateRequest request) {
            return new GraphSubscription(
                    tenantId,
                    "created",
                    request.resource(),
                    request.changeType(),
                    request.notificationUrl(),
                    request.clientState(),
                    Instant.now().plus(request.expirationWindow()),
                    "app"
            );
        }

        @Override
        public GraphSubscription renew(UUID tenantId, String subscriptionId, Instant newExpiration) {
            renewCalls.incrementAndGet();
            return new GraphSubscription(
                    tenantId,
                    subscriptionId,
                    "users/u/events",
                    "updated",
                    "https://example.test/hook",
                    "cs",
                    newExpiration,
                    "app"
            );
        }

        @Override
        public java.util.Optional<GraphSubscription> get(UUID tenantId, String subscriptionId) {
            return java.util.Optional.empty();
        }

        @Override
        public void delete(UUID tenantId, String subscriptionId) {
        }
    }
}
