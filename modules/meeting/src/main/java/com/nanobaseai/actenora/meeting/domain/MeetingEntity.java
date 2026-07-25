package com.nanobaseai.actenora.meeting.domain;

import com.nanobaseai.actenora.sharedkernel.domain.TenantId;

import java.util.UUID;

/**
 * Meeting aggregate root. Internal to the meeting module — never imported by peers.
 */
public class MeetingEntity {

    private final UUID id;
    private final TenantId tenantId;
    private String title;

    public MeetingEntity(UUID id, TenantId tenantId, String title) {
        this.id = id;
        this.tenantId = tenantId;
        this.title = title;
    }

    public UUID id() {
        return id;
    }

    public TenantId tenantId() {
        return tenantId;
    }

    public String title() {
        return title;
    }
}
