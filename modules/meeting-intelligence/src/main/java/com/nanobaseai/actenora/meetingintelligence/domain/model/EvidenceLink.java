package com.nanobaseai.actenora.meetingintelligence.domain.model;

import com.nanobaseai.actenora.meetingintelligence.domain.exception.TenantIsolationViolationException;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Link from an intelligence subject to an opaque transcript evidence segment id.
 */
public final class EvidenceLink {

    private final UUID id;
    private final TenantId tenantId;
    private final UUID noteId;
    private final UUID noteVersionId;
    private final EvidenceSubjectType subjectType;
    private final UUID subjectId;
    private final String evidenceSegmentId;
    private final Instant createdAt;

    private EvidenceLink(
            UUID id,
            TenantId tenantId,
            UUID noteId,
            UUID noteVersionId,
            EvidenceSubjectType subjectType,
            UUID subjectId,
            String evidenceSegmentId,
            Instant createdAt
    ) {
        this.id = Objects.requireNonNull(id, "id");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.noteId = Objects.requireNonNull(noteId, "noteId");
        this.noteVersionId = Objects.requireNonNull(noteVersionId, "noteVersionId");
        this.subjectType = Objects.requireNonNull(subjectType, "subjectType");
        this.subjectId = Objects.requireNonNull(subjectId, "subjectId");
        this.evidenceSegmentId = requireText(evidenceSegmentId, "evidenceSegmentId");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    }

    public static EvidenceLink create(
            TenantId tenantId,
            UUID noteId,
            UUID noteVersionId,
            EvidenceSubjectType subjectType,
            UUID subjectId,
            String evidenceSegmentId,
            Instant now
    ) {
        return new EvidenceLink(
                UUID.randomUUID(),
                tenantId,
                noteId,
                noteVersionId,
                subjectType,
                subjectId,
                evidenceSegmentId,
                now
        );
    }

    public static EvidenceLink rehydrate(
            UUID id,
            TenantId tenantId,
            UUID noteId,
            UUID noteVersionId,
            EvidenceSubjectType subjectType,
            UUID subjectId,
            String evidenceSegmentId,
            Instant createdAt
    ) {
        return new EvidenceLink(
                id, tenantId, noteId, noteVersionId, subjectType, subjectId, evidenceSegmentId, createdAt
        );
    }

    public void assertTenant(TenantId tenantId) {
        if (!this.tenantId.equals(tenantId)) {
            throw new TenantIsolationViolationException();
        }
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim();
    }

    public UUID id() { return id; }
    public TenantId tenantId() { return tenantId; }
    public UUID noteId() { return noteId; }
    public UUID noteVersionId() { return noteVersionId; }
    public EvidenceSubjectType subjectType() { return subjectType; }
    public UUID subjectId() { return subjectId; }
    public String evidenceSegmentId() { return evidenceSegmentId; }
    public Instant createdAt() { return createdAt; }
}
