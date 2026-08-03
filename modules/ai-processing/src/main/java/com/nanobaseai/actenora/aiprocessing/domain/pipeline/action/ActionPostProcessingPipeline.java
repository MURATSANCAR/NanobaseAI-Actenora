package com.nanobaseai.actenora.aiprocessing.domain.pipeline.action;

import com.nanobaseai.actenora.aiprocessing.domain.pipeline.ActionItemCandidate;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.CommitmentCandidate;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.DecisionCandidate;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.ExtractionBundle;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.FinalNoteDraft;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.SegmentInput;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.normalization.DomainRegisterNormalizer;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.normalization.MeetingTerminologyNormalizer;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Shared action post-processing orchestration for legacy and staged pipelines.
 *
 * <pre>
 * prefix → compound decompose → clause bind → relative-date resolve → dedup → audit flags
 * </pre>
 */
public final class ActionPostProcessingPipeline {

    public static final ZoneId DEFAULT_ZONE = ZoneId.of("Europe/Istanbul");

    private final ActionDiscoursePrefixNormalizer prefixNormalizer;
    private final CompoundActionDecomposer decomposer;
    private final TurkishRelativeDateResolver dateResolver;
    private final ActionDeduplicator deduplicator;
    private final CommitmentOwnerBinder commitmentOwnerBinder;
    private final ActionExtractionAuditor auditor;
    private final ActionTitleEvidenceBackfiller titleBackfiller;
    private final DomainRegisterNormalizer registerNormalizer;
    private final MeetingTerminologyNormalizer terminologyNormalizer;
    private final ActionIdentityNormalizer identityNormalizer = new ActionIdentityNormalizer();

    public ActionPostProcessingPipeline() {
        this(
                new ActionDiscoursePrefixNormalizer(),
                new CompoundActionDecomposer(),
                new TurkishRelativeDateResolver(),
                new ActionDeduplicator(),
                new CommitmentOwnerBinder(),
                new ActionExtractionAuditor(),
                new ActionTitleEvidenceBackfiller(),
                new DomainRegisterNormalizer(),
                MeetingTerminologyNormalizer.productionDefaults()
        );
    }

    public ActionPostProcessingPipeline(
            ActionDiscoursePrefixNormalizer prefixNormalizer,
            CompoundActionDecomposer decomposer,
            TurkishRelativeDateResolver dateResolver,
            ActionDeduplicator deduplicator,
            CommitmentOwnerBinder commitmentOwnerBinder,
            ActionExtractionAuditor auditor
    ) {
        this(
                prefixNormalizer,
                decomposer,
                dateResolver,
                deduplicator,
                commitmentOwnerBinder,
                auditor,
                new ActionTitleEvidenceBackfiller(),
                new DomainRegisterNormalizer(),
                MeetingTerminologyNormalizer.productionDefaults()
        );
    }

    public ActionPostProcessingPipeline(
            ActionDiscoursePrefixNormalizer prefixNormalizer,
            CompoundActionDecomposer decomposer,
            TurkishRelativeDateResolver dateResolver,
            ActionDeduplicator deduplicator,
            CommitmentOwnerBinder commitmentOwnerBinder,
            ActionExtractionAuditor auditor,
            ActionTitleEvidenceBackfiller titleBackfiller,
            DomainRegisterNormalizer registerNormalizer,
            MeetingTerminologyNormalizer terminologyNormalizer
    ) {
        this.prefixNormalizer = Objects.requireNonNull(prefixNormalizer);
        this.decomposer = Objects.requireNonNull(decomposer);
        this.dateResolver = Objects.requireNonNull(dateResolver);
        this.deduplicator = Objects.requireNonNull(deduplicator);
        this.commitmentOwnerBinder = Objects.requireNonNull(commitmentOwnerBinder);
        this.auditor = Objects.requireNonNull(auditor);
        this.titleBackfiller = Objects.requireNonNull(titleBackfiller);
        this.registerNormalizer = Objects.requireNonNull(registerNormalizer);
        this.terminologyNormalizer = Objects.requireNonNull(terminologyNormalizer);
    }

    public static ActionPostProcessingPipeline productionDefaults() {
        return new ActionPostProcessingPipeline();
    }

    public record Context(
            List<SegmentInput> transcriptSegments,
            Set<String> participants,
            OffsetDateTime meetingStartedAt,
            ZoneId meetingTimezone,
            String meetingId
    ) {
        public Context {
            transcriptSegments = transcriptSegments == null ? List.of() : List.copyOf(transcriptSegments);
            participants = participants == null ? Set.of() : Set.copyOf(participants);
            meetingTimezone = meetingTimezone == null ? DEFAULT_ZONE : meetingTimezone;
        }
    }

