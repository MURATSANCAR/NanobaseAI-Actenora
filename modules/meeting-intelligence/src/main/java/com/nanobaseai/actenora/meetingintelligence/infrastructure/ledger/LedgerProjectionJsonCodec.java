package com.nanobaseai.actenora.meetingintelligence.infrastructure.ledger;

import com.nanobaseai.actenora.meetingintelligence.domain.ledger.CommitmentConfirmation;
import com.nanobaseai.actenora.meetingintelligence.domain.ledger.ContinuityProjection;
import com.nanobaseai.actenora.meetingintelligence.domain.ledger.ContinuityRelationSuggestion;
import com.nanobaseai.actenora.meetingintelligence.domain.ledger.ContinuitySuggestionStatus;
import com.nanobaseai.actenora.meetingintelligence.domain.ledger.ContradictionCandidate;
import com.nanobaseai.actenora.meetingintelligence.domain.ledger.ContradictionStatus;
import com.nanobaseai.actenora.meetingintelligence.domain.ledger.DecisionHistoryEntry;
import com.nanobaseai.actenora.meetingintelligence.domain.ledger.projection.LedgerProjectionState;
import com.nanobaseai.actenora.meetingintelligence.domain.model.ActionItemStatus;
import com.nanobaseai.actenora.meetingintelligence.domain.model.CommitmentConfirmationStatus;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;
import com.nanobaseai.actenora.sharedkernel.persistence.jdbc.JdbcJson;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** JSON codec for {@link LedgerProjectionState} stored in {@code meetingintelligence.meeting_briefs}. */
final class LedgerProjectionJsonCodec {

    private LedgerProjectionJsonCodec() {
    }

    static String write(LedgerProjectionState state) {
        SnapshotDto dto = new SnapshotDto(
                toDecisionDtos(state.decisions()),
                toCommitmentDtos(state.commitments()),
                toActionItemDtos(state.actionItems()),
                toRiskDtos(state.risks()),
                toQuestionDtos(state.openQuestions()),
                toContinuityDtos(state.continuities()),
                toContradictionDtos(state.contradictions()),
                toSuggestionDtos(state.suggestions())
        );
        return JdbcJson.write(dto);
    }

    static LedgerProjectionState read(TenantId tenantId, String json) {
        SnapshotDto dto = JdbcJson.read(json, SnapshotDto.class);
        LedgerProjectionState state = new LedgerProjectionState(tenantId);
        if (dto == null) {
            return state;
        }
        dto.decisions().forEach(d -> state.putDecision(fromDecision(tenantId, d)));
        dto.commitments().forEach(c -> state.putCommitment(fromCommitment(tenantId, c)));
        dto.actionItems().forEach(a -> state.putActionItem(new LedgerProjectionState.TrackedActionItem(
                a.id(), a.meetingOccurrenceId(), a.noteId(), a.text(), ActionItemStatus.valueOf(a.status())
        )));
        dto.risks().forEach(r -> state.putRisk(new LedgerProjectionState.TrackedRisk(
                r.id(), r.meetingOccurrenceId(), r.noteId(), r.text(), r.open()
        )));
        dto.openQuestions().forEach(q -> state.putOpenQuestion(new LedgerProjectionState.TrackedOpenQuestion(
                q.id(), q.meetingOccurrenceId(), q.noteId(), q.text(), q.unresolved()
        )));
        dto.continuities().forEach(c -> state.putContinuity(fromContinuity(tenantId, c)));
        dto.contradictions().forEach(c -> state.putContradiction(ContradictionCandidate.rehydrate(
                c.id(), tenantId, c.meetingOccurrenceId(), c.leftDecisionId(), c.rightDecisionId(),
                c.reason(), new BigDecimal(c.confidence()), ContradictionStatus.valueOf(c.status()),
                c.createdAt(), c.decidedAt(), c.decidedBy()
        )));
        dto.suggestions().forEach(s -> state.putSuggestion(ContinuityRelationSuggestion.rehydrate(
                s.id(), tenantId, s.sourceOccurrenceId(), s.targetOccurrenceId(),
                ContinuityRelationSuggestion.ProposedRelation.valueOf(s.proposedRelation()),
                new BigDecimal(s.confidence()), s.reason(), ContinuitySuggestionStatus.valueOf(s.status()),
                s.createdAt(), s.decidedAt(), s.decidedBy()
        )));
        return state;
    }

