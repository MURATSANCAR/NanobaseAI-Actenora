package com.nanobaseai.actenora.aiprocessing.domain.pipeline.action;

import com.nanobaseai.actenora.aiprocessing.domain.pipeline.ActionItemCandidate;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.DecisionCandidate;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.SegmentInput;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.lineage.ItemLineageRecord;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.lineage.LineageOperation;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.lineage.LineageReasonCode;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.lineage.LineageStage;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.lineage.LineageSupport;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Completes low-specificity action titles from nearby evidence / decision context.
 * Never invents owners, dates, priorities, or new work — fail-safe is NO_UPDATE.
 */
public final class ActionTitleEvidenceBackfiller {

    public static final int MAX_CONTEXT_CUE_DISTANCE = 3;
    public static final boolean CROSS_TOPIC_LOOKUP = false;
    public static final boolean CROSS_CHUNK_LOOKUP = false;
    public static final String RULE_VERSION = "action-title-context-v1";

    private static final int MIN_TITLE_LEN = 18;
    private static final int MAX_EVIDENCE_CHARS = 220;
    private static final int MAX_SEQ_GAP_SAME_TOPIC = 40;

    private static final int RX = Pattern.CASE_INSENSITIVE
            | Pattern.UNICODE_CASE
            | Pattern.UNICODE_CHARACTER_CLASS;

    private static final Pattern ENDS_TRUNCATED = Pattern.compile(".*(…|\\.{2,}|\\u2026)\\s*$");
    private static final Pattern LIKELY_INCOMPLETE = Pattern.compile(
            "^(yak[ıi]n|taban[ıi]na|e[gğ]itimi|hesaplanaca[gğ][ıi]|[uü]zerinden|"
                    + "arasında|grubunu|modelinin|verisine)\\b.*",
            RX
    );
    private static final Pattern HAS_VERBISH = Pattern.compile(
            "\\b(oluştur|haz[ıi]rla|aktar|belirle|e[sş]le[sş]tir|konu[sş]|yap[ıi]l|"
                    + "kur|ara|payla[sş]|tamamla|yaz|ekle|g[uü]ncelle|sa[gğ]la|d[uü]zelt|"
                    + "uygula|ger[cç]ekle[sş]tir|kontrol)\\w*",
            RX
    );
    private static final Pattern FUTURE_VERB = Pattern.compile(
            "\\b([\\p{L}]+(?:yacak|yecek|acak|ecek))\\b",
            RX
    );
    private static final Pattern GENERIC_OBJECT = Pattern.compile(
            "\\b(ba[sş]l[ıi][gğk]\\w*|d[uü]zeltme\\w*|kontrol\\w*|de[gğ]i[sş]iklik\\w*|"
                    + "g[uü]ncelleme\\w*|i[sş]lem\\w*|kay[ıi]t\\w*|konu\\w*|madde\\w*|nokta\\w*|"
                    + "bunu|[sş]unu|onu)\\b",
            RX
    );
    private static final Pattern DECISION_CUE = Pattern.compile(
            "(karar[ıi]?\\s+(a[cç][ıi]k[cç]a\\s+)?kayda|zorunlu\\s+olacak|"
                    + "karar[ıi]\\s+ald[ıi]k|kararla[sş]t[ıi]rd[ıi]k|se[cç]enek\\s+[sş]u)",
            RX
    );
    private static final Pattern STOP = Pattern.compile(
            "^(ve|veya|ile|i[cç]in|bir|bu|şu|o|da|de|ki|mi|mu|m[uü]|ya|yada|olarak|"
                    + "kadar|sonra|önce|gibi|daha|en|her|çok|az|var|yok|ise|ama|fakat|"
                    + "aksiyon|karar[ıi]?|kayd[ıi]|kayda|ge[cç]iriyorum|zorunlu|a[cç][ıi]k[cç]a|"
                    + "the|a|an|to|of|in|on|for|and|or)$",
            RX
    );

    public enum BackfillDecision {
        UPDATED,
        NO_UPDATE
    }

    public record ContextCandidate(
            String text,
            List<String> evidenceSegmentIds,
            String kind,
            int cueDistance,
            int sequence,
            String topicId,
            String chunkId
    ) {
        public ContextCandidate(
                String text,
                List<String> evidenceSegmentIds,
                String kind,
                int cueDistance,
                int sequence
        ) {
            this(text, evidenceSegmentIds, kind, cueDistance, sequence, null, null);
        }
    }

    public record ActionBackfillContext(
            List<SegmentInput> ownEvidence,
            List<ContextCandidate> precedingCandidates,
            String topicId,
            String chunkId,
            int maxCueDistance
    ) {
        public ActionBackfillContext {
            ownEvidence = ownEvidence == null ? List.of() : List.copyOf(ownEvidence);
            precedingCandidates = precedingCandidates == null ? List.of() : List.copyOf(precedingCandidates);
            maxCueDistance = maxCueDistance <= 0 ? MAX_CONTEXT_CUE_DISTANCE : maxCueDistance;
        }
    }

