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

    private static final int MAX_SUMMARY_DECISIONS = 2;
    private static final int MAX_SUMMARY_ACTIONS = 3;
    private static final int MAX_SUMMARY_RISKS = 1;

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
     * Decision/action-first executive summary — never an agenda dump.
     * Topics stay on {@link ExtractionBundle#topics()} / draft topics only.
     */
    private static String buildSummary(ExtractionBundle bundle, String language) {
        boolean en = "en".equals(language);
        StringBuilder sb = new StringBuilder();

        List<DecisionCandidate> decisions = bundle.decisions();
        List<ActionItemCandidate> actions = bundle.actionItems();
        List<RiskCandidate> risks = bundle.risks();

        if (!decisions.isEmpty()) {
            sb.append(en ? "Outcomes:" : "Sonuçlar:").append('\n');
            appendNumberedLines(sb, decisions.stream()
                    .limit(MAX_SUMMARY_DECISIONS)
                    .map(d -> d.text().strip())
                    .toList());
        }
        if (!actions.isEmpty()) {
            if (!sb.isEmpty()) {
                sb.append('\n');
            }
            sb.append(en ? "Next steps:" : "Sonraki adımlar:").append('\n');
            appendNumberedLines(sb, actions.stream()
                    .limit(MAX_SUMMARY_ACTIONS)
                    .map(item -> {
                        String line = item.text().strip();
                        if (item.owner() != null && !item.owner().isBlank()) {
                            line = line + " (" + item.owner().strip() + ")";
                        }
                        return line;
                    })
                    .toList());
            if (actions.size() > MAX_SUMMARY_ACTIONS) {
                int more = actions.size() - MAX_SUMMARY_ACTIONS;
                sb.append(en
                        ? ("… +" + more + " more actions")
                        : ("… +" + more + " aksiyon daha")).append('\n');
            }
        }
        if (!risks.isEmpty()) {
            if (!sb.isEmpty()) {
                sb.append('\n');
            }
            sb.append(en ? "Watchouts:" : "Dikkat:").append('\n');
            appendNumberedLines(sb, risks.stream()
                    .limit(MAX_SUMMARY_RISKS)
                    .map(r -> r.text().strip())
                    .toList());
        }
        if (sb.isEmpty() && !bundle.openQuestions().isEmpty()) {
            sb.append(en ? "Open questions remain." : "Açık sorular devam ediyor.");
            sb.append('\n');
        }
        if (sb.isEmpty() && !bundle.importantFacts().isEmpty()) {
            sb.append(en ? "Key facts captured." : "Önemli bulgular kaydedildi.");
            sb.append('\n');
        }
        if (sb.isEmpty()) {
            return OutputLanguagePolicy.unreliableSummary(language);
        }
        return sb.toString().trim();
    }

    /** Display ordinals only — entities already selected by stream/limit, not by magic index. */
    private static void appendNumberedLines(StringBuilder sb, List<String> lines) {
        int n = 1;
        for (String line : lines) {
            sb.append(n++).append(". ").append(line).append('\n');
        }
    }

    /** Kept for callers/tests that still filter agenda-quality topics for the topics field. */
    static boolean isUsableTopic(TopicCandidate topic) {
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
