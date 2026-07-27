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
        String summary = buildSummary(bundle, lang);
        return new FinalNoteDraft(
                summary,
                bundle.decisions(),
                bundle.actionItems(),
                bundle.risks(),
                bundle.openQuestions(),
                bundle.commitments(),
                bundle.topics(),
                bundle.issues(),
                bundle.proposals(),
                bundle.importantFacts(),
                flags,
                bundle.evidenceSegmentIds(),
                bundle.confidence(),
                manual
        );
    }

    private static String buildSummary(ExtractionBundle bundle, String language) {
        boolean en = "en".equals(language);
        StringBuilder sb = new StringBuilder();
        if (!bundle.topics().isEmpty()) {
            sb.append(en ? "Agenda: " : "Gündem: ");
            List<String> topicLines = new ArrayList<>();
            for (TopicCandidate topic : bundle.topics()) {
                if (topic.summary() != null && !topic.summary().isBlank()) {
                    topicLines.add(topic.text() + " — " + topic.summary());
                } else {
                    topicLines.add(topic.text());
                }
            }
            sb.append(String.join("; ", topicLines)).append('.');
        }
        if (!bundle.decisions().isEmpty()) {
            if (!sb.isEmpty()) {
                sb.append(' ');
            }
            sb.append(bundle.decisions().size())
                    .append(en ? " decision(s) recorded." : " karar kaydedildi.");
        }
        if (!bundle.actionItems().isEmpty()) {
            if (!sb.isEmpty()) {
                sb.append(' ');
            }
            sb.append(bundle.actionItems().size())
                    .append(en ? " action item(s)." : " aksiyon maddesi.");
        }
        if (!bundle.risks().isEmpty()) {
            if (!sb.isEmpty()) {
                sb.append(' ');
            }
            sb.append(bundle.risks().size())
                    .append(en ? " risk(s)." : " risk.");
        }
        if (sb.isEmpty()) {
            return OutputLanguagePolicy.emptySummary(language);
        }
        return sb.toString();
    }
}
