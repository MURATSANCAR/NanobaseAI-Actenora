package com.nanobaseai.actenora.notification.application;

import com.nanobaseai.actenora.notification.api.NotificationApi;
import com.nanobaseai.actenora.notification.api.UserNotificationListView;
import com.nanobaseai.actenora.notification.api.UserNotificationView;
import com.nanobaseai.actenora.notification.application.port.UserNotificationRepository;
import com.nanobaseai.actenora.notification.domain.UserNotification;
import com.nanobaseai.actenora.notification.domain.UserNotificationType;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;
import com.nanobaseai.actenora.sharedkernel.time.InstantClock;

import java.util.Objects;
import java.util.UUID;

public final class UserNotificationService implements NotificationApi {

    private final UserNotificationRepository repository;
    private final InstantClock clock;

    public UserNotificationService(UserNotificationRepository repository, InstantClock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public UserNotificationListView listForRecipient(TenantId tenantId, String recipientOid, int limit) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(recipientOid, "recipientOid");
        var items = repository.listForRecipient(tenantId, recipientOid, limit).stream()
                .map(UserNotificationService::toView)
                .toList();
        return new UserNotificationListView(items, repository.countUnread(tenantId, recipientOid));
    }

    @Override
    public boolean markRead(TenantId tenantId, String recipientOid, UUID notificationId) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(recipientOid, "recipientOid");
        Objects.requireNonNull(notificationId, "notificationId");
        return repository.markRead(tenantId, recipientOid, notificationId);
    }

    @Override
    public int markAllRead(TenantId tenantId, String recipientOid) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(recipientOid, "recipientOid");
        return repository.markAllRead(tenantId, recipientOid);
    }

    @Override
    public boolean publish(
            TenantId tenantId,
            String recipientOid,
            UserNotificationType type,
            String title,
            String body,
            String href,
            String dedupeKey
    ) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(recipientOid, "recipientOid");
        Objects.requireNonNull(type, "type");
        UserNotification notification = UserNotification.create(
                tenantId,
                recipientOid,
                type,
                title,
                body,
                href,
                dedupeKey,
                clock.instant()
        );
        return repository.insertIfAbsent(notification).isPresent();
    }

    private static UserNotificationView toView(UserNotification n) {
        return new UserNotificationView(
                n.id(),
                n.type(),
                n.title(),
                n.body(),
                n.href(),
                n.createdAt(),
                n.readAt().orElse(null)
        );
    }
}