    public record Result(
            List<ActionItemCandidate> actions,
            List<CommitmentCandidate> commitments,
            List<String> qualityFlags,
            ActionPostProcessingStats stats,
            boolean requiresManualReview
    ) {
    }

    public Result postProcess(
            List<ActionItemCandidate> actions,
            List<CommitmentCandidate> commitments,
            Context context
    ) {
        Objects.requireNonNull(actions, "actions");
        Objects.requireNonNull(commitments, "commitments");
        Context ctx = context == null
                ? new Context(List.of(), Set.of(), OffsetDateTime.now(DEFAULT_ZONE), DEFAULT_ZONE, null)
                : context;
        ActionPostProcessingStats stats = new ActionPostProcessingStats();
        stats.setInputActionCount(actions.size());
        Set<String> participants = new LinkedHashSet<>(ctx.participants());
        for (SegmentInput s : ctx.transcriptSegments()) {
            s.speakerDisplayNameOptional().ifPresent(participants::add);
        }

        List<ActionItemCandidate> working = new ArrayList<>();
        List<String> flags = new ArrayList<>();

        for (ActionItemCandidate action : actions) {
            String stripped = prefixNormalizer.strip(action.text());
            if (!stripped.equals(action.text().strip())) {
                stats.incrementPrefixesRemoved();
            }
            ActionItemCandidate prefixed = action.withText(stripped);
            CompoundActionDecomposer.Decomposition decomposition =
                    decomposer.decompose(prefixed, participants, ctx.transcriptSegments());
            if (decomposition.ambiguous()) {
                stats.incrementAmbiguousCompoundActions();
                stats.warn(decomposition.warning());
                flags.add(CompoundActionDecomposer.AMBIGUOUS_SPLIT);
            }
            if (decomposition.split()) {
                stats.incrementCompoundActionsSplit(decomposition.actions().size());
            }
            working.addAll(decomposition.actions());
        }

        List<ActionItemCandidate> dated = new ArrayList<>();
        for (ActionItemCandidate action : working) {
            dated.add(resolveDates(action, ctx, stats, flags));
        }

        List<ActionItemCandidate> ownerSanitized =
                sanitizeUnknownOwners(dated, participants, ctx.transcriptSegments(), stats);

        List<ActionItemCandidate> titlesFilled =
                titleBackfiller.backfill(ownerSanitized, ctx.transcriptSegments());
        List<ActionItemCandidate> registerNormalized = new ArrayList<>(titlesFilled.size());
        for (ActionItemCandidate action : titlesFilled) {
            String rewritten = normalizeItemText(action.text());
            registerNormalized.add(rewritten.equals(action.text()) ? action : action.withText(rewritten));
        }

        ActionDeduplicator.Result dedup = deduplicator.deduplicate(registerNormalized);
        List<ActionItemCandidate> dedupedActions = new ArrayList<>();
        for (ActionItemCandidate action : dedup.actions()) {
            dedupedActions.add(ensureDueDateFromDueAt(action));
        }
        for (int i = 0; i < dedup.removed(); i++) {
            stats.incrementDuplicatesRemoved();
        }
        flags.addAll(dedup.warnings());

        CommitmentOwnerBinder.Result commitmentsBound =
                commitmentOwnerBinder.bind(commitments, ctx.transcriptSegments());
        List<CommitmentCandidate> normalizedCommitments = new ArrayList<>();
        for (CommitmentCandidate c : commitmentsBound.commitments()) {
            String rewritten = normalizeItemText(c.text());
            if (rewritten.equals(c.text())) {
                normalizedCommitments.add(c);
            } else {
                normalizedCommitments.add(new CommitmentCandidate(
                        rewritten, c.owner(), c.evidenceSegmentIds(), c.confidence()));
            }
        }
        for (int i = 0; i < commitmentsBound.bound(); i++) {
            stats.incrementCommitmentsOwnerBound();
        }

        ActionExtractionAuditor.AuditResult audit = auditor.audit(dedupedActions);
        flags.addAll(audit.flags());
        stats.setAuditStatus(audit.passed() ? "PASSED" : "FAILED");
        stats.setActionTrace(buildActionTrace(dedupedActions, actions, ctx));

        stats.setOutputActionCount(dedupedActions.size());
        boolean manual = !audit.passed()
                || flags.contains(CompoundActionDecomposer.AMBIGUOUS_SPLIT)
                || flags.contains(ActionDeduplicator.AMBIGUOUS_DEDUP)
                || flags.contains(ActionExtractionAuditor.UNRESOLVED_RELATIVE_DATE)
                || flags.contains(ActionExtractionAuditor.DATE_CUE_MISSING_STRUCTURED);
        if (manual && flags.stream().noneMatch(f -> "REQUIRES_MANUAL_REVIEW".equalsIgnoreCase(f))) {
            flags.add("REQUIRES_MANUAL_REVIEW");
        }
        return new Result(
                dedupedActions,
                List.copyOf(normalizedCommitments),
                List.copyOf(new LinkedHashSet<>(flags)),
                stats,
                manual
        );
    }

