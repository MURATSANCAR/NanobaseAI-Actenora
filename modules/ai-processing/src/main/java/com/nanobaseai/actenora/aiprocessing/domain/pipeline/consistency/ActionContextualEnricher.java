package com.nanobaseai.actenora.aiprocessing.domain.pipeline.consistency;

import com.nanobaseai.actenora.aiprocessing.domain.pipeline.ActionItemCandidate;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.CommitmentCandidate;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.FinalNoteDraft;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.SegmentInput;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Enriches generic actions from transcript/commitment context — never invents new verbs/objects.
 */
public final class ActionContextualEnricher {

    public static final String AMBIGUOUS = "AMBIGUOUS_ACTION_ENRICHMENT";

    private static final Pattern GENERIC = Pattern.compile(
            "(?iu)^(.*(düzeltmeyi\\s+yapacak|ba[sş]l[ıi][gğ][ıi]\\s+düzeltecek|benar[ıi]m|"
                    + "bakar[ıi]m|hallederim|yapaca[gğ][ıi]m|düzeltme(yi)?\\s+yap)\\.?)$"
    );
    private static final Pattern GENERIC_SHORT = Pattern.compile(
            "(?iu)^[\\p{L}\\s]{0,40}(düzeltmeyi\\s+yapacak|ba[sş]l[ıi][gğ][ıi]\\s+düzeltecek|"
                    + "bakar[ıi]m|halleder)\\.?$"
    );

    public FinalNoteDraft enrich(FinalNoteDraft draft, List<SegmentInput> segments) {
        Objects.requireNonNull(draft, "draft");
        Map<String, String> byId = indexSegments(segments == null ? List.of() : segments);
        List<ActionItemCandidate> out = new ArrayList<>();
        Set<String> flags = new LinkedHashSet<>(draft.qualityFlags());
        boolean ambiguous = false;

        for (ActionItemCandidate action : draft.actionItems()) {
            if (!isGenericAction(action.text())) {
                out.add(action);
                continue;
            }
            List<String> contexts = new ArrayList<>();
            for (String evid : action.evidenceSegmentIds()) {
                String content = byId.get(evid);
                if (content != null && !content.isBlank()) {
                    contexts.add(content.strip());
                }
            }
            if (action.owner() != null && !action.owner().isBlank()) {
                for (CommitmentCandidate c : draft.commitments()) {
                    if (c.owner() != null && c.owner().equalsIgnoreCase(action.owner())) {
                        contexts.add(c.text());
                    }
                }
            }
            String chosen = pickSingleContext(contexts);
            if (chosen == null) {
                ambiguous = true;
                out.add(action);
                continue;
            }
            String enriched = mergeWithoutNewVerb(action.text(), chosen, action.owner());
            if (enriched == null || enriched.equals(action.text())) {
                ambiguous = true;
                out.add(action);
                continue;
            }
            out.add(new ActionItemCandidate(
                    enriched,
                    action.owner(),
                    action.dueDate(),
                    action.evidenceSegmentIds(),
                    action.confidence(),
                    action.ownerType(),
                    action.priority(),
                    action.relativeDate()
            ));
        }
        if (ambiguous) {
            flags.add(AMBIGUOUS);
        }
        return new FinalNoteDraft(
                draft.executiveSummary(),
                draft.decisions(),
                out,
                draft.risks(),
                draft.openQuestions(),
                draft.commitments(),
                draft.topics(),
                draft.issues(),
                draft.proposals(),
                draft.importantFacts(),
                new ArrayList<>(flags),
                draft.evidenceSegmentIds(),
                draft.confidence(),
                draft.requiresManualReview()
        );
    }

    public static boolean isGenericAction(String text) {
        if (text == null || text.isBlank()) {
            return true;
        }
        String t = text.strip();
        if (t.length() < 28 && GENERIC_SHORT.matcher(t).find()) {
            return true;
        }
        return GENERIC.matcher(t).find()
                || t.toLowerCase(Locale.ROOT).matches(".*düzeltmeyi yapacak\\.?")
                || t.toLowerCase(Locale.ROOT).matches(".*başlığı düzeltecek\\.?")
                || t.toLowerCase(Locale.ROOT).matches(".*basligi duzeltecek\\.?");
    }

    private static Map<String, String> indexSegments(List<SegmentInput> segments) {
        Map<String, String> map = new LinkedHashMap<>();
        for (SegmentInput s : segments) {
            if (s != null && s.segmentId() != null) {
                map.put(s.segmentId(), s.content());
            }
        }
        return map;
    }

    private static String pickSingleContext(List<String> contexts) {
        if (contexts == null || contexts.isEmpty()) {
            return null;
        }
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        for (String c : contexts) {
            String core = ItemTextViews.comparisonCore(c);
            if (!core.isBlank()) {
                unique.add(core);
            }
        }
        if (unique.size() != 1) {
            return null;
        }
        return contexts.getFirst();
    }

    /**
     * Prefer the longer context sentence when action is a generic wrapper; refuse if context
     * is itself too vague ("bakarım").
     */
    static String mergeWithoutNewVerb(String actionText, String context, String owner) {
        String ctx = context.strip();
        String lower = ctx.toLowerCase(Locale.ROOT);
        if (lower.contains("bakarım") || lower.contains("bakarim") || lower.matches(".*\\bben bakar.*")) {
            return null;
        }
        if (ctx.length() < actionText.length()) {
            return null;
        }
        // Keep owner prefix if present in action and missing in context.
        if (owner != null && !owner.isBlank()
                && !ctx.toLowerCase(Locale.ROOT).contains(owner.toLowerCase(Locale.ROOT))) {
            return owner + ", " + ctx;
        }
        return ctx;
    }
}
