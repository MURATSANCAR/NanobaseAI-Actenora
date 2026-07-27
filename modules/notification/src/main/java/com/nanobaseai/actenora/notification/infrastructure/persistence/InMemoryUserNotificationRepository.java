package com.nanobaseai.actenora.notification.infrastructure.persistence;

import com.nanobaseai.actenora.notification.application.port.UserNotificationRepository;
import com.nanobaseai.actenora.notification.domain.UserNotification;
import com.nanobaseai.actenora.notification.domain.UserNotificationType;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryUserNotificationRepository implements UserNotificationRepository {

    private final Map<UUID, UserNotification> byId = new ConcurrentHashMap<>();

    @Override
    public Optional<UserNotification> insertIfAbsent(UserNotification notification) {
        if (exists(
                notification.tenantId(),
                notification.recipientOid(),
                notification.type(),
                notification.dedupeKey()
        )) {
            return Optional.empty();
        }
        byId.put(notification.id(), notification);
        return Optional.of(notification);
    }

    @Override
    public Optional<UserNotification> findById(TenantId tenantId, UUID id) {
        return Optional.ofNullable(byId.get(id))
                .filter(n -> n.tenantId().equals(tenantId));
    }

    @Override
    public List<UserNotification> listForRecipient(TenantId tenantId, String recipientOid, int limit) {
        int capped = Math.max(1, Math.min(limit, 100));
        return byId.values().stream()
                .filter(n -> n.tenantId().equals(tenantId))
                .filter(n -> n.recipientOid().equalsIgnoreCase(recipientOid))
                .sorted(Comparator.comparing(UserNotification::createdAt).reversed())
                .limit(capped)
                .toList();
    }

    @Override
    public int countUnread(TenantId tenantId, String recipientOid) {
        return (int) byId.values().stream()
                .filter(n -> n.tenantId().equals(tenantId))
                .filter(n -> n.recipientOid().equalsIgnoreCase(recipientOid))
                .filter(UserNotification::isUnread)
                .count();
    }

    @Override
    public boolean markRead(TenantId tenantId, String recipientOid, UUID id) {
        UserNotification existing = byId.get(id);
        if (existing == null
                || !existing.tenantId().equals(tenantId)
                || !existing.recipientOid().equalsIgnoreCase(recipientOid)) {
            return false;
        }
        existing.markRead(Instant.now());
        return true;
    }

    @Override
    public int markAllRead(TenantId tenantId, String recipientOid) {
        Instant now = Instant.now();
        int count = 0;
        for (UserNotification n : byId.values()) {
            if (n.tenantId().equals(tenantId)
                    && n.recipientOid().equalsIgnoreCase(recipientOid)
                    && n.isUnread()) {
                n.markRead(now);
                count++;
            }
        }
        return count;
    }

    @Override
    public boolean exists(TenantId tenantId, String recipientOid, UserNotificationType type, String dedupeKey) {
        return byId.values().stream().anyMatch(n ->
                n.tenantId().equals(tenantId)
                        && n.recipientOid().equalsIgnoreCase(recipientOid)
                        && n.type() == type
                        && n.dedupeKey().equals(dedupeKey)
        );
    }

    public void clear() {
        byId.clear();
    }
}