    public ExtractionBundle applyToBundle(ExtractionBundle bundle, Context context) {
        Result result = postProcess(bundle.actionItems(), bundle.commitments(), context);
        List<String> flags = new ArrayList<>(bundle.qualityFlags());
        for (String f : result.qualityFlags()) {
            if (!flags.contains(f)) {
                flags.add(f);
            }
        }
        return new ExtractionBundle(
                bundle.topics(),
                normalizeDecisions(bundle.decisions()),
                result.actions(),
                bundle.risks(),
                bundle.openQuestions(),
                result.commitments(),
                bundle.issues(),
                bundle.proposals(),
                bundle.importantFacts(),
                flags,
                bundle.evidenceSegmentIds(),
                bundle.confidence()
        );
    }

    public FinalNoteDraft applyToDraft(FinalNoteDraft draft, Context context) {
        return applyToDraftDetailed(draft, context).draft();
    }

    /**
     * Same as {@link #applyToDraft} but preserves structured post-processing stats for artifacts.
     */
    public AppliedDraft applyToDraftDetailed(FinalNoteDraft draft, Context context) {
        Result result = postProcess(draft.actionItems(), draft.commitments(), context);
        List<String> flags = auditor.remapDecisionFlags(draft.qualityFlags(), false);
        for (String f : result.qualityFlags()) {
            if (!flags.contains(f)) {
                flags.add(f);
            }
        }
        // Keep legacy decision consistency token mapping when present.
        if (draft.qualityFlags().stream().anyMatch("CONSISTENCY_AUDIT_PASSED"::equals)
                && flags.stream().noneMatch(ActionExtractionAuditor.DECISION_PASSED::equals)) {
            flags.add(ActionExtractionAuditor.DECISION_PASSED);
        }
        boolean manual = draft.requiresManualReview() || result.requiresManualReview();
        FinalNoteDraft out = new FinalNoteDraft(
                draft.executiveSummary(),
                normalizeDecisions(draft.decisions()),
                result.actions(),
                draft.risks(),
                draft.openQuestions(),
                result.commitments(),
                draft.topics(),
                draft.issues(),
                draft.proposals(),
                draft.importantFacts(),
                flags,
                draft.evidenceSegmentIds(),
                draft.confidence(),
                manual
        );
        return new AppliedDraft(out, result.stats());
    }

    public record AppliedDraft(FinalNoteDraft draft, ActionPostProcessingStats stats) {
    }

    private String normalizeItemText(String text) {
        return registerNormalizer.rewrite(terminologyNormalizer.rewrite(text));
    }

    private List<DecisionCandidate> normalizeDecisions(List<DecisionCandidate> decisions) {
        if (decisions == null || decisions.isEmpty()) {
            return decisions == null ? List.of() : decisions;
        }
        List<DecisionCandidate> out = new ArrayList<>(decisions.size());
        for (DecisionCandidate decision : decisions) {
            String rewritten = normalizeItemText(decision.text());
            if (rewritten.equals(decision.text())) {
                out.add(decision);
            } else {
                out.add(new DecisionCandidate(
                        rewritten,
                        decision.evidenceSegmentIds(),
                        decision.confidence(),
                        decision.rationale(),
                        decision.status()
                ));
            }
        }
        return List.copyOf(out);
    }