    private static List<DecisionDto> toDecisionDtos(Iterable<DecisionHistoryEntry> entries) {
        List<DecisionDto> list = new ArrayList<>();
        for (DecisionHistoryEntry entry : entries) {
            list.add(new DecisionDto(
                    entry.decisionId(),
                    entry.meetingOccurrenceId(),
                    entry.noteId(),
                    entry.text(),
                    entry.supersedesDecisionId().orElse(null),
                    entry.supersededByDecisionId().orElse(null),
                    entry.active(),
                    entry.recordedAt(),
                    entry.updatedAt()
            ));
        }
        return list;
    }

    private static DecisionHistoryEntry fromDecision(TenantId tenantId, DecisionDto dto) {
        return new DecisionHistoryEntry(
                dto.decisionId(),
                tenantId,
                dto.meetingOccurrenceId(),
                dto.noteId(),
                dto.text(),
                Optional.ofNullable(dto.supersedesDecisionId()),
                Optional.ofNullable(dto.supersededByDecisionId()),
                dto.active(),
                dto.recordedAt(),
                dto.updatedAt()
        );
    }

    private static List<CommitmentDto> toCommitmentDtos(Iterable<CommitmentConfirmation> items) {
        List<CommitmentDto> list = new ArrayList<>();
        for (CommitmentConfirmation item : items) {
            list.add(new CommitmentDto(
                    item.commitmentId(),
                    item.meetingOccurrenceId(),
                    item.noteId(),
                    item.text(),
                    item.owner().orElse(null),
                    item.status().name(),
                    item.dueDate().orElse(null),
                    item.overdue(),
                    item.recordedAt(),
                    item.updatedAt(),
                    item.decidedAt().orElse(null),
                    item.decidedByUserId().orElse(null)
            ));
        }
        return list;
    }

    private static CommitmentConfirmation fromCommitment(TenantId tenantId, CommitmentDto dto) {
        CommitmentConfirmation created = CommitmentConfirmation.create(
                dto.commitmentId(),
                tenantId,
                dto.meetingOccurrenceId(),
                dto.noteId(),
                dto.text(),
                dto.owner(),
                dto.dueDate(),
                dto.recordedAt(),
                LocalDate.now()
        );
        if (dto.status().equals(CommitmentConfirmationStatus.PENDING_CONFIRMATION.name())) {
            return created;
        }
        return created.withStatus(
                CommitmentConfirmationStatus.valueOf(dto.status()),
                dto.decidedByUserId(),
                dto.decidedAt() == null ? dto.updatedAt() : dto.decidedAt(),
                dto.dueDate() == null ? LocalDate.now() : dto.dueDate()
        );
    }

    private static List<ActionItemDto> toActionItemDtos(Iterable<LedgerProjectionState.TrackedActionItem> items) {
        List<ActionItemDto> list = new ArrayList<>();
        for (LedgerProjectionState.TrackedActionItem item : items) {
            list.add(new ActionItemDto(
                    item.id(), item.meetingOccurrenceId(), item.noteId(), item.text(), item.status().name()
            ));
        }
        return list;
    }

    private static List<RiskDto> toRiskDtos(Iterable<LedgerProjectionState.TrackedRisk> items) {
        List<RiskDto> list = new ArrayList<>();
        for (LedgerProjectionState.TrackedRisk item : items) {
            list.add(new RiskDto(item.id(), item.meetingOccurrenceId(), item.noteId(), item.text(), item.open()));
        }
        return list;
    }

    private static List<OpenQuestionDto> toQuestionDtos(Iterable<LedgerProjectionState.TrackedOpenQuestion> items) {
        List<OpenQuestionDto> list = new ArrayList<>();
        for (LedgerProjectionState.TrackedOpenQuestion item : items) {
            list.add(new OpenQuestionDto(
                    item.id(), item.meetingOccurrenceId(), item.noteId(), item.text(), item.unresolved()
            ));
        }
        return list;
    }

    private static List<ContinuityDto> toContinuityDtos(Iterable<ContinuityProjection> items) {
        List<ContinuityDto> list = new ArrayList<>();
        for (ContinuityProjection item : items) {
            list.add(new ContinuityDto(
                    item.meetingOccurrenceId(),
                    item.meetingSeriesId().orElse(null),
                    item.businessContextId().orElse(null),
                    item.previousOccurrenceId().orElse(null),
                    item.nextOccurrenceId().orElse(null),
                    item.sameSeriesOccurrenceIds(),
                    item.sameBusinessContextOccurrenceIds(),
                    item.followUpChain(),
                    item.projectedAt()
            ));
        }
        return list;
    }

