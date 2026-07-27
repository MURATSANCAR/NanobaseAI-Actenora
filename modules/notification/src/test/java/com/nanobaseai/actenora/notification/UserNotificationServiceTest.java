package com.nanobaseai.actenora.notification;

import com.nanobaseai.actenora.notification.api.NotificationApi;
import com.nanobaseai.actenora.notification.application.UserNotificationService;
import com.nanobaseai.actenora.notification.domain.UserNotificationType;
import com.nanobaseai.actenora.notification.infrastructure.persistence.InMemoryUserNotificationRepository;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;
import com.nanobaseai.actenora.sharedkernel.time.InstantClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserNotificationServiceTest {

    private InMemoryUserNotificationRepository repository;
    private NotificationApi api;
    private final TenantId tenantId = TenantId.of(UUID.fromString("11111111-1111-1111-1111-111111111111"));

    @BeforeEach
    void setUp() {
        repository = new InMemoryUserNotificationRepository();
        api = new UserNotificationService(
                repository,
                new InstantClock(java.time.Clock.fixed(Instant.parse("2026-07-27T12:00:00Z"), java.time.ZoneOffset.UTC))
        );
    }

    @Test
    void publishIsIdempotentByDedupeKey() {
        assertTrue(api.publish(
                tenantId, "oid-a", UserNotificationType.APPROVAL_REQUESTED,
                "Approval needed", "Note ready", "/approvals", "approval:1"
        ));
        assertFalse(api.publish(
                tenantId, "oid-a", UserNotificationType.APPROVAL_REQUESTED,
                "Approval needed", "Note ready", "/approvals", "approval:1"
        ));
        assertEquals(1, api.listForRecipient(tenantId, "oid-a", 20).items().size());
        assertEquals(1, api.listForRecipient(tenantId, "oid-a", 20).unreadCount());
    }

    @Test
    void markReadAndMarkAllRead() {
        api.publish(tenantId, "oid-a", UserNotificationType.ACTION_OVERDUE,
                "Overdue", "Do it", "/actions", "action:1");
        api.publish(tenantId, "oid-a", UserNotificationType.COMMITMENT_OVERDUE,
                "Commitment", "Confirm", "/commitments", "commitment:1");
        var list = api.listForRecipient(tenantId, "oid-a", 20);
        assertEquals(2, list.unreadCount());
        assertTrue(api.markRead(tenantId, "oid-a", list.items().getFirst().id()));
        assertEquals(1, api.listForRecipient(tenantId, "oid-a", 20).unreadCount());
        assertEquals(1, api.markAllRead(tenantId, "oid-a"));
        assertEquals(0, api.listForRecipient(tenantId, "oid-a", 20).unreadCount());
    }
}