    private List<Map<String, Object>> buildActionTrace(
            List<ActionItemCandidate> finalActions,
            List<ActionItemCandidate> originalActions,
            Context ctx
    ) {
        List<Map<String, Object>> trace = new ArrayList<>();
        for (int i = 0; i < finalActions.size(); i++) {
            ActionItemCandidate action = finalActions.get(i);
            ActionItemCandidate parent = inferCompoundParent(action, originalActions);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("index", i + 1);
            row.put("owner", action.owner());
            row.put("textHash", identityNormalizer.textHash(action.text()));
            row.put("normalizedCoreHash", identityNormalizer.coreHash(action));
            row.put("normalizedOwner", identityNormalizer.canonicalOwner(action));
            row.put("dueDate", action.dueDate());
            row.put("dueAt", action.dueAt());
            row.put("relativeDate", action.relativeDate());
            row.put("evidenceSegmentIds", List.copyOf(action.evidenceSegmentIds()));
            row.put("evidenceSpeaker", firstEvidenceSpeaker(action, ctx.transcriptSegments()));
            row.put("parentActionId", parent == null ? null : identityNormalizer.textHash(parent.text()));
            row.put("splitFromCompound", parent != null);
            row.put("dateResolutionStatus", action.dueAt() != null && !action.dueAt().isBlank()
                    ? "RESOLVED"
                    : action.relativeDate() != null && !action.relativeDate().isBlank() ? "UNRESOLVED" : "NONE");
            row.put("dateResolutionSource", action.dueAt() != null && !action.dueAt().isBlank()
                    ? "RELATIVE_DATE_EVIDENCE"
                    : action.dueDate() != null && !action.dueDate().isBlank() ? "MODEL_PROVIDED_UNVERIFIED" : "NONE");
            row.put("dateResolutionReferenceTime",
                    action.dueAt() != null && !action.dueAt().isBlank() && ctx.meetingStartedAt() != null
                            ? ctx.meetingStartedAt().toString()
                            : null);
            row.put("dateResolutionTimezone",
                    action.dueAt() != null && !action.dueAt().isBlank()
                            ? ctx.meetingTimezone().getId()
                            : null);
            row.put("dedupIdentityHash", identityNormalizer.sha256(identityNormalizer.identityKey(action)));
            trace.add(row);
        }
        return trace;
    }

    private ActionItemCandidate inferCompoundParent(
            ActionItemCandidate finalAction,
            List<ActionItemCandidate> originalActions
    ) {
        for (ActionItemCandidate original : originalActions) {
            if (!ActionDeduplicator.looksCompound(original.text())) {
                continue;
            }
            if (!ActionDeduplicator.evidenceOverlap(finalAction.evidenceSegmentIds(), original.evidenceSegmentIds())) {
                continue;
            }
            String finalOwner = identityNormalizer.canonicalOwner(finalAction);
            String originalText = identityNormalizer.normalizeLoose(original.text());
            if (!finalOwner.isBlank() && !originalText.contains(finalOwner)) {
                continue;
            }
            String finalCore = identityNormalizer.canonicalCore(finalAction);
            String coreAnchor = firstCoreToken(finalCore);
            if (!finalCore.isBlank() && !coreAnchor.isBlank() && originalText.contains(coreAnchor)) {
                return original;
            }
        }
        return null;
    }

    private static String firstCoreToken(String core) {
        if (core == null || core.isBlank()) {
            return "";
        }
        for (String token : core.split("\\s+")) {
            if (!token.isBlank()) {
                return token;
            }
        }
        return "";
    }

    private String firstEvidenceSpeaker(ActionItemCandidate action, List<SegmentInput> segments) {
        Set<String> evidenceIds = new LinkedHashSet<>(action.evidenceSegmentIds());
        for (SegmentInput segment : segments) {
            if (evidenceIds.contains(segment.segmentId()) && segment.speakerDisplayName() != null) {
                return segment.speakerDisplayName();
            }
        }
        return null;
    }

    private ActionItemCandidate ensureDueDateFromDueAt(ActionItemCandidate action) {
        if (action.dueDate() != null && !action.dueDate().isBlank()) {
            return action;
        }
        if (action.dueAt() == null || action.dueAt().isBlank()) {
            return action;
        }
        try {
            String dueDate = OffsetDateTime.parse(action.dueAt()).toLocalDate().toString();
            return action.withDates(dueDate, action.relativeDate(), action.dueAt());
        } catch (RuntimeException ex) {
            return action;
        }
    }

