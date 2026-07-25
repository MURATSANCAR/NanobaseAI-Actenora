package com.nanobaseai.actenora.meetingintelligence.domain.model;

import com.nanobaseai.actenora.meetingintelligence.domain.exception.TenantIsolationViolationException;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class QualityFlag {

    private final UUID id;
    private final TenantId tenantId;
    private final UUID noteId;
    private final UUID noteVersionId;
    private final QualityFlagCode code;
    private final String detail;
    private final EvidenceSubjectType subjectType;
    private final UUID subjectId;
    private final Instant createdAt;

    private QualityFlag(
            UUID id,
            TenantId tenantId,
            UUID noteId,
            UUID noteVersionId,
            QualityFlagCode code,
            String detail,
            EvidenceSubjectType subjectType,
            UUID subjectId,
            Instant createdAt
    ) {
        this.id = Objects.requireNonNull(id, "id");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.noteId = Objects.requireNonNull(noteId, "noteId");
        this.noteVersionId = Objects.requireNonNull(noteVersionId, "noteVersionId");
        this.code = Objects.requireNonNull(code, "code");
        this.detail = detail == null ? "" : detail.trim();
        this.subjectType = subjectType;
        this.subjectId = subjectId;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    }

    public static QualityFlag create(
            TenantId tenantId,
            UUID noteId,
            UUID noteVersionId,
            QualityFlagCode code,
            String detail,
            EvidenceSubjectType subjectType,
            UUID subjectId,
            Instant now
    ) {
        return new QualityFlag(
                UUID.randomUUID(),
                tenantId,
                noteId,
                noteVersionId,
                code,
                detail,
                subjectType,
                subjectId,
                now
        );
    }

    public static QualityFlag rehydrate(
            UUID id,
            TenantId tenantId,
            UUID noteId,
            UUID noteVersionId,
            QualityFlagCode code,
            String detail,
            EvidenceSubjectType subjectType,
            UUID subjectId,
            Instant createdAt
    ) {
        return new QualityFlag(
                id, tenantId, noteId, noteVersionId, code, detail, subjectType, subjectId, createdAt
        );
    }

    public void assertTenant(TenantId tenantId) {
        if (!this.tenantId.equals(tenantId)) {
            throw new TenantIsolationViolationException();
        }
    }

    public UUID id() { return id; }
    public TenantId tenantId() { return tenantId; }
    public UUID noteId() { return noteId; }
    public UUID noteVersionId() { return noteVersionId; }
    public QualityFlagCode code() { return code; }
    public String detail() { return detail; }
    public EvidenceSubjectType subjectType() { return subjectType; }
    public UUID subjectId() { return subjectId; }
    public Instant createdAt() { return createdAt; }
}
