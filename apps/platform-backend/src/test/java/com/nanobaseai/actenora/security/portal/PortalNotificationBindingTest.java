package com.nanobaseai.actenora.security.portal;

import com.nanobaseai.actenora.identity.api.Permission;
import com.nanobaseai.actenora.identity.domain.SystemRole;
import com.nanobaseai.actenora.notification.api.NotificationApi;
import com.nanobaseai.actenora.notification.application.UserNotificationService;
import com.nanobaseai.actenora.notification.domain.UserNotificationType;
import com.nanobaseai.actenora.notification.infrastructure.persistence.InMemoryUserNotificationRepository;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;
import com.nanobaseai.actenora.sharedkernel.security.AuthenticatedPrincipal;
import com.nanobaseai.actenora.sharedkernel.security.TenantSecurityContext;
import com.nanobaseai.actenora.sharedkernel.time.InstantClock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PortalNotificationBindingTest {

    private final TenantId tenantId = TenantId.of(UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"));
    private final UUID userId = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private final String oid = "entra-oid-notify-1";

    private InMemoryUserNotificationRepository repository;
    private NotificationApi notificationApi;

    @BeforeEach
    void setUp() {
        repository = new InMemoryUserNotificationRepository();
        notificationApi = new UserNotificationService(
                repository,
                new InstantClock(Clock.fixed(Instant.parse("2026-07-27T12:00:00Z"), ZoneOffset.UTC))
        );
        TenantSecurityContext.set(new AuthenticatedPrincipal(
                tenantId,
                userId,
                oid,
                "notify@example.com",
                "Notify User",
                Set.of(SystemRole.TENANT_ADMIN.code()),
                Set.of(Permission.MEETING_READ.code()),
                false
        ));
    }

    @AfterEach
    void tearDown() {
        TenantSecurityContext.clear();
        repository.clear();
    }

    @Test
    void publishDedupeAndUnreadFlow() {
        assertTrue(notificationApi.publish(
                tenantId,
                oid,
                UserNotificationType.APPROVAL_REQUESTED,
                "Approval requested",
                "Note waiting",
                "/approvals",
                "approval:abc"
        ));
        assertFalse(notificationApi.publish(
                tenantId,
                oid,
                UserNotificationType.APPROVAL_REQUESTED,
                "Approval requested",
                "Note waiting",
                "/approvals",
                "approval:abc"
        ));

        var list = notificationApi.listForRecipient(tenantId, oid, 20);
        assertEquals(1, list.items().size());
        assertEquals(1, list.unreadCount());

        assertTrue(notificationApi.markRead(tenantId, oid, list.items().getFirst().id()));
        assertEquals(0, notificationApi.listForRecipient(tenantId, oid, 20).unreadCount());

        notificationApi.publish(
                tenantId,
                oid,
                UserNotificationType.DRAFT_MINUTES_READY,
                "Draft minutes ready",
                "Ready",
                "/meetings/1",
                "draft-minutes:1"
        );
        assertEquals(1, notificationApi.markAllRead(tenantId, oid));
        assertEquals(0, notificationApi.listForRecipient(tenantId, oid, 20).unreadCount());
    }

    @Test
    void recipientIsolation() {
        notificationApi.publish(
                tenantId,
                oid,
                UserNotificationType.AI_JOB_FAILED,
                "AI processing failed",
                "Failed",
                "/meetings/1",
                "ai-job-dead:1"
        );
        assertEquals(0, notificationApi.listForRecipient(tenantId, "other-oid", 20).items().size());
        assertEquals(1, notificationApi.listForRecipient(tenantId, oid, 20).items().size());
    }
}