    private ActionItemCandidate resolveDates(
            ActionItemCandidate action,
            Context ctx,
            ActionPostProcessingStats stats,
            List<String> flags
    ) {
        String relative = action.relativeDate();
        if ((relative == null || relative.isBlank())) {
            relative = dateResolver.extractPhrase(action.text()).orElse(null);
        }
        if (relative == null || relative.isBlank()) {
            return action;
        }
        stats.incrementDatesDetected();
        TurkishRelativeDateResolver.Result resolved = dateResolver.resolve(
                relative,
                ctx.meetingStartedAt(),
                ctx.meetingTimezone()
        );
        if (resolved.status() == TurkishRelativeDateResolver.Status.RESOLVED && resolved.dueAt() != null) {
            stats.incrementDatesResolved();
            String dueAt = resolved.dueAt().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
            String dueDate = resolved.dueAt().toLocalDate().toString();
            String text = dateResolver.stripPhrase(action.text(), relative);
            text = text.replaceAll("\\s+", " ").strip();
            if (text.isBlank()) {
                text = action.text();
            } else if (!text.endsWith(".")) {
                text = text + (text.endsWith(".") ? "" : "");
                if (!text.endsWith(".")) {
                    text = text + ".";
                }
            }
            // Capitalize
            if (!text.isEmpty()) {
                text = Character.toUpperCase(text.charAt(0)) + text.substring(1);
            }
            return new ActionItemCandidate(
                    text,
                    action.owner(),
                    dueDate,
                    action.evidenceSegmentIds(),
                    action.confidence(),
                    action.ownerType(),
                    action.priority(),
                    relative,
                    dueAt
            );
        }
        if (resolved.status() == TurkishRelativeDateResolver.Status.UNRESOLVED) {
            flags.add(ActionExtractionAuditor.UNRESOLVED_RELATIVE_DATE);
            stats.incrementUnresolvedRelativeDates();
            stats.warn(ActionExtractionAuditor.UNRESOLVED_RELATIVE_DATE);
            return action.withDates(action.dueDate(), relative, action.dueAt());
        }
        return action.withDates(action.dueDate(), relative, action.dueAt());
    }

    public static Set<String> participantsFromSegments(List<SegmentInput> segments) {
        Set<String> set = new LinkedHashSet<>();
        if (segments == null) {
            return set;
        }
        for (SegmentInput s : segments) {
            s.speakerDisplayNameOptional().ifPresent(name -> set.add(name.strip()));
        }
        return set;
    }

    /**
     * Binds owners to the meeting roster (invitees + transcript speakers) and drops
     * hallucinated names. When the roster is empty, clear claimed owners so the quality
     * gate does not hard-reject. When the roster is non-empty, blank owners may be filled
     * from evidence speakers that match an invitee.
     */
    static List<ActionItemCandidate> sanitizeUnknownOwners(
            List<ActionItemCandidate> actions,
            Set<String> participants,
            ActionPostProcessingStats stats
    ) {
        return sanitizeUnknownOwners(actions, participants, List.of(), stats);
    }

    static List<ActionItemCandidate> sanitizeUnknownOwners(
            List<ActionItemCandidate> actions,
            Set<String> participants,
            List<SegmentInput> segments,
            ActionPostProcessingStats stats
    ) {
        List<ActionItemCandidate> out = new ArrayList<>(actions.size());
        for (ActionItemCandidate action : actions) {
            String owner = action.owner();
            if (participants.isEmpty()) {
                if (owner != null && !owner.isBlank()) {
                    stats.incrementOwnersCleared();
                    out.add(action.withOwner(null));
                } else {
                    out.add(action);
                }
                continue;
            }
            String resolved = resolveOwnerToParticipant(owner, participants);
            if (resolved != null) {
                if (owner == null || owner.isBlank() || !resolved.equals(owner)) {
                    stats.incrementOwnersBound();
                }
                out.add(action.withOwner(resolved));
                continue;
            }
            String evidenceSpeaker = firstEvidenceSpeakerName(action, segments);
            String fromEvidence = resolveOwnerToParticipant(evidenceSpeaker, participants);
            if (fromEvidence != null) {
                stats.incrementOwnersBound();
                out.add(action.withOwner(fromEvidence));
                continue;
            }
            if (owner == null || owner.isBlank()) {
                String fromText = ownerHintFromActionText(action.text());
                String boundFromText = resolveOwnerToParticipant(fromText, participants);
                if (boundFromText != null) {
                    stats.incrementOwnersBound();
                    out.add(action.withOwner(boundFromText));
                    continue;
                }
                out.add(action);
                continue;
            }
            stats.incrementOwnersCleared();
            out.add(action.withOwner(null));
        }
        return out;
    }

