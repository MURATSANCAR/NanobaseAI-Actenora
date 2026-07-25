package com.nanobaseai.actenora.microsoftconnection.infrastructure.notification;

import com.nanobaseai.actenora.microsoftconnection.application.port.NotificationInbox;

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory Graph notification idempotency gate (FAZ 28 duplicate notification scenario).
 */
public final class InMemoryNotificationInbox implements NotificationInbox {

    private final Set<String> claimed = ConcurrentHashMap.newKeySet();

    @Override
    public boolean claim(String consumerName, String notificationId) {
        Objects.requireNonNull(consumerName, "consumerName");
        Objects.requireNonNull(notificationId, "notificationId");
        if (consumerName.isBlank() || notificationId.isBlank()) {
            throw new IllegalArgumentException("consumerName and notificationId must not be blank");
        }
        return claimed.add(consumerName + "::" + notificationId);
    }

    public int size() {
        return claimed.size();
    }

    public void clear() {
        claimed.clear();
    }
}