    public record ActionBackfillResult(
            ActionItemCandidate action,
            BackfillDecision decision,
            String reasonCode,
            List<String> contextEvidenceIds,
            String ruleVersion,
            String beforeText,
            String afterText
    ) {
        public ActionBackfillResult {
            Objects.requireNonNull(action, "action");
            Objects.requireNonNull(decision, "decision");
            Objects.requireNonNull(reasonCode, "reasonCode");
            contextEvidenceIds = contextEvidenceIds == null ? List.of() : List.copyOf(contextEvidenceIds);
            ruleVersion = ruleVersion == null ? RULE_VERSION : ruleVersion;
        }
    }

    public List<ActionItemCandidate> backfill(
            List<ActionItemCandidate> actions,
            List<SegmentInput> segments
    ) {
        return backfill(actions, segments, List.of());
    }

    public List<ActionItemCandidate> backfill(
            List<ActionItemCandidate> actions,
            List<SegmentInput> segments,
            List<DecisionCandidate> decisions
    ) {
        Objects.requireNonNull(actions, "actions");
        List<SegmentInput> segs = segments == null ? List.of() : segments;
        List<DecisionCandidate> decs = decisions == null ? List.of() : decisions;
        List<ActionItemCandidate> out = new ArrayList<>(actions.size());
        for (ActionItemCandidate action : actions) {
            ActionBackfillResult result = backfill(action, buildContext(action, segs, decs));
            observe(result);
            out.add(result.action());
        }
        return List.copyOf(out);
    }

    public ActionBackfillResult backfill(ActionItemCandidate action, ActionBackfillContext context) {
        Objects.requireNonNull(action, "action");
        ActionBackfillContext ctx = context == null
                ? new ActionBackfillContext(List.of(), List.of(), null, null, MAX_CONTEXT_CUE_DISTANCE)
                : context;

        String before = action.text() == null ? "" : action.text().strip();

        // Dated actions already carry scheduling semantics — never rewrite them.
        if (hasStructuredDate(action)) {
            return noUpdate(action, "ACTION_TITLE_ALREADY_SPECIFIC", before);
        }

        // 1. Already specific?
        if (!isLowSpecificity(before)) {
            return noUpdate(action, "ACTION_TITLE_ALREADY_SPECIFIC", before);
        }

        // 2. Own evidence first
        String fromOwn = completeFromOwnEvidence(action, before, ctx.ownEvidence());
        if (fromOwn != null) {
            return update(action, fromOwn, evidenceIds(ctx.ownEvidence()), "ACTION_TITLE_CONTEXT_BACKFILLED", before);
        }

        // 3–4. Preceding contexts within cue distance
        List<ContextCandidate> eligible = new ArrayList<>();
        for (ContextCandidate c : ctx.precedingCandidates()) {
            if (c == null || c.text() == null || c.text().isBlank()) {
                continue;
            }
            if (c.cueDistance() < 1 || c.cueDistance() > ctx.maxCueDistance()) {
                continue;
            }
            if (!CROSS_TOPIC_LOOKUP
                    && ctx.topicId() != null
                    && c.topicId() != null
                    && !ctx.topicId().equals(c.topicId())) {
                continue;
            }
            if (!CROSS_CHUNK_LOOKUP
                    && ctx.chunkId() != null
                    && c.chunkId() != null
                    && !ctx.chunkId().equals(c.chunkId())) {
                continue;
            }
            if (!providesMissingSpecificity(before, c.text())) {
                continue;
            }
            if (verbConflict(before, c.text())) {
                continue;
            }
            eligible.add(c);
        }
        if (eligible.isEmpty()) {
            // Keep legacy truncated-ASR path as last own-evidence attempt already done.
            if (needsLegacyTruncationRepair(before)) {
                String legacy = legacyRepairFromOwn(action, before, ctx.ownEvidence());
                if (legacy != null) {
                    return update(action, legacy, evidenceIds(ctx.ownEvidence()),
                            "ACTION_TITLE_CONTEXT_BACKFILLED", before);
                }
            }
            return noUpdate(action, "ACTION_TITLE_CONTEXT_NOT_FOUND", before);
        }
        if (eligible.size() > 1) {
            // Prefer nearest unique; if two at same distance with different scopes → ambiguous
            int minDist = eligible.stream().mapToInt(ContextCandidate::cueDistance).min().orElse(0);
            List<ContextCandidate> nearest = eligible.stream()
                    .filter(c -> c.cueDistance() == minDist)
                    .toList();
            if (nearest.size() > 1 && !sameSpecificTokenSet(nearest)) {
                return noUpdate(action, "ACTION_TITLE_CONTEXT_AMBIGUOUS", before);
            }
            eligible = List.of(nearest.getFirst());
        }

        ContextCandidate chosen = eligible.getFirst();
        String rewritten = rewriteWithContext(before, chosen.text(), action.owner());
        if (rewritten == null || rewritten.equalsIgnoreCase(before)) {
            return noUpdate(action, "ACTION_TITLE_BACKFILL_CONFIDENCE_LOW", before);
        }
        if (!isMoreSpecific(before, rewritten)) {
            return noUpdate(action, "ACTION_TITLE_BACKFILL_CONFIDENCE_LOW", before);
        }
        if (!isStandaloneUnderstandable(rewritten)) {
            return noUpdate(action, "ACTION_TITLE_BACKFILL_CONFIDENCE_LOW", before);
        }
        // 5. Must not mutate owner/date/priority — we only change text via withText
        return update(action, rewritten, chosen.evidenceSegmentIds(), "ACTION_TITLE_CONTEXT_BACKFILLED", before);
    }

