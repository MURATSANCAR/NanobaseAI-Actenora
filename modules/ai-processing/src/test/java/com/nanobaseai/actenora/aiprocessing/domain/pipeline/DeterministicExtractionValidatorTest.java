package com.nanobaseai.actenora.aiprocessing.domain.pipeline;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DeterministicExtractionValidatorTest {

    private final DeterministicExtractionValidator validator = new DeterministicExtractionValidator();

    @Test
    void rejectsUnknownEvidenceId() {
        ExtractionBundle bundle = new ExtractionBundle(
                java.util.List.of(new TopicCandidate("T", java.util.List.of("missing"), 0.9)),
                java.util.List.of(),
                java.util.List.of(),
                java.util.List.of(),
                java.util.List.of(),
                java.util.List.of(),
                java.util.List.of(),
                java.util.List.of("missing"),
                0.9
        );
        PipelineException ex = assertThrows(
                PipelineException.class,
                () -> validator.validate(bundle, java.util.Set.of("seg-1"), "hello")
        );
        assertEquals(FailureCategory.EVIDENCE_MISSING, ex.category());
    }

    @Test
    void suggestionAsDecisionRejected() {
        ExtractionBundle bundle = new ExtractionBundle(
                java.util.List.of(),
                java.util.List.of(new DecisionCandidate("We should maybe postpone", java.util.List.of("seg-1"), 0.9)),
                java.util.List.of(),
                java.util.List.of(),
                java.util.List.of(),
                java.util.List.of(),
                java.util.List.of(),
                java.util.List.of("seg-1"),
                0.9
        );
        PipelineException ex = assertThrows(
                PipelineException.class,
                () -> validator.validate(bundle, java.util.Set.of("seg-1"), "We should maybe postpone")
        );
        assertEquals(FailureCategory.SCHEMA_VIOLATION, ex.category());
    }
}