    private static ContinuityProjection fromContinuity(TenantId tenantId, ContinuityDto dto) {
        ContinuityProjection projection = ContinuityProjection.empty(
                tenantId, dto.meetingOccurrenceId(), dto.projectedAt()
        );
        if (dto.meetingSeriesId() != null) {
            projection = projection.withSeries(dto.meetingSeriesId(), dto.sameSeriesOccurrenceIds(), dto.projectedAt());
        }
        if (dto.businessContextId() != null) {
            projection = projection.withBusinessContext(
                    dto.businessContextId(), dto.sameBusinessContextOccurrenceIds(), dto.projectedAt()
            );
        }
        projection = projection.withNeighbors(
                dto.previousOccurrenceId(), dto.nextOccurrenceId(), dto.projectedAt()
        );
        return projection.withFollowUpChain(dto.followUpChain(), dto.projectedAt());
    }

    private static List<ContradictionDto> toContradictionDtos(Iterable<ContradictionCandidate> items) {
        List<ContradictionDto> list = new ArrayList<>();
        for (ContradictionCandidate item : items) {
            list.add(new ContradictionDto(
                    item.id(), item.meetingOccurrenceId(), item.leftDecisionId(), item.rightDecisionId(),
                    item.reason(), item.confidence().toPlainString(), item.status().name(),
                    item.createdAt(), item.decidedAt().orElse(null), item.decidedBy().orElse(null)
            ));
        }
        return list;
    }

    private static List<SuggestionDto> toSuggestionDtos(Iterable<ContinuityRelationSuggestion> items) {
        List<SuggestionDto> list = new ArrayList<>();
        for (ContinuityRelationSuggestion item : items) {
            list.add(new SuggestionDto(
                    item.id(), item.sourceOccurrenceId(), item.targetOccurrenceId(), item.proposedRelation().name(),
                    item.confidence().toPlainString(), item.reason(), item.status().name(),
                    item.createdAt(), item.decidedAt().orElse(null), item.decidedBy().orElse(null)
            ));
        }
        return list;
    }

    private record SnapshotDto(
            List<DecisionDto> decisions,
            List<CommitmentDto> commitments,
            List<ActionItemDto> actionItems,
            List<RiskDto> risks,
            List<OpenQuestionDto> openQuestions,
            List<ContinuityDto> continuities,
            List<ContradictionDto> contradictions,
            List<SuggestionDto> suggestions
    ) {
    }

    private record DecisionDto(
            UUID decisionId,
            UUID meetingOccurrenceId,
            UUID noteId,
            String text,
            UUID supersedesDecisionId,
            UUID supersededByDecisionId,
            boolean active,
            Instant recordedAt,
            Instant updatedAt
    ) {
    }

    private record CommitmentDto(
            UUID commitmentId,
            UUID meetingOccurrenceId,
            UUID noteId,
            String text,
            String owner,
            String status,
            LocalDate dueDate,
            boolean overdue,
            Instant recordedAt,
            Instant updatedAt,
            Instant decidedAt,
            UUID decidedByUserId
    ) {
    }

    private record ActionItemDto(UUID id, UUID meetingOccurrenceId, UUID noteId, String text, String status) {
    }

    private record RiskDto(UUID id, UUID meetingOccurrenceId, UUID noteId, String text, boolean open) {
    }

    private record OpenQuestionDto(UUID id, UUID meetingOccurrenceId, UUID noteId, String text, boolean unresolved) {
    }

    private record ContinuityDto(
            UUID meetingOccurrenceId,
            UUID meetingSeriesId,
            UUID businessContextId,
            UUID previousOccurrenceId,
            UUID nextOccurrenceId,
            List<UUID> sameSeriesOccurrenceIds,
            List<UUID> sameBusinessContextOccurrenceIds,
            List<UUID> followUpChain,
            Instant projectedAt
    ) {
    }

    private record ContradictionDto(
            UUID id,
            UUID meetingOccurrenceId,
            UUID leftDecisionId,
            UUID rightDecisionId,
            String reason,
            String confidence,
            String status,
            Instant createdAt,
            Instant decidedAt,
            String decidedBy
    ) {
    }

    private record SuggestionDto(
            UUID id,
            UUID sourceOccurrenceId,
            UUID targetOccurrenceId,
            String proposedRelation,
            String confidence,
            String reason,
            String status,
            Instant createdAt,
            Instant decidedAt,
            String decidedBy
    ) {
    }
}
