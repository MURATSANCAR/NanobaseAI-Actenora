package com.nanobaseai.actenora.security.notification;

import com.nanobaseai.actenora.aiprocessing.domain.job.AiJob;
import com.nanobaseai.actenora.identity.api.IdentityApi;
import com.nanobaseai.actenora.identity.api.Permission;
import com.nanobaseai.actenora.identity.api.UserView;
import com.nanobaseai.actenora.meeting.api.MeetingApi;
import com.nanobaseai.actenora.meetingintelligence.api.ledger.ContinuityLedgerApi;
import com.nanobaseai.actenora.meetingintelligence.application.port.ActionItemRepository;
import com.nanobaseai.actenora.meetingintelligence.domain.ledger.CommitmentConfirmation;
import com.nanobaseai.actenora.meetingintelligence.domain.model.ActionItem;
import com.nanobaseai.actenora.notification.api.NotificationApi;
import com.nanobaseai.actenora.notification.domain.UserNotificationType;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Platform-side writers that turn domain outcomes into in-app notifications.
 */
public final class PlatformUserNotificationPublisher {

    private static final Logger log = LoggerFactory.getLogger(PlatformUserNotificationPublisher.class);

    private final NotificationApi notificationApi;
    private final IdentityApi identityApi;
    private final Optional<MeetingApi> meetingApi;
    private final Optional<ContinuityLedgerApi> ledgerApi;
    private final Optional<ActionItemRepository> actionItems;

    public PlatformUserNotificationPublisher(
            NotificationApi notificationApi,
            IdentityApi identityApi,
            ObjectProvider<MeetingApi> meetingApi,
            ObjectProvider<ContinuityLedgerApi> ledgerApi,
            ObjectProvider<ActionItemRepository> actionItems
    ) {
        this.notificationApi = Objects.requireNonNull(notificationApi, "notificationApi");
        this.identityApi = Objects.requireNonNull(identityApi, "identityApi");
        this.meetingApi = Optional.ofNullable(meetingApi.getIfAvailable());
        this.ledgerApi = Optional.ofNullable(ledgerApi.getIfAvailable());
        this.actionItems = Optional.ofNullable(actionItems.getIfAvailable());
    }

    public void notifyDraftMinutesReady(
            UUID tenantId,
            UUID meetingOccurrenceId,
            UUID noteId,
            String meetingTitle
    ) {
        TenantId tid = TenantId.of(tenantId);
        String title = meetingTitle == null || meetingTitle.isBlank() ? "Meeting" : meetingTitle.trim();
        for (String oid : resolveOrganizerOids(tid, meetingOccurrenceId)) {
            notificationApi.publish(
                    tid,
                    oid,
                    UserNotificationType.DRAFT_MINUTES_READY,
                    "Draft minutes ready",
                    "\"" + title + "\" draft minutes are ready for review.",
                    "/meetings/" + meetingOccurrenceId,
                    "draft-minutes:" + noteId
            );
        }
    }

    public void notifyApprovalRequested(
            UUID tenantId,
            UUID noteId,
            UUID meetingOccurrenceId,
            UUID approvalId,
            String approverId
    ) {
        TenantId tid = TenantId.of(tenantId);
        Set<String> recipients = new LinkedHashSet<>();
        resolveOidForApproverId(tid, approverId).ifPresent(recipients::add);
        for (UserView user : identityApi.listUsers(tid)) {
            if (user.permissions() != null && user.permissions().contains(Permission.APPROVAL_DECIDE.code())) {
                if (user.entraObjectId() != null && !user.entraObjectId().isBlank()) {
                    recipients.add(user.entraObjectId());
                }
            }
        }
        for (String oid : recipients) {
            notificationApi.publish(
                    tid,
                    oid,
                    UserNotificationType.APPROVAL_REQUESTED,
                    "Approval requested",
                    "A meeting note is waiting for your approval.",
                    "/approvals",
                    "approval:" + approvalId
            );
        }
    }

    public void notifyAiJobFailed(AiJob job) {
        if (job == null) {
            return;
        }
        TenantId tid = TenantId.of(job.tenantId());
        String meetingTitle = resolveMeetingTitle(job.meetingOccurrenceId());
        for (String oid : resolveOrganizerOids(tid, job.meetingOccurrenceId())) {
            notificationApi.publish(
                    tid,
                    oid,
                    UserNotificationType.AI_JOB_FAILED,
                    "AI processing failed",
                    "Processing failed for \"" + meetingTitle + "\".",
                    "/meetings/" + job.meetingOccurrenceId(),
                    "ai-job-dead:" + job.id()
            );
        }
    }

