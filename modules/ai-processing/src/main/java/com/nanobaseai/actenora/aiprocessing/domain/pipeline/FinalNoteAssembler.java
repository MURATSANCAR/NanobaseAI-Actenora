package com.nanobaseai.actenora.aiprocessing.domain.pipeline;

import com.nanobaseai.actenora.aiprocessing.domain.prompt.ExtractionPromptRules;
import com.nanobaseai.actenora.aiprocessing.domain.prompt.OutputLanguagePolicy;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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

    /**
     * Decision/action-first executive summary — never a topic dump with counters.
     */
    private static String buildSummary(ExtractionBundle bundle, String language) {
        boolean en = "en".equals(language);
        StringBuilder sb = new StringBuilder();

        List<TopicCandidate> usableTopics = bundle.topics().stream()
                .filter(FinalNoteAssembler::isUsableTopic)
                .toList();
        if (!usableTopics.isEmpty()) {
            sb.append(en ? "Agenda:" : "Gündem:").append('\n');
            int i = 1;
            for (TopicCandidate topic : usableTopics) {
                sb.append(i++).append(". ").append(topic.text().strip()).append('\n');
            }
        }
        if (!bundle.decisions().isEmpty()) {
            if (!sb.isEmpty()) {
                sb.append('\n');
            }
            sb.append(en ? "Decisions" : "Kararlar").append('\n');
            int i = 1;
            for (DecisionCandidate decision : bundle.decisions()) {
                sb.append(i++).append(". ").append(decision.text().strip()).append('\n');
            }
        }
        if (!bundle.actionItems().isEmpty()) {
            if (!sb.isEmpty()) {
                sb.append('\n');
            }
            sb.append(en ? "Actions" : "Aksiyonlar").append('\n');
            int i = 1;
            for (ActionItemCandidate item : bundle.actionItems()) {
                sb.append(i++).append(". ").append(item.text().strip()).append('\n');
            }
        }
        if (!bundle.commitments().isEmpty()) {
            if (!sb.isEmpty()) {
                sb.append('\n');
            }
            sb.append(en ? "Commitments" : "Taahhütler").append('\n');
            int i = 1;
            for (CommitmentCandidate item : bundle.commitments()) {
                sb.append(i++).append(". ").append(item.text().strip()).append('\n');
            }
        }
        if (!bundle.risks().isEmpty()) {
            if (!sb.isEmpty()) {
                sb.append('\n');
            }
            sb.append(en ? "Risks" : "Riskler").append('\n');
            int i = 1;
            for (RiskCandidate risk : bundle.risks()) {
                sb.append(i++).append(". ").append(risk.text().strip()).append('\n');
            }
        }
        if (sb.isEmpty() && !bundle.importantFacts().isEmpty()) {
            sb.append(en ? "Facts" : "Önemli noktalar").append('\n');
            int i = 1;
            for (ImportantFactCandidate fact : bundle.importantFacts()) {
                sb.append(i++).append(". ").append(fact.text().strip()).append('\n');
            }
        }
        if (sb.isEmpty() && !bundle.openQuestions().isEmpty()) {
            sb.append(en ? "Open questions" : "Açık konular").append('\n');
            int i = 1;
            for (OpenQuestionCandidate question : bundle.openQuestions()) {
                sb.append(i++).append(". ").append(question.text().strip()).append('\n');
            }
        }
        if (sb.isEmpty()) {
            return OutputLanguagePolicy.unreliableSummary(language);
        }
        return sb.toString().trim();
    }

    private static boolean isUsableTopic(TopicCandidate topic) {
        if (topic == null || topic.text() == null || topic.text().isBlank()) {
            return false;
        }
        if (topic.evidenceSegmentIds() == null || topic.evidenceSegmentIds().isEmpty()) {
            return false;
        }
        String t = topic.text().toLowerCase(Locale.ROOT);
        return !(t.contains("bağlam")
                || t.contains("nokta")
                || t.contains("detaylandır")
                || t.contains("kapanış")
                || t.contains("açmamız")
                || t.length() < 12);
    }
}