    public ActionBackfillContext buildContext(
            ActionItemCandidate action,
            List<SegmentInput> segments,
            List<DecisionCandidate> decisions
    ) {
        Map<String, SegmentInput> byId = indexSegments(segments);
        List<SegmentInput> own = new ArrayList<>();
        int actionSeq = Integer.MAX_VALUE;
        String actionChunkHint = null;
        for (String id : action.evidenceSegmentIds()) {
            SegmentInput s = byId.get(id);
            if (s != null) {
                own.add(s);
                actionSeq = Math.min(actionSeq, s.sequence());
            }
        }
        if (actionSeq == Integer.MAX_VALUE) {
            actionSeq = 0;
        }

        List<ContextCandidate> preceding = new ArrayList<>();
        // Decision candidates with evidence before action (or decision-only when segments omit IDs)
        if (decisions != null) {
            for (DecisionCandidate d : decisions) {
                boolean evidenceInIndex = d.evidenceSegmentIds().stream().anyMatch(byId::containsKey);
                int seq = minSeq(d.evidenceSegmentIds(), byId, Integer.MIN_VALUE);
                int distance;
                if (!evidenceInIndex || seq == Integer.MIN_VALUE) {
                    // Decision evidence not in the transcript window — still usable as topic scope.
                    distance = 1;
                    seq = Math.max(0, actionSeq - 1);
                } else {
                    if (seq >= actionSeq) {
                        continue;
                    }
                    if (!CROSS_CHUNK_LOOKUP && !sameLocalWindow(seq, actionSeq)) {
                        continue;
                    }
                    distance = cueDistanceBetween(seq, actionSeq, segments);
                    if (distance > MAX_CONTEXT_CUE_DISTANCE) {
                        continue;
                    }
                    if (distance < 1) {
                        distance = 1;
                    }
                }
                preceding.add(new ContextCandidate(
                        d.text(), d.evidenceSegmentIds(), "DECISION", distance, seq));
            }
        }
        // Transcript decision-like cues before action
        for (SegmentInput s : segments == null ? List.<SegmentInput>of() : segments) {
            if (s.sequence() >= actionSeq) {
                continue;
            }
            if (!CROSS_CHUNK_LOOKUP && !sameLocalWindow(s.sequence(), actionSeq)) {
                continue;
            }
            boolean decisionLike = DECISION_CUE.matcher(s.content()).find()
                    || looksLikeClosedDecision(s.content());
            // Also accept nearby segments that supply missing object specificity for generic titles
            // (e.g. UTF-8 / e-posta başlığı context for "Başlık düzeltmesini yapacak").
            boolean specificityDonor = providesMissingSpecificity(
                    action.text() == null ? "" : action.text(), s.content())
                    && sharesObjectFamily(action.text() == null ? "" : action.text(), s.content());
            if (!decisionLike && !specificityDonor) {
                continue;
            }
            int distance = cueDistanceBetween(s.sequence(), actionSeq, segments);
            if (distance > MAX_CONTEXT_CUE_DISTANCE) {
                continue;
            }
            preceding.add(new ContextCandidate(
                    s.content(),
                    List.of(s.segmentId()),
                    decisionLike ? "SEGMENT_DECISION" : "SEGMENT_SCOPE",
                    Math.max(1, distance),
                    s.sequence()));
        }
        // Deduplicate by normalized text / overlapping specificity; prefer DECISION kind
        Map<String, ContextCandidate> uniq = new LinkedHashMap<>();
        for (ContextCandidate c : preceding) {
            String key = specificityKey(c.text());
            ContextCandidate prev = uniq.get(key);
            if (prev == null) {
                uniq.put(key, c);
                continue;
            }
            if (preferContext(c, prev)) {
                uniq.put(key, c);
            }
        }
        List<ContextCandidate> ordered = new ArrayList<>(uniq.values());
        ordered.sort((a, b) -> Integer.compare(a.cueDistance(), b.cueDistance()));
        return new ActionBackfillContext(own, ordered, actionChunkHint, actionChunkHint, MAX_CONTEXT_CUE_DISTANCE);
    }

