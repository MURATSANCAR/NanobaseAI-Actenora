package com.nanobaseai.actenora.microsoftconnection.infrastructure.notification;

import com.nanobaseai.actenora.microsoftconnection.application.port.NotificationInbox;
import com.nanobaseai.actenora.sharedkernel.coordination.ShortLivedDeduplicator;

import java.time.Duration;
import java.util.Objects;

/**
 * Short-lived dedup front for durable {@link NotificationInbox}.
 * Database remains source of truth; deduplicator only skips obvious duplicates.
 */
public final class DeduplicatingNotificationInbox implements NotificationInbox {

    private static final Duration TTL = Duration.ofHours(24);

    private final NotificationInbox durable;
    private final ShortLivedDeduplicator deduplicator;

    public DeduplicatingNotificationInbox(NotificationInbox durable, ShortLivedDeduplicator deduplicator) {
        this.durable = Objects.requireNonNull(durable, "durable");
        this.deduplicator = Objects.requireNonNull(deduplicator, "deduplicator");
    }

    @Override
    public boolean claim(String consumerName, String notificationId) {
        String key = "graph:inbox:" + consumerName + ":" + notificationId;
        try {
            if (!deduplicator.tryClaim(key, TTL)) {
                return false;
            }
        } catch (RuntimeException ignored) {
            // Coordinator unavailable — fall through to durable SoT.
        }
        return durable.claim(consumerName, notificationId);
    }
}
