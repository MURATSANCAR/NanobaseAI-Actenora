package com.nanobaseai.actenora.aiprocessing.domain.pipeline;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Deterministic post-model validation (fail closed). Does not invent content.
 */
public final class DeterministicExtractionValidator {

    private static final Pattern DATE_LIKE = Pattern.compile(
            "\\b(\\d{4}-\\d{2}-\\d{2}|\\d{1,2}[./]\\d{1,2}[./]\\d{2,4}|\\d{1,2}\\s+(January|February|March|April|May|June|July|August|September|October|November|December|Ocak|Şubat|Mart|Nisan|Mayıs|Haziran|Temmuz|Ağustos|Eylül|Ekim|Kasım|Aralık)\\b)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );

    private static final Pattern SUGGESTION = Pattern.compile(
            "(?i)\\b(should|could|might|öner|öneri|belki|perhaps|maybe)\\b"
    );

    private static final double LOW_CONFIDENCE_THRESHOLD = 0.55d;

    public void validate(
            ExtractionBundle bundle,
            Set<String> allowedEvidenceIds,
            String transcriptCorpus
    ) {
        Objects.requireNonNull(bundle, "bundle");
        Objects.requireNonNull(allowedEvidenceIds, "allowedEvidenceIds");
        Objects.requireNonNull(transcriptCorpus, "transcriptCorpus");

        assertEvidenceSubset(bundle.evidenceSegmentIds(), allowedEvidenceIds, "bundle");

        for (TopicCandidate topic : bundle.topics()) {
            assertRecordEvidence(topic.evidenceSegmentIds(), allowedEvidenceIds, "topic");
        }
        for (DecisionCandidate decision : bundle.decisions()) {
            assertRecordEvidence(decision.evidenceSegmentIds(), allowedEvidenceIds, "decision");
            assertNotSuggestionAsDecision(decision.text());
        }
        assertNoDuplicateDecisions(bundle.decisions());

        for (ActionItemCandidate item : bundle.actionItems()) {
            assertRecordEvidence(item.evidenceSegmentIds(), allowedEvidenceIds, "actionItem");
            assertDueDateGrounded(item.dueDate(), transcriptCorpus);
        }
        for (RiskCandidate risk : bundle.risks()) {
            assertRecordEvidence(risk.evidenceSegmentIds(), allowedEvidenceIds, "risk");
        }
        for (OpenQuestionCandidate question : bundle.openQuestions()) {
            assertRecordEvidence(question.evidenceSegmentIds(), allowedEvidenceIds, "openQuestion");
        }
        for (CommitmentCandidate commitment : bundle.commitments()) {
            assertRecordEvidence(commitment.evidenceSegmentIds(), allowedEvidenceIds, "commitment");
        }
    }

    public boolean requiresManualReview(ExtractionBundle bundle) {
        if (bundle.confidence() < LOW_CONFIDENCE_THRESHOLD) {
            return true;
        }
        return bundle.qualityFlags().stream()
                .anyMatch(flag -> flag.toLowerCase(Locale.ROOT).contains("manual")
                        || flag.equalsIgnoreCase("LOW_CONFIDENCE"));
    }

    private void assertRecordEvidence(List<String> evidenceIds, Set<String> allowed, String kind) {
        if (evidenceIds == null || evidenceIds.isEmpty()) {
            throw new PipelineException(
                    FailureCategory.EVIDENCE_MISSING,
                    PipelineStage.DETERMINISTIC_VALIDATE,
                    "Evidence missing for " + kind
            );
        }
        assertEvidenceSubset(evidenceIds, allowed, kind);
    }

    private void assertEvidenceSubset(List<String> evidenceIds, Set<String> allowed, String kind) {
        for (String id : evidenceIds) {
            if (!allowed.contains(id)) {
                throw new PipelineException(
                        FailureCategory.EVIDENCE_MISSING,
                        PipelineStage.DETERMINISTIC_VALIDATE,
                        "Unknown evidenceSegmentId for " + kind + ": " + id
                );
            }
        }
    }

    private void assertDueDateGrounded(String dueDate, String transcriptCorpus) {
        if (dueDate == null || dueDate.isBlank()) {
            return;
        }
        String corpus = transcriptCorpus.toLowerCase(Locale.ROOT);
        String needle = dueDate.toLowerCase(Locale.ROOT).trim();
        if (corpus.contains(needle)) {
            return;
        }
        // Models often normalize "gelecek hafta cuma" to ISO. Do not fail-close the whole
        // extraction for due-date normalization; owner claims are roster-normalized and
        // soft-cleared later in action post-processing.
        if (!DATE_LIKE.matcher(dueDate).find() && !corpus.contains(needle)) {
            throw new PipelineException(
                    FailureCategory.HALLUCINATED_DATE,
                    PipelineStage.DETERMINISTIC_VALIDATE,
                    "Due date not present in transcript: " + dueDate
            );
        }
    }

    private void assertNotSuggestionAsDecision(String text) {
        if (SUGGESTION.matcher(text).find()) {
            throw new PipelineException(
                    FailureCategory.SCHEMA_VIOLATION,
                    PipelineStage.DETERMINISTIC_VALIDATE,
                    "Suggestion must not be recorded as a decision: " + text
            );
        }
    }

    private void assertNoDuplicateDecisions(List<DecisionCandidate> decisions) {
        Set<String> seen = new HashSet<>();
        for (DecisionCandidate decision : decisions) {
            String key = decision.text().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
            if (!seen.add(key)) {
                throw new PipelineException(
                        FailureCategory.DUPLICATE_DECISION,
                        PipelineStage.DETERMINISTIC_VALIDATE,
                        "Duplicate decision: " + decision.text()
                );
            }
        }
    }
}