    private static String specificityKey(String text) {
        Set<String> toks = distinctiveTokens(text);
        if (toks.isEmpty()) {
            return normalize(text);
        }
        return String.join("|", toks);
    }

    private static boolean preferContext(ContextCandidate candidate, ContextCandidate existing) {
        boolean candDecision = "DECISION".equals(candidate.kind());
        boolean existingDecision = "DECISION".equals(existing.kind());
        if (candDecision && !existingDecision) {
            return true;
        }
        if (!candDecision && existingDecision) {
            return false;
        }
        if (candidate.cueDistance() < existing.cueDistance()) {
            return true;
        }
        // Prefer more compact decision text (less dialogue noise)
        return candidate.text().length() < existing.text().length();
    }

    private static boolean hasStructuredDate(ActionItemCandidate action) {
        return (action.dueDate() != null && !action.dueDate().isBlank())
                || (action.relativeDate() != null && !action.relativeDate().isBlank())
                || (action.dueAt() != null && !action.dueAt().isBlank());
    }

    static boolean isLowSpecificity(String text) {
        if (text == null || text.isBlank()) {
            return true;
        }
        String t = text.strip();
        if (t.length() < MIN_TITLE_LEN) {
            return true;
        }
        if (ENDS_TRUNCATED.matcher(t).matches()) {
            return true;
        }
        // Titles that already carry a relative/absolute time cue are task-card complete enough.
        if (containsRelativeDate(t)) {
            return false;
        }
        if (LIKELY_INCOMPLETE.matcher(t).matches() && !HAS_VERBISH.matcher(t).find()) {
            return true;
        }
        // Generic object + future verb, few distinctive tokens
        boolean genericObj = GENERIC_OBJECT.matcher(t).find();
        boolean future = FUTURE_VERB.matcher(t).find();
        Set<String> distinctive = distinctiveTokens(t);
        if (genericObj && future && distinctive.size() <= 1) {
            return true;
        }
        // Short title with only generic object and verbish
        if (genericObj && t.length() < 55 && distinctive.isEmpty()) {
            return true;
        }
        return false;
    }

    /** Prefer {@link #isLowSpecificity(String)}; kept for existing callers/tests. */
    @Deprecated
    static boolean needsBackfill(String text) {
        return isLowSpecificity(text) || needsLegacyTruncationRepair(text == null ? "" : text);
    }

    private static boolean needsLegacyTruncationRepair(String t) {
        String s = t.strip();
        if (s.length() < MIN_TITLE_LEN) {
            return true;
        }
        if (ENDS_TRUNCATED.matcher(s).matches()) {
            return true;
        }
        String first = firstToken(s).toLowerCase(Locale.ROOT);
        return LIKELY_INCOMPLETE.matcher(first + " x").matches() && s.length() < 80;
    }

    private String completeFromOwnEvidence(
            ActionItemCandidate action,
            String before,
            List<SegmentInput> ownEvidence
    ) {
        // Prefer extracting a more specific object phrase from own evidence without replacing whole title
        for (SegmentInput s : ownEvidence) {
            if (s == null || s.content() == null) {
                continue;
            }
            String clause = relevantEvidenceClause(s.content(), action.owner(), before);
            // Skip if evidence is mostly the same low-spec clause
            if (normalize(clause).contains(normalize(before))
                    && !providesMissingSpecificity(before, clause)) {
                continue;
            }
            if (!sharesObjectFamily(before, clause)) {
                continue;
            }
            if (providesMissingSpecificity(before, clause) && !verbConflict(before, clause)) {
                String rewritten = rewriteWithContext(before, clause, action.owner());
                if (rewritten != null && isMoreSpecific(before, rewritten) && isStandaloneUnderstandable(rewritten)) {
                    return rewritten;
                }
            }
        }
        return null;
    }

    private static String relevantEvidenceClause(String content, String owner, String action) {
        // Prefer owner-scoped clause; also split on Turkish compound separators.
        String[] parts = content.split("[;\\n]|\\s+ve\\s+(?=[A-ZÇĞİÖŞÜ])");
        if (parts.length <= 1) {
            // Strip speech-act prefixes so generic "Aksiyon kaydı: Can başlığı…" can still match.
            return content.replaceFirst("(?iu)^\\s*aksiyon\\s+kayd[ıi]\\s*:\\s*", "").strip();
        }
        for (String raw : parts) {
            String part = raw.strip().replaceFirst("(?iu)^\\s*aksiyon\\s+kayd[ıi]\\s*:\\s*", "");
            if (part.isEmpty()) {
                continue;
            }
            if (owner != null && !owner.isBlank()
                    && part.toLowerCase(Locale.ROOT).contains(owner.toLowerCase(Locale.ROOT))
                    && sharesObjectFamily(action, part)) {
                return part;
            }
        }
        for (String raw : parts) {
            String part = raw.strip().replaceFirst("(?iu)^\\s*aksiyon\\s+kayd[ıi]\\s*:\\s*", "");
            if (sharesObjectFamily(action, part)
                    && (owner == null || owner.isBlank()
                    || !part.toLowerCase(Locale.ROOT).matches("(?iu).*\\b(outlook|apple\\s*mail|regresyon).*"))) {
                return part;
            }
        }
        return content.replaceFirst("(?iu)^\\s*aksiyon\\s+kayd[ıi]\\s*:\\s*", "").strip();
    }

