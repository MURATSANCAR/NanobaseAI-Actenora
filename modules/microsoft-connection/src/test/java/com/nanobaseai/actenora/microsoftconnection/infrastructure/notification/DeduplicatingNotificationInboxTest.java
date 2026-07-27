package com.nanobaseai.actenora.microsoftconnection.infrastructure.notification;

import com.nanobaseai.actenora.sharedkernel.coordination.InMemoryShortLivedDeduplicator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeduplicatingNotificationInboxTest {

    @Test
    void redisFrontSkipsDuplicateBeforeDurableClaim() {
        InMemoryNotificationInbox durable = new InMemoryNotificationInbox();
        DeduplicatingNotificationInbox inbox = new DeduplicatingNotificationInbox(
                durable, new InMemoryShortLivedDeduplicator());

        assertTrue(inbox.claim("graph-consumer", "n-1"));
        assertFalse(inbox.claim("graph-consumer", "n-1"));
        assertFalse(durable.claim("graph-consumer", "n-1"));
    }

    @Test
    void distinctNotificationsStillReachDurableStore() {
        InMemoryNotificationInbox durable = new InMemoryNotificationInbox();
        DeduplicatingNotificationInbox inbox = new DeduplicatingNotificationInbox(
                durable, new InMemoryShortLivedDeduplicator());

        assertTrue(inbox.claim("graph-consumer", "n-1"));
        assertTrue(inbox.claim("graph-consumer", "n-2"));
    }
}
