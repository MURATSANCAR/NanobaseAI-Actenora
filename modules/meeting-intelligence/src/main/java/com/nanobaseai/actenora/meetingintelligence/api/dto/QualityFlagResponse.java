package com.nanobaseai.actenora.meetingintelligence.api.dto;

import com.nanobaseai.actenora.meetingintelligence.domain.model.EvidenceSubjectType;
import com.nanobaseai.actenora.meetingintelligence.domain.model.QualityFlagCode;

import java.time.Instant;
import java.util.UUID;

public record QualityFlagResponse(
        UUID id,
        QualityFlagCode code,
        String detail,
        EvidenceSubjectType subjectType,
        UUID subjectId,
        Instant createdAt
) {
}
