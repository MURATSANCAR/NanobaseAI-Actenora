package com.nanobaseai.actenora.meeting.infrastructure.relation;

import com.nanobaseai.actenora.meeting.application.port.MeetingAuditPort;
import com.nanobaseai.actenora.meeting.application.relation.port.RelationAuditPort;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Bridges FAZ 7 relation audits into the shared meeting audit port (Audit BC via platform). */
public final class MeetingAuditRelationAuditPort implements RelationAuditPort {

    private final MeetingAuditPort meetingAuditPort;

    public MeetingAuditRelationAuditPort(MeetingAuditPort meetingAuditPort) {
        this.meetingAuditPort = Objects.requireNonNull(meetingAuditPort, "meetingAuditPort");
    }

    @Override
    public void record(
            UUID tenantId,
            String actor,
            String action,
            String resourceType,
            UUID resourceId,
            Map<String, Object> metadata,
            Instant occurredAt
    ) {
        UUID actorUserId;
        try {
            actorUserId = UUID.fromString(actor);
        } catch (RuntimeException ex) {
            actorUserId = UUID.nameUUIDFromBytes(Objects.requireNonNullElse(actor, "system").getBytes());
        }
        meetingAuditPort.record(
                TenantId.of(tenantId),
                actorUserId,
                action,
                resourceType,
                resourceId,
                metadata
        );
    }
}
