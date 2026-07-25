package com.nanobaseai.actenora.microsoftconnection.application.port;

/**
 * Idempotency gate for Graph change / lifecycle notifications.
 * Duplicate {@code notificationId} for the same consumer is skipped.
 */
public interface NotificationInbox {

    /**
     * @return true if this is the first time seeing the notification (caller should process)
     */
    boolean claim(String consumerName, String notificationId);
}
