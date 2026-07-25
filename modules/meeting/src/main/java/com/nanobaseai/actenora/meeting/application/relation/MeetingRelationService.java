package com.nanobaseai.actenora.meeting.application.relation;

import com.nanobaseai.actenora.meeting.application.relation.port.MeetingRelationRepository;
import com.nanobaseai.actenora.meeting.application.relation.port.MeetingRelationSuggestionRepository;
import com.nanobaseai.actenora.meeting.application.relation.port.OccurrenceContinuityPort;
import com.nanobaseai.actenora.meeting.application.relation.port.RelationAuditPort;
import com.nanobaseai.actenora.meeting.domain.continuity.ImmutableEventIdentity;
import com.nanobaseai.actenora.meeting.domain.continuity.OccurrenceContinuityKey;
import com.nanobaseai.actenora.meeting.domain.continuity.OccurrenceIdentitySnapshot;
import com.nanobaseai.actenora.meeting.domain.relation.ContinuityProjection;
import com.nanobaseai.actenora.meeting.domain.relation.ContinuityProjector;
import com.nanobaseai.actenora.meeting.domain.relation.CrossTenantRelationException;
import com.nanobaseai.actenora.meeting.domain.relation.FollowUpLink;
import com.nanobaseai.actenora.meeting.domain.relation.MeetingRelation;
import com.nanobaseai.actenora.meeting.domain.relation.MeetingRelationSuggestion;
import com.nanobaseai.actenora.meeting.domain.relation.RelationInvariantChecker;
import com.nanobaseai.actenora.meeting.domain.relation.RelationType;
import com.nanobaseai.actenora.meeting.domain.relation.SeriesResolutionResult;
import com.nanobaseai.actenora.meeting.domain.relation.SeriesResolver;
import com.nanobaseai.actenora.meeting.domain.relation.SuggestionNotFoundException;
import com.nanobaseai.actenora.meeting.domain.relation.SuggestionStatus;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Application façade for meeting relations, suggestions, series resolution, and continuity projection.
 */
public final class MeetingRelationService {

    private final MeetingRelationRepository relationRepository;
    private final MeetingRelationSuggestionRepository suggestionRepository;
    private final OccurrenceContinuityPort occurrencePort;
    private final RelationAuditPort auditPort;
    private final RelationInvariantChecker invariantChecker;
    private final SeriesResolver seriesResolver;
    private final ContinuityProjector continuityProjector;
    private final Clock clock;

    public MeetingRelationService(
            MeetingRelationRepository relationRepository,
            MeetingRelationSuggestionRepository suggestionRepository,
            OccurrenceContinuityPort occurrencePort,
            RelationAuditPort auditPort,
            Clock clock
    ) {
        this.relationRepository = Objects.requireNonNull(relationRepository, "relationRepository");
        this.suggestionRepository = Objects.requireNonNull(suggestionRepository, "suggestionRepository");
        this.occurrencePort = Objects.requireNonNull(occurrencePort, "occurrencePort");
        this.auditPort = Objects.requireNonNull(auditPort, "auditPort");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.invariantChecker = new RelationInvariantChecker();
        this.seriesResolver = new SeriesResolver();
        this.continuityProjector = new ContinuityProjector();
    }

    public MeetingRelation createManualRelation(CreateManualRelationCommand command) {
        requireOccurrence(command.tenantId(), command.sourceOccurrenceId());
        requireOccurrence(command.tenantId(), command.targetOccurrenceId());

        RelationType type = command.relationType() == RelationType.AI_SUGGESTED
                ? RelationType.MANUAL
                : command.relationType();

        Instant now = clock.instant();
        MeetingRelation relation = MeetingRelation.createManual(
                command.tenantId(),
                command.sourceOccurrenceId(),
                command.targetOccurrenceId(),
                type,
                command.actor(),
                now
        );
        invariantChecker.assertCanAdd(relation, relationRepository.findAllByTenant(command.tenantId()));
        MeetingRelation saved = relationRepository.save(relation);
        auditPort.record(
                command.tenantId(),
                command.actor(),
                "RELATION_CREATED",
                "MeetingRelation",
                saved.id(),
                metadata(saved),
                now
        );
        return saved;
    }

