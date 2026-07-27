package com.nanobaseai.actenora.notification.api;

import com.nanobaseai.actenora.notification.domain.UserNotificationType;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;

import java.util.UUID;

/**
 * Public façade for in-app user notifications.
 */
public interface NotificationApi {

    UserNotificationListView listForRecipient(TenantId tenantId, String recipientOid, int limit);

    boolean markRead(TenantId tenantId, String recipientOid, UUID notificationId);

    int markAllRead(TenantId tenantId, String recipientOid);

    /**
     * Idempotent write keyed by {@code (tenant, recipient, type, dedupeKey)}.
     *
     * @return true when a new row was inserted
     */
    boolean publish(
            TenantId tenantId,
            String recipientOid,
            UserNotificationType type,
            String title,
            String body,
            String href,
            String dedupeKey
    );
}