    /**
     * Pulls a leading person name / honorific from action text when the owner field is blank
     * (common with unattributed ASR where the LLM embeds "Ahmet Bey'in …" in the sentence).
     */
    static String ownerHintFromActionText(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String hint = new ActionIdentityNormalizer().ownerHintFromText(text);
        if (hint != null && !hint.isBlank()) {
            return hint;
        }
        // "Ahmet Bey'in …" / "Murat Bey'den …" / "Görkem Hocam'ın …"
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(
                "(?iu)^\\s*([\\p{L}][\\p{L}'\\-]{1,40})(?:\\s+(?:bey|han[ıi]m|hocam))?['’]?[a-zçğıöşü]*\\b"
        ).matcher(text.strip());
        if (m.find()) {
            return m.group(1);
        }
        return null;
    }

    /**
     * Returns the canonical roster display name when {@code owner} fuzzy-matches a participant,
     * otherwise {@code null}.
     */
    static String resolveOwnerToParticipant(String owner, Set<String> participants) {
        if (owner == null || owner.isBlank() || participants == null || participants.isEmpty()) {
            return null;
        }
        String ownerNorm = stripHonorific(normalizePersonToken(owner));
        if (ownerNorm.isBlank()) {
            return null;
        }
        String ownerFirst = firstToken(ownerNorm);
        String best = null;
        for (String participant : participants) {
            String pNorm = stripHonorific(normalizePersonToken(participant));
            if (pNorm.isBlank()) {
                continue;
            }
            if (pNorm.equals(ownerNorm) || firstToken(pNorm).equals(ownerFirst) || pNorm.startsWith(ownerFirst + " ")) {
                if (best == null || participant.length() > best.length()) {
                    best = participant;
                }
            }
        }
        return best;
    }

    static boolean ownerMatchesParticipant(String owner, Set<String> participants) {
        return resolveOwnerToParticipant(owner, participants) != null;
    }

    private static String firstEvidenceSpeakerName(ActionItemCandidate action, List<SegmentInput> segments) {
        if (segments == null || segments.isEmpty()) {
            return null;
        }
        Set<String> evidenceIds = new LinkedHashSet<>(action.evidenceSegmentIds());
        for (SegmentInput segment : segments) {
            if (evidenceIds.contains(segment.segmentId())
                    && segment.speakerDisplayName() != null
                    && !segment.speakerDisplayName().isBlank()) {
                return segment.speakerDisplayName();
            }
        }
        return null;
    }

    private static String normalizePersonToken(String value) {
        if (value == null) {
            return "";
        }
        return value.trim()
                .toLowerCase(Locale.ROOT)
                .replace('ı', 'i')
                .replace('İ', 'i')
                .replace('ş', 's')
                .replace('Ş', 's')
                .replace('ğ', 'g')
                .replace('Ğ', 'g')
                .replace('ç', 'c')
                .replace('Ç', 'c')
                .replace('ö', 'o')
                .replace('Ö', 'o')
                .replace('ü', 'u')
                .replace('Ü', 'u')
                .replaceAll("[^\\p{Alnum}]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    /** Drops Turkish honorifics so "Ahmet bey" matches "Ahmet Faruk". */
    private static String stripHonorific(String normalized) {
        if (normalized.isBlank()) {
            return normalized;
        }
        return normalized
                .replaceAll("\\b(bey|hanim|hanım|bay|bayan|mr|mrs|ms)\\b", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static String firstToken(String normalized) {
        int space = normalized.indexOf(' ');
        return space < 0 ? normalized : normalized.substring(0, space);
    }

    public static OffsetDateTime parseMeetingStart(String isoOrEmpty, ZoneId zone) {
        ZoneId z = zone == null ? DEFAULT_ZONE : zone;
        if (isoOrEmpty == null || isoOrEmpty.isBlank()) {
            return OffsetDateTime.now(z);
        }
        try {
            return OffsetDateTime.parse(isoOrEmpty);
        } catch (RuntimeException ignored) {
            try {
                return java.time.LocalDate.parse(isoOrEmpty).atStartOfDay(z).toOffsetDateTime();
            } catch (RuntimeException ex) {
                return OffsetDateTime.now(z);
            }
        }
    }

    public static String formatDueAtDisplay(String dueAtIso) {
        if (dueAtIso == null || dueAtIso.isBlank()) {
            return null;
        }
        try {
            OffsetDateTime odt = OffsetDateTime.parse(dueAtIso);
            return odt.format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm", Locale.ROOT));
        } catch (RuntimeException ex) {
            return dueAtIso;
        }
    }
}