    /**
     * Persists an AI suggestion without creating a relation.
     */
    public MeetingRelationSuggestion recordSuggestion(RecordRelationSuggestionCommand command) {
        requireOccurrence(command.tenantId(), command.sourceOccurrenceId());
        requireOccurrence(command.tenantId(), command.targetOccurrenceId());

        Instant now = clock.instant();
        MeetingRelationSuggestion suggestion = MeetingRelationSuggestion.propose(
                command.tenantId(),
                command.sourceOccurrenceId(),
                command.targetOccurrenceId(),
                command.proposedType(),
                command.confidence(),
                command.reason(),
                now
        );
        MeetingRelationSuggestion saved = suggestionRepository.save(suggestion);
        auditPort.record(
                command.tenantId(),
                "ai-suggestion",
                "RELATION_SUGGESTION_RECORDED",
                "MeetingRelationSuggestion",
                saved.id(),
                Map.of(
                        "confidence", saved.confidence(),
                        "reason", saved.reason(),
                        "proposedType", saved.proposedType().name(),
                        "sourceOccurrenceId", saved.sourceOccurrenceId().toString(),
                        "targetOccurrenceId", saved.targetOccurrenceId().toString()
                ),
                now
        );
        return saved;
    }

    public Optional<MeetingRelation> decideSuggestion(DecideSuggestionCommand command) {
        MeetingRelationSuggestion suggestion = suggestionRepository
                .findById(command.tenantId(), command.suggestionId())
                .orElseThrow(() -> new SuggestionNotFoundException(command.suggestionId()));

        Instant now = clock.instant();
        if (command.approve()) {
            MeetingRelationSuggestion approved = suggestionRepository.save(suggestion.approve(command.actor(), now));
            MeetingRelation relation = MeetingRelation.fromApprovedSuggestion(approved, now);
            invariantChecker.assertCanAdd(relation, relationRepository.findAllByTenant(command.tenantId()));
            MeetingRelation saved = relationRepository.save(relation);
            auditPort.record(
                    command.tenantId(),
                    command.actor(),
                    "RELATION_SUGGESTION_APPROVED",
                    "MeetingRelationSuggestion",
                    approved.id(),
                    Map.of(
                            "relationId", saved.id().toString(),
                            "status", SuggestionStatus.APPROVED.name()
                    ),
                    now
            );
            auditPort.record(
                    command.tenantId(),
                    command.actor(),
                    "RELATION_CREATED",
                    "MeetingRelation",
                    saved.id(),
                    metadata(saved),
                    now
            );
            return Optional.of(saved);
        }

        MeetingRelationSuggestion rejected = suggestionRepository.save(suggestion.reject(command.actor(), now));
        auditPort.record(
                command.tenantId(),
                command.actor(),
                "RELATION_SUGGESTION_REJECTED",
                "MeetingRelationSuggestion",
                rejected.id(),
                Map.of("status", SuggestionStatus.REJECTED.name()),
                now
        );
        return Optional.empty();
    }

    public SeriesResolutionResult resolveSeries(
            UUID tenantId,
            ImmutableEventIdentity immutableEventIdentity,
            OccurrenceContinuityKey continuityKey,
            String joinWebUrl
    ) {
        return seriesResolver.resolve(
                tenantId,
                immutableEventIdentity,
                continuityKey,
                joinWebUrl,
                occurrencePort.findAllByTenant(tenantId)
        );
    }

    public ContinuityProjection projectContinuity(UUID tenantId, UUID occurrenceId) {
        OccurrenceIdentitySnapshot focus = requireOccurrence(tenantId, occurrenceId);
        return continuityProjector.project(
                focus,
                occurrencePort.findAllByTenant(tenantId),
                relationRepository.findAllByTenant(tenantId)
        );
    }

    public List<FollowUpLink> listFollowUps(UUID tenantId, UUID occurrenceId) {
        requireOccurrence(tenantId, occurrenceId);
        return relationRepository.findByOccurrence(tenantId, occurrenceId).stream()
                .map(FollowUpLink::from)
                .flatMap(Optional::stream)
                .toList();
    }

    public List<MeetingRelation> listRelations(UUID tenantId, UUID occurrenceId) {
        requireOccurrence(tenantId, occurrenceId);
        return relationRepository.findByOccurrence(tenantId, occurrenceId);
    }

    private OccurrenceIdentitySnapshot requireOccurrence(UUID tenantId, UUID occurrenceId) {
        return occurrencePort.findById(tenantId, occurrenceId)
                .orElseThrow(() -> new CrossTenantRelationException(tenantId, occurrenceId));
    }

    private static Map<String, Object> metadata(MeetingRelation relation) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("relationType", relation.relationType().name());
        map.put("sourceOccurrenceId", relation.sourceOccurrenceId().toString());
        map.put("targetOccurrenceId", relation.targetOccurrenceId().toString());
        if (relation.suggestionId() != null) {
            map.put("suggestionId", relation.suggestionId().toString());
        }
        return map;
    }
}