    /**
     * Idempotent overdue fan-out for the current tenant (safe to call on list).
     */
    public void ensureOverdueNotifications(TenantId tenantId) {
        Objects.requireNonNull(tenantId, "tenantId");
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        if (actionItems.isPresent()) {
            for (ActionItem item : actionItems.get().findByTenantId(tenantId)) {
                if (item.status().isTerminal() || item.dueDate() == null || !item.dueDate().isBefore(today)) {
                    continue;
                }
                for (String oid : resolveOwnerOids(tenantId, item.owner())) {
                    notificationApi.publish(
                            tenantId,
                            oid,
                            UserNotificationType.ACTION_OVERDUE,
                            "Action overdue",
                            truncate(item.text()),
                            "/actions",
                            "action-overdue:" + item.id() + ":" + item.dueDate()
                    );
                }
            }
        }
        if (ledgerApi.isPresent()) {
            for (CommitmentConfirmation c : ledgerApi.get().overdueCommitments(tenantId)) {
                for (String oid : resolveOwnerOids(tenantId, c.owner().orElse(null))) {
                    notificationApi.publish(
                            tenantId,
                            oid,
                            UserNotificationType.COMMITMENT_OVERDUE,
                            "Commitment overdue",
                            truncate(c.text()),
                            "/commitments",
                            "commitment-overdue:" + c.commitmentId()
                    );
                }
            }
        }
    }

    private Set<String> resolveOrganizerOids(TenantId tenantId, UUID meetingOccurrenceId) {
        Set<String> oids = new LinkedHashSet<>();
        if (meetingApi.isEmpty() || meetingOccurrenceId == null) {
            return oids;
        }
        try {
            var meeting = meetingApi.get().getMeeting(meetingOccurrenceId);
            var participants = meetingApi.get().listParticipants(meetingOccurrenceId);
            participants.stream()
                    .filter(p -> p.participantType() != null && "ORGANIZER".equalsIgnoreCase(p.participantType().name()))
                    .map(p -> p.entraUserId())
                    .filter(id -> id != null && !id.isBlank())
                    .forEach(oids::add);
            if (oids.isEmpty() && meeting.organizerUserId() != null) {
                oids.add(meeting.organizerUserId().toString());
            }
            if (oids.isEmpty()) {
                participants.stream()
                        .map(p -> p.email())
                        .filter(email -> email != null && !email.isBlank())
                        .findFirst()
                        .flatMap(email -> resolveOidByEmailOrName(tenantId, email))
                        .ifPresent(oids::add);
            }
        } catch (RuntimeException ex) {
            log.debug("Organizer resolve failed meetingId={} reason={}", meetingOccurrenceId, ex.getMessage());
        }
        return oids;
    }

    private Optional<String> resolveOidForApproverId(TenantId tenantId, String approverId) {
        if (approverId == null || approverId.isBlank()) {
            return Optional.empty();
        }
        String trimmed = approverId.trim();
        try {
            UUID userId = UUID.fromString(trimmed);
            return identityApi.listUsers(tenantId).stream()
                    .filter(u -> u.id().equals(userId))
                    .map(UserView::entraObjectId)
                    .filter(oid -> oid != null && !oid.isBlank())
                    .findFirst();
        } catch (IllegalArgumentException ignored) {
            return resolveOidByEmailOrName(tenantId, trimmed);
        }
    }

    private Set<String> resolveOwnerOids(TenantId tenantId, String owner) {
        Set<String> oids = new LinkedHashSet<>();
        if (owner == null || owner.isBlank()) {
            return oids;
        }
        resolveOidByEmailOrName(tenantId, owner).ifPresent(oids::add);
        return oids;
    }

    private Optional<String> resolveOidByEmailOrName(TenantId tenantId, String needle) {
        String n = needle.trim().toLowerCase(Locale.ROOT);
        return identityApi.listUsers(tenantId).stream()
                .filter(u ->
                        (u.email() != null && u.email().toLowerCase(Locale.ROOT).equals(n))
                                || (u.displayName() != null && u.displayName().toLowerCase(Locale.ROOT).equals(n))
                                || (u.entraObjectId() != null && u.entraObjectId().equalsIgnoreCase(needle.trim()))
                )
                .map(UserView::entraObjectId)
                .filter(oid -> oid != null && !oid.isBlank())
                .findFirst();
    }

    private String resolveMeetingTitle(UUID meetingOccurrenceId) {
        if (meetingApi.isEmpty() || meetingOccurrenceId == null) {
            return "Meeting";
        }
        try {
            String title = meetingApi.get().getMeeting(meetingOccurrenceId).title();
            return title == null || title.isBlank() ? "Meeting" : title.trim();
        } catch (RuntimeException ex) {
            return "Meeting";
        }
    }

    private static String truncate(String text) {
        if (text == null || text.isBlank()) {
            return "Open item needs attention.";
        }
        String t = text.trim();
        return t.length() <= 160 ? t : t.substring(0, 157) + "...";
    }
}
