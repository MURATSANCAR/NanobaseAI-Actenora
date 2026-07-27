package com.nanobaseai.actenora.aiprocessing.domain.pipeline;

import com.nanobaseai.actenora.aiprocessing.domain.prompt.ExtractionPromptRules;
import com.nanobaseai.actenora.aiprocessing.domain.prompt.OutputLanguagePolicy;

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
        return assemble(bundle, "tr");
    }

    public FinalNoteDraft assemble(ExtractionBundle bundle, String language) {
        Objects.requireNonNull(bundle, "bundle");
        String lang = ExtractionPromptRules.normalizeLanguage(language);
        List<String> flags = new ArrayList<>(bundle.qualityFlags());
        boolean manual = validator.requiresManualReview(bundle);
        if (manual && flags.stream().noneMatch(f -> f.equalsIgnoreCase("LOW_CONFIDENCE"))) {
            flags.add("LOW_CONFIDENCE");
        }
        ExtractionBundle cleaned = MeetingNoisePatterns.stripStatusQuoDecisions(bundle);
        String summary = buildSummary(cleaned, lang);
        return new FinalNoteDraft(
                summary,
                cleaned.decisions(),
                cleaned.actionItems(),
                cleaned.risks(),
                cleaned.openQuestions(),
                cleaned.commitments(),
                cleaned.topics(),
                cleaned.issues(),
                cleaned.proposals(),
                cleaned.importantFacts(),
                flags,
                cleaned.evidenceSegmentIds(),
                cleaned.confidence(),
                manual
        );
    }

    /**
     * Builds a scannable executive summary: numbered agenda lines, then count lines —
     * never a single semicolon-joined paragraph.
     */
    private static String buildSummary(ExtractionBundle bundle, String language) {
        boolean en = "en".equals(language);
        StringBuilder sb = new StringBuilder();
        if (!bundle.topics().isEmpty()) {
            sb.append(en ? "Agenda:" : "Gündem:").append('\n');
            int i = 1;
            for (TopicCandidate topic : bundle.topics()) {
                String line;
                if (topic.summary() != null && !topic.summary().isBlank()) {
                    line = topic.text() + " — " + topic.summary();
                } else {
                    line = topic.text();
                }
                sb.append(i++).append(". ").append(line).append('\n');
            }
        }
        List<String> stats = new ArrayList<>(3);
        if (!bundle.decisions().isEmpty()) {
            stats.add(bundle.decisions().size() + (en ? " decision(s) recorded." : " karar kaydedildi."));
        }
        if (!bundle.actionItems().isEmpty()) {
            stats.add(bundle.actionItems().size() + (en ? " action item(s)." : " aksiyon maddesi."));
        }
        if (!bundle.risks().isEmpty()) {
            stats.add(bundle.risks().size() + (en ? " risk(s)." : " risk."));
        }
        if (!stats.isEmpty()) {
            if (!sb.isEmpty()) {
                sb.append('\n');
            }
            for (String stat : stats) {
                sb.append(stat).append('\n');
            }
        }
        if (sb.isEmpty()) {
            return OutputLanguagePolicy.emptySummary(language);
        }
        return sb.toString().trim();
    }
}
