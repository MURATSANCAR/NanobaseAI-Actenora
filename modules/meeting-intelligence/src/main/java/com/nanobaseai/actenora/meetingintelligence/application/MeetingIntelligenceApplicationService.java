package com.nanobaseai.actenora.meetingintelligence.application;

import com.nanobaseai.actenora.meetingintelligence.api.dto.ActionItemResponse;
import com.nanobaseai.actenora.meetingintelligence.api.dto.ActionItemUpdateRequest;
import com.nanobaseai.actenora.meetingintelligence.api.dto.CommitmentDecisionRequest;
import com.nanobaseai.actenora.meetingintelligence.api.dto.CommitmentResponse;
import com.nanobaseai.actenora.meetingintelligence.api.dto.DecisionResponse;
import com.nanobaseai.actenora.meetingintelligence.api.dto.DecisionUpdateRequest;
import com.nanobaseai.actenora.meetingintelligence.api.dto.EvidenceLinkResponse;
import com.nanobaseai.actenora.meetingintelligence.api.dto.MeetingNoteDetailResponse;
import com.nanobaseai.actenora.meetingintelligence.api.dto.MeetingNoteUpdateRequest;
import com.nanobaseai.actenora.meetingintelligence.api.dto.MeetingNoteVersionResponse;
import com.nanobaseai.actenora.meetingintelligence.api.dto.RiskResponse;
import com.nanobaseai.actenora.meetingintelligence.api.dto.RiskUpdateRequest;
import com.nanobaseai.actenora.meetingintelligence.api.dto.VersionCompareResponse;
import com.nanobaseai.actenora.meetingintelligence.api.dto.MapAiCandidatesCommand;
import com.nanobaseai.actenora.meetingintelligence.application.mapping.MapAiCandidatesToNoteService;
import com.nanobaseai.actenora.meetingintelligence.application.port.ActionItemRepository;
import com.nanobaseai.actenora.meetingintelligence.application.port.ClockPort;
import com.nanobaseai.actenora.meetingintelligence.application.port.CommitmentRepository;
import com.nanobaseai.actenora.meetingintelligence.application.port.DecisionRepository;
import com.nanobaseai.actenora.meetingintelligence.application.port.EvidenceLinkRepository;
import com.nanobaseai.actenora.meetingintelligence.application.port.MeetingNoteRepository;
import com.nanobaseai.actenora.meetingintelligence.application.port.MeetingNoteVersionRepository;
import com.nanobaseai.actenora.meetingintelligence.application.port.OpenQuestionRepository;
import com.nanobaseai.actenora.meetingintelligence.application.port.QualityFlagRepository;
import com.nanobaseai.actenora.meetingintelligence.application.port.RiskRepository;
import com.nanobaseai.actenora.meetingintelligence.application.port.TenantContextPort;
import com.nanobaseai.actenora.meetingintelligence.domain.exception.IntelligenceResourceNotFoundException;
import com.nanobaseai.actenora.meetingintelligence.domain.exception.MeetingNoteNotFoundException;
import com.nanobaseai.actenora.meetingintelligence.domain.exception.NoteVersionImmutableException;
import com.nanobaseai.actenora.meetingintelligence.domain.model.ActionItem;
import com.nanobaseai.actenora.meetingintelligence.domain.model.Commitment;
import com.nanobaseai.actenora.meetingintelligence.domain.model.Decision;
import com.nanobaseai.actenora.meetingintelligence.domain.model.EvidenceLink;
import com.nanobaseai.actenora.meetingintelligence.domain.model.MeetingNote;
import com.nanobaseai.actenora.meetingintelligence.domain.model.MeetingNoteStatus;
import com.nanobaseai.actenora.meetingintelligence.domain.model.MeetingNoteVersion;
import com.nanobaseai.actenora.meetingintelligence.domain.model.QualityFlag;
import com.nanobaseai.actenora.meetingintelligence.domain.model.QualityFlagCode;
import com.nanobaseai.actenora.meetingintelligence.domain.model.Risk;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class MeetingIntelligenceApplicationService {

    private final TenantContextPort tenantContext;
    private final ClockPort clock;
    private final MapAiCandidatesToNoteService mappingService;
    private final MeetingNoteRepository noteRepository;
    private final MeetingNoteVersionRepository versionRepository;
    private final DecisionRepository decisionRepository;
    private final ActionItemRepository actionItemRepository;
    private final RiskRepository riskRepository;
    private final CommitmentRepository commitmentRepository;
    private final OpenQuestionRepository openQuestionRepository;
    private final EvidenceLinkRepository evidenceLinkRepository;
    private final QualityFlagRepository qualityFlagRepository;

    public MeetingIntelligenceApplicationService(
            TenantContextPort tenantContext,
            ClockPort clock,
            MapAiCandidatesToNoteService mappingService,
            MeetingNoteRepository noteRepository,
            MeetingNoteVersionRepository versionRepository,
            DecisionRepository decisionRepository,
            ActionItemRepository actionItemRepository,
            RiskRepository riskRepository,
            CommitmentRepository commitmentRepository,
            OpenQuestionRepository openQuestionRepository,
            EvidenceLinkRepository evidenceLinkRepository,
            QualityFlagRepository qualityFlagRepository
    ) {
        this.tenantContext = Objects.requireNonNull(tenantContext);
        this.clock = Objects.requireNonNull(clock);
        this.mappingService = Objects.requireNonNull(mappingService);
        this.noteRepository = Objects.requireNonNull(noteRepository);
        this.versionRepository = Objects.requireNonNull(versionRepository);
        this.decisionRepository = Objects.requireNonNull(decisionRepository);
        this.actionItemRepository = Objects.requireNonNull(actionItemRepository);
        this.riskRepository = Objects.requireNonNull(riskRepository);
        this.commitmentRepository = Objects.requireNonNull(commitmentRepository);
        this.openQuestionRepository = Objects.requireNonNull(openQuestionRepository);
        this.evidenceLinkRepository = Objects.requireNonNull(evidenceLinkRepository);
        this.qualityFlagRepository = Objects.requireNonNull(qualityFlagRepository);
    }

    public MeetingNoteDetailResponse mapAiCandidates(MapAiCandidatesCommand command) {
        MeetingNote note = mappingService.map(command);
        return detail(note.id(), TenantId.of(command.tenantId()));
    }

    public MeetingNoteDetailResponse noteDetail(UUID noteId) {
        TenantId tenantId = tenantContext.requireTenantId();
        return detail(noteId, tenantId);
    }

    public List<MeetingNoteDetailResponse> listNotesForMeeting(UUID meetingOccurrenceId) {
        TenantId tenantId = tenantContext.requireTenantId();
        return noteRepository.findByMeetingOccurrenceIdAndTenantId(meetingOccurrenceId, tenantId).stream()
                .map(note -> detail(note.id(), tenantId))
                .toList();
    }

    public MeetingNoteDetailResponse updateNote(UUID noteId, MeetingNoteUpdateRequest request) {
        TenantId tenantId = tenantContext.requireTenantId();
        UUID actor = tenantContext.requireActorUserId();
        Objects.requireNonNull(request, "request");

        MeetingNote note = requireNote(noteId, tenantId);
        MeetingNoteVersion current = versionRepository
                .findByIdAndTenantId(note.currentVersionId(), tenantId)
                .orElseThrow(() -> new IntelligenceResourceNotFoundException("MeetingNoteVersion", note.currentVersionId()));

        Instant now = clock.now();
        if (current.approvalStatus() == MeetingNoteStatus.PENDING_APPROVAL) {
            throw new NoteVersionImmutableException();
        }
        if (current.approvalStatus() == MeetingNoteStatus.APPROVED
                || current.approvalStatus() == MeetingNoteStatus.REJECTED
                || current.approvalStatus() == MeetingNoteStatus.CHANGES_REQUESTED) {
            current.transitionApprovalStatus(MeetingNoteStatus.SUPERSEDED);
            versionRepository.save(current);
        }

        MeetingNoteVersion next = note.appendHumanEdit(
                request.executiveSummary(),
                request.correctionReason(),
                actor,
                current.provenance(),
                request.expectedVersion(),
                now
        );
        versionRepository.save(next);
        qualityFlagRepository.save(QualityFlag.create(
                tenantId, note.id(), next.id(), QualityFlagCode.HUMAN_CORRECTION,
                request.correctionReason(), null, null, now
        ));
        noteRepository.save(note);
        return detail(note.id(), tenantId);
    }

    public List<MeetingNoteVersionResponse> listVersions(UUID noteId) {
        TenantId tenantId = tenantContext.requireTenantId();
        requireNote(noteId, tenantId);
        return versionRepository.findAllByNoteId(noteId, tenantId).stream()
                .map(IntelligenceMapper::toVersionResponse)
                .toList();
    }

    public VersionCompareResponse compareVersions(UUID noteId, int fromVersion, int toVersion) {
        TenantId tenantId = tenantContext.requireTenantId();
        requireNote(noteId, tenantId);
        MeetingNoteVersion from = versionRepository.findByNoteIdAndVersionNumber(noteId, fromVersion, tenantId)
                .orElseThrow(() -> new IntelligenceResourceNotFoundException("MeetingNoteVersion", noteId));
        MeetingNoteVersion to = versionRepository.findByNoteIdAndVersionNumber(noteId, toVersion, tenantId)
                .orElseThrow(() -> new IntelligenceResourceNotFoundException("MeetingNoteVersion", noteId));
        return new VersionCompareResponse(
                IntelligenceMapper.toVersionResponse(from),
                IntelligenceMapper.toVersionResponse(to),
                !Objects.equals(from.executiveSummary(), to.executiveSummary())
        );
    }

    public DecisionResponse updateDecision(UUID decisionId, DecisionUpdateRequest request) {
        TenantId tenantId = tenantContext.requireTenantId();
        Decision decision = decisionRepository.findByIdAndTenantId(decisionId, tenantId)
                .orElseThrow(() -> new IntelligenceResourceNotFoundException("Decision", decisionId));
        Instant now = clock.now();
        long expected = request.expectedVersion();
        if (request.text() != null) {
            decision.updateText(request.text(), expected, now);
            expected = decision.version();
        }
        if (request.supersedesDecisionId() != null) {
            Decision older = decisionRepository.findByIdAndTenantId(request.supersedesDecisionId(), tenantId)
                    .orElseThrow(() -> new IntelligenceResourceNotFoundException(
                            "Decision", request.supersedesDecisionId()));
            decision.supersede(older, expected, now);
            decisionRepository.save(older);
        }
        return IntelligenceMapper.toDecisionResponse(decisionRepository.save(decision));
    }

    public ActionItemResponse updateActionItem(UUID actionItemId, ActionItemUpdateRequest request) {
        TenantId tenantId = tenantContext.requireTenantId();
        ActionItem item = actionItemRepository.findByIdAndTenantId(actionItemId, tenantId)
                .orElseThrow(() -> new IntelligenceResourceNotFoundException("ActionItem", actionItemId));
        Instant now = clock.now();
        long expected = request.expectedVersion();
        if (request.text() != null || request.owner() != null || request.dueDate() != null) {
            item.update(request.text(), request.owner(), request.dueDate(), expected, now);
            expected = item.version();
        }
        if (request.targetStatus() != null) {
            item.transitionTo(request.targetStatus(), expected, now);
        }
        return IntelligenceMapper.toActionItemResponse(actionItemRepository.save(item));
    }

    public RiskResponse updateRisk(UUID riskId, RiskUpdateRequest request) {
        TenantId tenantId = tenantContext.requireTenantId();
        Risk risk = riskRepository.findByIdAndTenantId(riskId, tenantId)
                .orElseThrow(() -> new IntelligenceResourceNotFoundException("Risk", riskId));
        risk.updateText(request.text(), request.expectedVersion(), clock.now());
        return IntelligenceMapper.toRiskResponse(riskRepository.save(risk));
    }

    public CommitmentResponse approveCommitment(UUID commitmentId, CommitmentDecisionRequest request) {
        return decideCommitment(commitmentId, request, true);
    }

    public CommitmentResponse rejectCommitment(UUID commitmentId, CommitmentDecisionRequest request) {
        return decideCommitment(commitmentId, request, false);
    }

    public EvidenceLinkResponse evidenceDetail(UUID evidenceId) {
        TenantId tenantId = tenantContext.requireTenantId();
        EvidenceLink link = evidenceLinkRepository.findByIdAndTenantId(evidenceId, tenantId)
                .orElseThrow(() -> new IntelligenceResourceNotFoundException("EvidenceLink", evidenceId));
        return IntelligenceMapper.toEvidenceResponse(link);
    }

    private CommitmentResponse decideCommitment(UUID commitmentId, CommitmentDecisionRequest request, boolean approve) {
        TenantId tenantId = tenantContext.requireTenantId();
        UUID actor = tenantContext.requireActorUserId();
        Commitment commitment = commitmentRepository.findByIdAndTenantId(commitmentId, tenantId)
                .orElseThrow(() -> new IntelligenceResourceNotFoundException("Commitment", commitmentId));
        if (approve) {
            commitment.approve(actor, request.expectedVersion(), clock.now());
        } else {
            commitment.reject(actor, request.expectedVersion(), clock.now());
        }
        return IntelligenceMapper.toCommitmentResponse(commitmentRepository.save(commitment));
    }

    private MeetingNoteDetailResponse detail(UUID noteId, TenantId tenantId) {
        MeetingNote note = requireNote(noteId, tenantId);
        MeetingNoteVersion current = versionRepository.findByIdAndTenantId(note.currentVersionId(), tenantId)
                .orElseThrow(() -> new IntelligenceResourceNotFoundException("MeetingNoteVersion", note.currentVersionId()));
        return IntelligenceMapper.toDetailResponse(
                note,
                current,
                decisionRepository.findByNoteId(noteId, tenantId),
                actionItemRepository.findByNoteId(noteId, tenantId),
                riskRepository.findByNoteId(noteId, tenantId),
                commitmentRepository.findByNoteId(noteId, tenantId),
                openQuestionRepository.findByNoteId(noteId, tenantId),
                evidenceLinkRepository.findByNoteId(noteId, tenantId),
                qualityFlagRepository.findByNoteId(noteId, tenantId)
        );
    }

    private MeetingNote requireNote(UUID noteId, TenantId tenantId) {
        return noteRepository.findByIdAndTenantId(noteId, tenantId)
                .orElseThrow(() -> new MeetingNoteNotFoundException(noteId));
    }
}
