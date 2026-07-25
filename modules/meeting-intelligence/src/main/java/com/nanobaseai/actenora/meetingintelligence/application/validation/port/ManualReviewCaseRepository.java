package com.nanobaseai.actenora.meetingintelligence.application.validation.port;

import com.nanobaseai.actenora.meetingintelligence.domain.validation.ManualReviewCase;
import com.nanobaseai.actenora.meetingintelligence.domain.validation.ManualReviewStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ManualReviewCaseRepository {

    ManualReviewCase save(ManualReviewCase reviewCase);

    Optional<ManualReviewCase> findById(UUID tenantId, UUID caseId);

    Optional<ManualReviewCase> findOpenByDecision(UUID tenantId, UUID qualityGateDecisionId);

    List<ManualReviewCase> findByTenant(UUID tenantId, ManualReviewStatus status);
}
