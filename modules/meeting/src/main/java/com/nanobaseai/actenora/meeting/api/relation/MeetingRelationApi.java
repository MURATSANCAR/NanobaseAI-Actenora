package com.nanobaseai.actenora.meeting.api.relation;

import com.nanobaseai.actenora.meeting.application.relation.CreateManualRelationCommand;
import com.nanobaseai.actenora.meeting.application.relation.DecideSuggestionCommand;
import com.nanobaseai.actenora.meeting.application.relation.MeetingRelationService;
import com.nanobaseai.actenora.meeting.application.relation.RecordRelationSuggestionCommand;
import com.nanobaseai.actenora.meeting.domain.continuity.ImmutableEventIdentity;
import com.nanobaseai.actenora.meeting.domain.continuity.OccurrenceContinuityKey;
import com.nanobaseai.actenora.meeting.domain.relation.MeetingRelation;
import com.nanobaseai.actenora.meeting.domain.relation.MeetingRelationSuggestion;
import com.nanobaseai.actenora.meeting.domain.relation.RelationType;
import com.nanobaseai.actenora.meeting.domain.relation.SeriesResolutionResult;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Manual relation HTTP façade (thin). Tenant is always taken from authenticated context,
 * never from the request body.
 *
 * Routes (target):
 * <ul>
 *   <li>POST /api/v1/meeting-relations</li>
 *   <li>GET /api/v1/meeting-occurrences/{id}/relations</li>
 *   <li>POST /api/v1/meeting-relation-suggestions/{id}/approve</li>
 *   <li>POST /api/v1/meeting-relation-suggestions/{id}/reject</li>
 *   <li>GET /api/v1/meeting-occurrences/{id}/continuity</li>
 * </ul>
 */
public final class MeetingRelationApi {

    private final MeetingRelationService service;

    public MeetingRelationApi(MeetingRelationService service) {
        this.service = Objects.requireNonNull(service, "service");
    }

    public MeetingRelationResponse createManual(UUID tenantId, String actor, CreateManualRelationRequest request) {
        MeetingRelation relation = service.createManualRelation(new CreateManualRelationCommand(
                tenantId,
                request.sourceOccurrenceId(),
                request.targetOccurrenceId(),
                request.relationType(),
                actor
        ));
        return MeetingRelationResponse.from(relation);
    }

    public List<MeetingRelationResponse> listForOccurrence(UUID tenantId, UUID occurrenceId) {
        return service.listRelations(tenantId, occurrenceId).stream()
                .map(MeetingRelationResponse::from)
                .toList();
    }

    public MeetingRelationSuggestionResponse recordSuggestion(
            UUID tenantId,
            UUID sourceOccurrenceId,
            UUID targetOccurrenceId,
            RelationType proposedType,
            BigDecimal confidence,
            String reason
    ) {
        MeetingRelationSuggestion suggestion = service.recordSuggestion(new RecordRelationSuggestionCommand(
                tenantId,
                sourceOccurrenceId,
                targetOccurrenceId,
                proposedType,
                confidence,
                reason
        ));
        return MeetingRelationSuggestionResponse.from(suggestion);
    }

    public Optional<MeetingRelationResponse> approveSuggestion(UUID tenantId, UUID suggestionId, String actor) {
        return service.decideSuggestion(new DecideSuggestionCommand(tenantId, suggestionId, true, actor))
                .map(MeetingRelationResponse::from);
    }

    public void rejectSuggestion(UUID tenantId, UUID suggestionId, String actor) {
        service.decideSuggestion(new DecideSuggestionCommand(tenantId, suggestionId, false, actor));
    }

    public ContinuityProjectionResponse continuity(UUID tenantId, UUID occurrenceId) {
        return ContinuityProjectionResponse.from(service.projectContinuity(tenantId, occurrenceId));
    }

    public SeriesResolutionResult resolveSeries(
            UUID tenantId,
            String graphEventImmutableId,
            String graphSeriesMasterId,
            String iCalUId,
            Instant originalStartAt,
            String joinWebUrl
    ) {
        OccurrenceContinuityKey key = graphSeriesMasterId == null || iCalUId == null || originalStartAt == null
                ? null
                : new OccurrenceContinuityKey(graphSeriesMasterId, iCalUId, originalStartAt);
        return service.resolveSeries(
                tenantId,
                ImmutableEventIdentity.of(graphEventImmutableId),
                key,
                joinWebUrl
        );
    }
}