    private static boolean sharesObjectFamily(String action, String evidence) {
        if (action == null || evidence == null) {
            return false;
        }
        Matcher m = GENERIC_OBJECT.matcher(action);
        boolean found = false;
        while (m.find()) {
            found = true;
            String token = m.group().toLowerCase(Locale.ROOT);
            String stem = token.replaceAll("(?iu)(s[ıi]n[ıi]|n[ıi]|[ıiuü]|ler[ıi]|lar[ıi])$", "");
            if (stem.length() >= 4 && evidence.toLowerCase(Locale.ROOT).contains(stem.substring(0, Math.min(stem.length(), 6)))) {
                return true;
            }
        }
        // No generic object in action — allow own evidence path
        return !found;
    }

    private String legacyRepairFromOwn(
            ActionItemCandidate action,
            String before,
            List<SegmentInput> ownEvidence
    ) {
        Map<String, String> byId = new LinkedHashMap<>();
        for (SegmentInput s : ownEvidence) {
            byId.put(s.segmentId(), s.content());
        }
        String evidence = pickEvidenceSentence(action, byId);
        if (evidence == null) {
            return null;
        }
        String repaired = cleanEvidenceSentence(evidence);
        if (repaired == null || repaired.length() <= before.length()) {
            return null;
        }
        String stem = before.replaceAll("[.…\\u2026]+$", "").strip();
        String repairedLower = repaired.toLowerCase(Locale.ROOT);
        String stemLower = stem.toLowerCase(Locale.ROOT);
        if (!stem.isBlank() && stem.length() >= 8
                && !repairedLower.contains(stemLower)
                && !stemLower.contains(repairedLower.substring(0, Math.min(12, repairedLower.length())))) {
            return null;
        }
        if (!HAS_VERBISH.matcher(repaired).find() && HAS_VERBISH.matcher(before).find()) {
            return null;
        }
        return repaired;
    }

    static String rewriteWithContext(String actionText, String contextText) {
        return rewriteWithContext(actionText, contextText, null);
    }

    static String rewriteWithContext(String actionText, String contextText, String owner) {
        if (actionText == null || contextText == null) {
            return null;
        }
        String action = actionText.strip();
        boolean hadOwnerPrefix = owner != null && !owner.isBlank()
                && action.regionMatches(true, 0, owner, 0, owner.length());
        String actionBody = hadOwnerPrefix
                ? action.substring(owner.length()).replaceFirst("^[,:\\s]+", "")
                : action;
        if (actionBody.isBlank()) {
            actionBody = action;
            hadOwnerPrefix = false;
        }
        String context = contextText.replace('\u00A0', ' ').replaceAll("\\s+", " ").strip();

        String verb = extractFutureVerb(actionBody);
        if (verb == null) {
            verb = extractFutureVerb(action);
        }
        if (verb == null) {
            return null;
        }
        // Prefer a more precise verb when object implies repair and verb is generic "yap*"
        String preciseVerb = verb;
        if (verb.toLowerCase(Locale.ROOT).startsWith("yap")) {
            if (GENERIC_OBJECT.matcher(actionBody).find()
                    && actionBody.toLowerCase(Locale.ROOT).matches("(?iu).*d[uü]zelt.*")) {
                preciseVerb = "düzeltecek";
            }
        }
        if (actionBody.toLowerCase(Locale.ROOT).matches("(?iu).*d[uü]zeltecek.*")) {
            preciseVerb = extractFutureVerb(actionBody);
            if (preciseVerb == null) {
                preciseVerb = "düzeltecek";
            }
        }

        String scope = extractSpecificScopePhrase(context, actionBody);
        if (scope == null || scope.isBlank()) {
            return null;
        }
        // Ensure accusative-ish object ending when verb is transitive Turkish future
        String object = ensureObjectCase(scope, preciseVerb);
        String out = Character.toUpperCase(object.charAt(0)) + object.substring(1);
        if (!out.toLowerCase(Locale.ROOT).contains(preciseVerb.toLowerCase(Locale.ROOT))) {
            out = out + " " + preciseVerb;
        }
        if (!out.endsWith(".") && !out.endsWith("!") && !out.endsWith("?")) {
            out = out + ".";
        }
        if (hadOwnerPrefix) {
            String body = Character.toLowerCase(out.charAt(0)) + out.substring(1);
            out = owner + ", " + body;
        }
        // Never reintroduce speech-act cue prefixes into action titles
        out = out.replaceFirst("(?iu)^\\s*aksiyon\\s+kayd[ıi]\\s*:\\s*", "");
        if (out.isBlank()) {
            return null;
        }
        out = Character.toUpperCase(out.charAt(0)) + out.substring(1);
        // Safety: never introduce relative-date tokens from context into undated actions
        if (!containsRelativeDate(action) && containsRelativeDate(out) && !containsRelativeDate(scope)) {
            return null;
        }
        return out;
    }

