package com.nanobaseai.actenora.transcript.domain;

import com.nanobaseai.actenora.meeting.api.MeetingId;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;

import java.util.UUID;

/**
 * Transcript aggregate. References meetings only via {@link MeetingId} from the public API.
 */
public class TranscriptEntity {

    private final UUID id;
    private final TenantId tenantId;
    private final MeetingId meetingId;

    public TranscriptEntity(UUID id, TenantId tenantId, MeetingId meetingId) {
        this.id = id;
        this.tenantId = tenantId;
        this.meetingId = meetingId;
    }

    public UUID id() {
        return id;
    }

    public TenantId tenantId() {
        return tenantId;
    }

    public MeetingId meetingId() {
        return meetingId;
    }
}
