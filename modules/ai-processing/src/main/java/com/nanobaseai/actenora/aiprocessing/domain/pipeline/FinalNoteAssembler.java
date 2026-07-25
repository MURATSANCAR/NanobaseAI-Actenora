package com.nanobaseai.actenora.aiprocessing.domain.pipeline;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Assembles a final note draft from a validated merged extraction bundle.
 */
public final class FinalNoteAssembler {

    private final DeterministicExtractionValidator validator;

    public FinalNoteAssembler(DeterministicExtractionValidator validator) {
        this.validator = Objects.requireNonNull(validator, "validator");
    }

    public FinalNoteAssembler() {
        this(new DeterministicExtractionValidator());
    }

    public FinalNoteDraft assemble(ExtractionBundle bundle) {
        Objects.requireNonNull(bundle, "bundle");
        List<String> flags = new ArrayList<>(bundle.qualityFlags());
        boolean manual = validator.requiresManualReview(bundle);
        if (manual && flags.stream().noneMatch(f -> f.equalsIgnoreCase("LOW_CONFIDENCE"))) {
            flags.add("LOW_CONFIDENCE");
        }
        String summary = buildSummary(bundle);
        return new FinalNoteDraft(
                summary,
                bundle.decisions(),
                bundle.actionItems(),
                bundle.risks(),
                bundle.openQuestions(),
                bundle.commitments(),
                bundle.topics(),
                flags,
                bundle.evidenceSegmentIds(),
                bundle.confidence(),
                manual
        );
    }

    private static String buildSummary(ExtractionBundle bundle) {
        StringBuilder sb = new StringBuilder();
        if (!bundle.topics().isEmpty()) {
            sb.append("Topics: ");
            sb.append(bundle.topics().getFirst().text());
            if (bundle.topics().size() > 1) {
                sb.append(" (+").append(bundle.topics().size() - 1).append(" more)");
            }
            sb.append('.');
        }
        if (!bundle.decisions().isEmpty()) {
            if (!sb.isEmpty()) {
                sb.append(' ');
            }
            sb.append(bundle.decisions().size()).append(" decision(s) recorded.");
        }
        if (!bundle.actionItems().isEmpty()) {
            if (!sb.isEmpty()) {
                sb.append(' ');
            }
            sb.append(bundle.actionItems().size()).append(" action item(s).");
        }
        if (sb.isEmpty()) {
            return "Meeting extraction completed with no primary topics.";
        }
        return sb.toString();
    }
}