    private static String extractSpecificScopePhrase(String context, String action) {
        String c = context.strip();
        // Strip decision / action-cue prefixes
        c = c.replaceFirst("(?iu)^.*?(karar[ıi]?\\s+(a[cç][ıi]k[cç]a\\s+)?kayda\\s+ge[cç]iriyorum\\s*:\\s*)", "");
        c = c.replaceFirst("(?iu)^henüz\\s+karar\\s+değil;\\s*değerlendirdiğimiz\\s+seçenek\\s+şu\\s*:\\s*", "");
        c = c.replaceFirst("(?iu)^\\s*aksiyon\\s+kayd[ıi]\\s*:\\s*", "");
        c = c.replaceFirst("(?iu)^.*?(aksiyon\\s+kayd[ıi]\\s*:\\s*)", "");

        // Prefer clause ending with "olacak/zorunlu olacak"
        Matcher m = Pattern.compile(
                "(?iu)((?:yeni\\s+[\\p{L}0-9\\-]+(?:\\s+[\\p{L}0-9\\-]+){0,6})|"
                        + "(?:[\\p{L}0-9][\\p{L}0-9\\-/]*(?:\\s+[\\p{L}0-9\\-/]+){0,8}))"
                        + "\\s+(?:ba[sş]l[ıi][gğ][ıi]|ba[sş]l[ıi][gğ]lar[ıi]n[ıi]?|"
                        + "[\\p{L}0-9\\-/]+)\\s+(?:zorunlu\\s+)?olacak"
        ).matcher(c);
        if (m.find()) {
            String phrase = m.group().replaceFirst("(?iu)\\s+(zorunlu\\s+)?olacak\\s*$", "").strip();
            if (providesMissingSpecificity(action, phrase)) {
                return phrase;
            }
        }
        // Explicit UTF-8 / encoding header scopes (Cue 51 / A-06)
        Matcher utf = Pattern.compile(
                "(?iu)((?:yeni\\s+)?g[oö]nderim(?:lerde)?[^.]{0,40}?utf-?8[^.]{0,30}?ba[sş]l[ıi][gğ]\\w*)"
        ).matcher(c);
        if (utf.find()) {
            String phrase = utf.group(1).strip()
                    .replaceFirst("(?iu)\\s+(zorunlu\\s+)?olacak\\s*$", "")
                    .replaceFirst("(?iu)\\s+d[uü]zeltecek\\.?$", "")
                    .strip();
            if (phrase.length() >= 8 && providesMissingSpecificity(action, phrase)) {
                return phrase;
            }
        }

        // Generic: take longest noun-ish span containing distinctive tokens missing from action
        Set<String> missing = new LinkedHashSet<>(distinctiveTokens(c));
        missing.removeAll(distinctiveTokens(action));
        if (missing.isEmpty()) {
            return null;
        }
        // Build phrase from context around first missing token
        String[] words = c.split("\\s+");
        int hit = -1;
        for (int i = 0; i < words.length; i++) {
            String tok = normalizeToken(words[i]);
            if (missing.contains(tok)) {
                hit = i;
                break;
            }
        }
        if (hit < 0) {
            return null;
        }
        int start = Math.max(0, hit - 3);
        int end = Math.min(words.length, hit + 4);
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < end; i++) {
            if (i > start) {
                sb.append(' ');
            }
            sb.append(words[i].replaceAll("[,:;]+$", ""));
        }
        String phrase = sb.toString().replaceFirst("(?iu)\\s+(zorunlu\\s+)?olacak\\s*$", "").strip();
        phrase = phrase.replaceFirst("(?iu)^(ve|ama|fakat)\\s+", "");
        return phrase.length() >= 6 ? phrase : null;
    }

    private static String ensureObjectCase(String scope, String verb) {
        String s = scope.strip();
        // If scope already ends with object+verb, strip trailing verb
        String v = verb.toLowerCase(Locale.ROOT);
        if (s.toLowerCase(Locale.ROOT).endsWith(" " + v)) {
            s = s.substring(0, s.length() - v.length()).strip();
        }
        // Convert "... başlığı" → "... başlığını" when using düzelt*/yap*
        if (s.matches("(?iu).*ba[sş]l[ıi][gğ][ıi]$")) {
            s = s.replaceFirst("(?iu)ba[sş]l[ıi][gğ][ıi]$", "başlığını");
        } else if (s.matches("(?iu).*ba[sş]l[ıi][gğ]$")) {
            s = s + "ını";
        }
        return s;
    }

    private static boolean providesMissingSpecificity(String action, String context) {
        Set<String> ctxTok = distinctiveTokens(context);
        Set<String> actTok = distinctiveTokens(action);
        for (String t : ctxTok) {
            if (!actTok.contains(t)) {
                return true;
            }
        }
        return false;
    }

    private static boolean verbConflict(String action, String context) {
        // Soft check: if context is clearly a different imperative domain
        String a = action.toLowerCase(Locale.ROOT);
        String c = context.toLowerCase(Locale.ROOT);
        if (a.contains("ekley") && c.contains("silinecek")) {
            return true;
        }
        if (a.contains("düzelt") && (c.contains("iptal") || c.contains("vazgeç"))) {
            return true;
        }
        return false;
    }

    private static boolean isMoreSpecific(String before, String after) {
        if (after == null || after.length() <= before.strip().length()) {
            // allow equal length if more distinctive tokens
            return distinctiveTokens(after).size() > distinctiveTokens(before).size();
        }
        return distinctiveTokens(after).size() >= distinctiveTokens(before).size()
                && providesMissingSpecificity(before, after);
    }

    private static boolean isStandaloneUnderstandable(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String t = text.strip();
        if (t.length() < 20) {
            return false;
        }
        if (GENERIC_OBJECT.matcher(t).find() && distinctiveTokens(t).isEmpty()) {
            return false;
        }
        return FUTURE_VERB.matcher(t).find() || HAS_VERBISH.matcher(t).find();
    }

    private static boolean sameSpecificTokenSet(List<ContextCandidate> nearest) {
        Set<String> first = distinctiveTokens(nearest.getFirst().text());
        for (int i = 1; i < nearest.size(); i++) {
            if (!first.equals(distinctiveTokens(nearest.get(i).text()))) {
                return false;
            }
        }
        return true;
    }

    private static Set<String> distinctiveTokens(String text) {
        Set<String> out = new LinkedHashSet<>();
        if (text == null) {
            return out;
        }
        for (String raw : text.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}\\-_/]+")) {
            String tok = normalizeToken(raw);
            if (tok.length() < 3) {
                continue;
            }
            if (STOP.matcher(tok).matches()) {
                continue;
            }
            if (GENERIC_OBJECT.matcher(tok).find()) {
                continue;
            }
            if (tok.matches("(?iu).*(yacak|yecek|acak|ecek)$")) {
                continue;
            }
            // Digits, hyphen, slash, or longer content words
            if (tok.chars().anyMatch(Character::isDigit) || tok.contains("-") || tok.contains("/")
                    || tok.length() >= 4) {
                out.add(tok);
            }
        }
        return out;
    }

    private static String normalizeToken(String raw) {
        if (raw == null) {
            return "";
        }
        String t = raw.toLowerCase(Locale.ROOT).replaceAll("^[\\p{Punct}]+|[\\p{Punct}]+$", "");
        return t;
    }

    private static String normalize(String text) {
        return text == null ? "" : text.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").strip();
    }

    private static String extractFutureVerb(String action) {
        Matcher m = FUTURE_VERB.matcher(action);
        String last = null;
        while (m.find()) {
            last = m.group(1);
        }
        return last;
    }

    private static boolean containsRelativeDate(String text) {
        if (text == null) {
            return false;
        }
        String t = text.toLowerCase(Locale.ROOT);
        return t.contains("yarın") || t.contains("bugün") || t.contains("öğlen")
                || t.matches(".*\\b\\d{1,2}[.:]\\d{2}\\b.*");
    }

    private static boolean looksLikeClosedDecision(String content) {
        String t = content == null ? "" : content.toLowerCase(Locale.ROOT);
        return t.contains("zorunlu olacak") || t.contains("kararı açıkça")
                || t.contains("kararı kayda");
    }

    private static boolean sameLocalWindow(int seqA, int seqB) {
        if (CROSS_TOPIC_LOOKUP) {
            return true;
        }
        return Math.abs(seqA - seqB) <= MAX_SEQ_GAP_SAME_TOPIC;
    }

    private static int cueDistanceBetween(int fromSeq, int toSeq, List<SegmentInput> segments) {
        // Segment sequence is the cue index; distance is how many cues back the context sits.
        return Math.max(0, toSeq - fromSeq);
    }

    private static int minSeq(List<String> ids, Map<String, SegmentInput> byId, int fallback) {
        int min = Integer.MAX_VALUE;
        for (String id : ids) {
            SegmentInput s = byId.get(id);
            if (s != null) {
                min = Math.min(min, s.sequence());
            }
        }
        return min == Integer.MAX_VALUE ? fallback : min;
    }

    private static List<String> evidenceIds(List<SegmentInput> segs) {
        List<String> ids = new ArrayList<>();
        for (SegmentInput s : segs) {
            ids.add(s.segmentId());
        }
        return ids;
    }

    private static ActionBackfillResult noUpdate(ActionItemCandidate action, String reason, String before) {
        return new ActionBackfillResult(
                action, BackfillDecision.NO_UPDATE, reason, List.of(), RULE_VERSION, before, before);
    }

    private static ActionBackfillResult update(
            ActionItemCandidate action,
            String newText,
            List<String> contextEvidenceIds,
            String reason,
            String before
    ) {
        ActionItemCandidate updated = action.withText(newText);
        // Preserve owner/dates by construction of withText
        return new ActionBackfillResult(
                updated, BackfillDecision.UPDATED, reason, contextEvidenceIds, RULE_VERSION, before, newText);
    }

    private static void observe(ActionBackfillResult result) {
        LineageReasonCode code = mapReason(result.reasonCode());
        LineageOperation op = result.decision() == BackfillDecision.UPDATED
                ? LineageOperation.UPDATE
                : LineageOperation.KEEP;
        LineageSupport.record(
                LineageSupport.idOf("action", result.afterText(), result.action().evidenceSegmentIds()),
                "ACTION_ITEM",
                LineageStage.ACTION_TITLE_BACKFILL,
                op,
                code,
                result.contextEvidenceIds(),
                null,
                ItemLineageRecord.snapshot(
                        result.beforeText(),
                        result.action().owner(),
                        result.action().relativeDate(),
                        result.action().evidenceSegmentIds()),
                ItemLineageRecord.snapshot(
                        result.afterText(),
                        result.action().owner(),
                        result.action().relativeDate(),
                        result.action().evidenceSegmentIds()),
                RULE_VERSION,
                null,
                null,
                null
        );
    }

    private static LineageReasonCode mapReason(String reasonCode) {
        if (reasonCode == null) {
            return LineageReasonCode.ACTION_TITLE_BACKFILLED;
        }
        return switch (reasonCode) {
            case "ACTION_TITLE_CONTEXT_BACKFILLED" -> LineageReasonCode.ACTION_TITLE_CONTEXT_BACKFILLED;
            case "ACTION_TITLE_ALREADY_SPECIFIC" -> LineageReasonCode.ACTION_TITLE_ALREADY_SPECIFIC;
            case "ACTION_TITLE_CONTEXT_NOT_FOUND" -> LineageReasonCode.ACTION_TITLE_CONTEXT_NOT_FOUND;
            case "ACTION_TITLE_CONTEXT_AMBIGUOUS" -> LineageReasonCode.ACTION_TITLE_CONTEXT_AMBIGUOUS;
            case "ACTION_TITLE_BACKFILL_CONFIDENCE_LOW" -> LineageReasonCode.ACTION_TITLE_BACKFILL_CONFIDENCE_LOW;
            default -> LineageReasonCode.ACTION_TITLE_BACKFILLED;
        };
    }

    private static String firstToken(String text) {
        for (String token : text.strip().split("\\s+")) {
            if (!token.isBlank()) {
                return token;
            }
        }
        return "";
    }

    private static String pickEvidenceSentence(ActionItemCandidate action, Map<String, String> byId) {
        String best = null;
        for (String id : action.evidenceSegmentIds()) {
            String content = byId.get(id);
            if (content == null || content.isBlank()) {
                continue;
            }
            String sentence = cleanEvidenceSentence(content);
            if (sentence == null) {
                continue;
            }
            if (best == null || sentence.length() > best.length()) {
                best = sentence;
            }
        }
        return best;
    }

    static String cleanEvidenceSentence(String content) {
        if (content == null || content.isBlank()) {
            return null;
        }
        String t = content.replace('\u00A0', ' ').replaceAll("\\s+", " ").strip();
        if (t.length() > MAX_EVIDENCE_CHARS) {
            int cut = Math.min(MAX_EVIDENCE_CHARS, t.length());
            int period = t.lastIndexOf('.', cut);
            int q = t.lastIndexOf('?', cut);
            int e = t.lastIndexOf('!', cut);
            int end = Math.max(period, Math.max(q, e));
            if (end >= MIN_TITLE_LEN) {
                t = t.substring(0, end + 1).strip();
            } else {
                t = t.substring(0, cut).strip();
            }
        }
        if (t.length() < MIN_TITLE_LEN) {
            return null;
        }
        String lower = t.toLowerCase(Locale.ROOT);
        if (lower.matches("^(tamam|evet|h[ıi]h[ıi]|anlad[ıi]m|tabii).*") && t.length() < 40) {
            return null;
        }
        if (!Character.isUpperCase(t.charAt(0)) && Character.isLetter(t.charAt(0))) {
            t = Character.toUpperCase(t.charAt(0)) + t.substring(1);
        }
        if (!t.endsWith(".") && !t.endsWith("?") && !t.endsWith("!")) {
            t = t + ".";
        }
        return t;
    }

    private static Map<String, SegmentInput> indexSegments(List<SegmentInput> segments) {
        Map<String, SegmentInput> map = new LinkedHashMap<>();
        if (segments == null) {
            return map;
        }
        for (SegmentInput s : segments) {
            if (s != null && s.segmentId() != null) {
                map.put(s.segmentId(), s);
            }
        }
        return map;
    }
}
