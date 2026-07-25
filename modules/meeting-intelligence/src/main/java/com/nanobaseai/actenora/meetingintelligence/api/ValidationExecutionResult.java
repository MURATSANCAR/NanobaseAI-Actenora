package com.nanobaseai.actenora.meetingintelligence.api;

import com.nanobaseai.actenora.meetingintelligence.domain.validation.ManualReviewCase;
import com.nanobaseai.actenora.meetingintelligence.domain.validation.QualityGateDecision;
import com.nanobaseai.actenora.meetingintelligence.domain.validation.ValidationMetrics;
import com.nanobaseai.actenora.meetingintelligence.domain.validation.ValidationRun;

import java.util.Optional;

public record ValidationExecutionResult(
        ValidationRun run,
        QualityGateDecision decision,
        Optional<ManualReviewCase> manualReviewCase,
        ValidationMetrics runMetrics
) {
}
