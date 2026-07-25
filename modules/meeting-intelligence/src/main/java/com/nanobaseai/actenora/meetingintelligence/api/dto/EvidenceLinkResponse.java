package com.nanobaseai.actenora.meetingintelligence.api.dto;

import com.nanobaseai.actenora.meetingintelligence.domain.model.EvidenceSubjectType;

import java.time.Instant;
import java.util.UUID;

public record EvidenceLinkResponse(
        UUID id,
        UUID noteId,
        UUID noteVersionId,
        EvidenceSubjectType subjectType,
        UUID subjectId,
        String evidenceSegmentId,
        Instant createdAt
) {
}
