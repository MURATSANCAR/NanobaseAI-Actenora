package com.nanobaseai.actenora.notification.application.port;

import com.nanobaseai.actenora.notification.domain.UserNotification;
import com.nanobaseai.actenora.notification.domain.UserNotificationType;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserNotificationRepository {

    /**
     * Inserts when {@code (tenantId, recipientOid, type, dedupeKey)} is new.
     *
     * @return saved row, or empty when a duplicate already exists
     */
    Optional<UserNotification> insertIfAbsent(UserNotification notification);

    Optional<UserNotification> findById(TenantId tenantId, UUID id);

    List<UserNotification> listForRecipient(TenantId tenantId, String recipientOid, int limit);

    int countUnread(TenantId tenantId, String recipientOid);

    boolean markRead(TenantId tenantId, String recipientOid, UUID id);

    int markAllRead(TenantId tenantId, String recipientOid);

    boolean exists(TenantId tenantId, String recipientOid, UserNotificationType type, String dedupeKey);
}
