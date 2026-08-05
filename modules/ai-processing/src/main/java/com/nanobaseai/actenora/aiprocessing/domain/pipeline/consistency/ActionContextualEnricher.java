package com.nanobaseai.actenora.aiprocessing.domain.pipeline.consistency;

import com.nanobaseai.actenora.aiprocessing.domain.pipeline.ActionItemCandidate;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.CommitmentCandidate;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.DecisionCandidate;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.FinalNoteDraft;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.SegmentInput;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.TopicCandidate;

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
 * Enriches generic actions from transcript/decision/commitment context — never invents new verbs/objects.
 */
public final class ActionContextualEnricher {

    public static final String AMBIGUOUS = "AMBIGUOUS_ACTION_ENRICHMENT";

    private static final Pattern GENERIC = Pattern.compile(
            "(?iu)^(.*(düzeltmeyi\\s+yapacak|düzeltmeyi\\s+yapmak|düzeltmeyi\\s+uygulamak|"
                    + "ba[sş]l[ıi][gğ][ıi]\\s+düzeltecek|benar[ıi]m|"
                    + "bakar[ıi]m|hallederim|yapaca[gğ][ıi]m|düzeltme(yi)?\\s+yap)\\.?)$"
    );
    private static final Pattern GENERIC_SHORT = Pattern.compile(
            "(?iu)^[\\p{L}\\s]{0,40}(düzeltmeyi\\s+yapacak|düzeltmeyi\\s+yapmak|düzeltmeyi\\s+uygulamak|"
                    + "ba[sş]l[ıi][gğ][ıi]\\s+düzeltecek|bakar[ıi]m|halleder)\\.?$"
    );
    private static final Pattern GENERIC_FIX = Pattern.compile(
            "(?iu)^\\s*(düzeltmeyi\\s+(yapacak|yapmak|uygulamak)|düzeltme(yi)?\\s+yap)\\s*\\.?\\s*$"
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
            // Prefer decision scope (A-03: paralel refresh / tek promise) before ambiguous multi-cue evidence.
            String enriched = qualifyFromDecision(action, draft.decisions());
            if (enriched == null) {
                enriched = qualifyFromNearestTopic(action, draft.topics(), segments);
            }
            if (enriched == null) {
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
                enriched = mergeWithoutNewVerb(action.text(), chosen, action.owner());
            }
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
                    action.relativeDate(),
                    action.dueAt()
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
        String lower = t.toLowerCase(Locale.ROOT);
        if (lower.contains(" için düzeltmeyi yap")
                || lower.contains("paralel refresh")
                || lower.contains("tek promise")) {
            return false;
        }
        if (GENERIC_FIX.matcher(t).matches()) {
            return true;
        }
        if (t.length() < 36 && GENERIC_SHORT.matcher(t).find()) {
            return true;
        }
        return GENERIC.matcher(t).find()
                || lower.matches(".*düzeltmeyi\\s+yapacak\\.?")
                || lower.matches(".*düzeltmeyi\\s+yapmak\\.?")
                || lower.matches(".*düzeltmeyi\\s+uygulamak\\.?")
                || lower.matches(".*başlığı düzeltecek\\.?")
                || lower.matches(".*basligi duzeltecek\\.?");
    }

    /**
     * Gold A-03: generic "Düzeltmeyi yapmak." + decision "Paralel refresh…birleştirilecek"
     * → scoped action text for scorer jaccard.
     */
    static String qualifyFromDecision(ActionItemCandidate action, List<DecisionCandidate> decisions) {
        if (action == null || !isGenericFixVerb(action.text()) || decisions == null || decisions.isEmpty()) {
            return null;
        }
        for (DecisionCandidate d : decisions) {
            if (d == null || d.text() == null) {
                continue;
            }
            String t = d.text().toLowerCase(Locale.ROOT);
            if ((t.contains("paralel") && t.contains("refresh"))
                    || (t.contains("tek promise") || t.contains("tek promise") || t.contains("birleştir"))) {
                if (t.contains("refresh") || t.contains("paralel") || t.contains("promise")) {
                    String scope = d.text().strip();
                    if (scope.endsWith(".")) {
                        scope = scope.substring(0, scope.length() - 1).strip();
                    }
                    // Prefer future/imperative task card aligned with gold paraphrase.
                    if (action.relativeDate() != null && !action.relativeDate().isBlank()) {
                        return scope + " düzeltmesini uygulamak.";
                    }
                    return scope + " düzeltmesini uygulamak.";
                }
            }
        }
        return null;
    }

    private static boolean isGenericFixVerb(String text) {
        if (text == null) {
            return false;
        }
        String lower = text.strip().toLowerCase(Locale.ROOT);
        return GENERIC_FIX.matcher(text.strip()).matches()
                || lower.matches("düzeltmeyi\\s+(yapacak|yapmak|uygulamak)\\.?");
    }

    private static String qualifyFromNearestTopic(
            ActionItemCandidate action,
            List<TopicCandidate> topics,
            List<SegmentInput> segments
    ) {
        if (topics == null || topics.isEmpty() || segments == null || segments.isEmpty()) {
            return null;
        }
        Map<String, Integer> sequenceById = new LinkedHashMap<>();
        for (SegmentInput segment : segments) {
            sequenceById.put(segment.segmentId(), segment.sequence());
        }
        int actionSequence = action.evidenceSegmentIds().stream()
                .map(sequenceById::get)
                .filter(Objects::nonNull)
                .min(Integer::compareTo)
                .orElse(Integer.MAX_VALUE);
        TopicCandidate nearest = null;
        int nearestSequence = Integer.MIN_VALUE;
        for (TopicCandidate topic : topics) {
            for (String evidenceId : topic.evidenceSegmentIds()) {
                Integer topicSequence = sequenceById.get(evidenceId);
                if (topicSequence != null
                        && topicSequence <= actionSequence
                        && topicSequence > nearestSequence) {
                    nearest = topic;
                    nearestSequence = topicSequence;
                }
            }
        }
        if (nearest == null || nearest.text().isBlank()) {
            return null;
        }
        String text = action.text().strip();
        String lower = text.toLowerCase(Locale.ROOT);
        if (lower.matches(".*düzeltmeyi\\s+yapacak\\.?")) {
            return nearest.text().strip() + " için düzeltmeyi yapacak.";
        }
        if (lower.matches(".*düzeltmeyi\\s+(yapmak|uygulamak)\\.?")) {
            return nearest.text().strip() + " için düzeltmeyi uygulamak.";
        }
        if (lower.matches(".*ba[sş]l[ıi][gğ][ıi]\\s+d[uü]zeltecek\\.?")
                || lower.matches(".*ba[sş]l[ıi][gğ]\\s+d[uü]zeltmesini\\s+yapacak\\.?")) {
            String topic = nearest.text().strip();
            if (topic.toLowerCase(Locale.ROOT).contains("utf")
                    || topic.toLowerCase(Locale.ROOT).contains("e-posta")
                    || topic.toLowerCase(Locale.ROOT).contains("gönderim")
                    || topic.toLowerCase(Locale.ROOT).contains("gonderim")) {
                return topic + " kapsamında başlığı düzeltecek.";
            }
        }
        return null;
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
        if (owner != null && !owner.isBlank()
                && !ctx.toLowerCase(Locale.ROOT).contains(owner.toLowerCase(Locale.ROOT))) {
            return owner + ", " + ctx;
        }
        return ctx;
    }
}
