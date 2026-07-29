package com.nanobaseai.actenora.aiprocessing.domain.pipeline.action;

import com.nanobaseai.actenora.aiprocessing.domain.pipeline.ActionItemCandidate;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.CommitmentCandidate;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.ExtractionBundle;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.FinalNoteDraft;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.SegmentInput;

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
    private final ActionIdentityNormalizer identityNormalizer = new ActionIdentityNormalizer();

    public ActionPostProcessingPipeline() {
        this(
                new ActionDiscoursePrefixNormalizer(),
                new CompoundActionDecomposer(),
                new TurkishRelativeDateResolver(),
                new ActionDeduplicator(),
                new CommitmentOwnerBinder(),
                new ActionExtractionAuditor()
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
        this.prefixNormalizer = Objects.requireNonNull(prefixNormalizer);
        this.decomposer = Objects.requireNonNull(decomposer);
        this.dateResolver = Objects.requireNonNull(dateResolver);
        this.deduplicator = Objects.requireNonNull(deduplicator);
        this.commitmentOwnerBinder = Objects.requireNonNull(commitmentOwnerBinder);
        this.auditor = Objects.requireNonNull(auditor);
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

        ActionDeduplicator.Result dedup = deduplicator.deduplicate(dated);
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
                commitmentsBound.commitments(),
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
                bundle.decisions(),
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
                draft.decisions(),
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
            if (!finalCore.isBlank() && originalText.contains(finalCore.split("\\s+")[0])) {
                return original;
            }
        }
        return null;
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
