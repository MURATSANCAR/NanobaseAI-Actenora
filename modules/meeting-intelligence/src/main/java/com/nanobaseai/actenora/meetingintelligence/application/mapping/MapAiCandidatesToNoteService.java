package com.nanobaseai.actenora.meetingintelligence.application.mapping;

import com.nanobaseai.actenora.meetingintelligence.api.dto.ActionItemCandidateInput;
import com.nanobaseai.actenora.meetingintelligence.api.dto.AiCandidateBundle;
import com.nanobaseai.actenora.meetingintelligence.api.dto.CommitmentCandidateInput;
import com.nanobaseai.actenora.meetingintelligence.api.dto.DecisionCandidateInput;
import com.nanobaseai.actenora.meetingintelligence.api.dto.MapAiCandidatesCommand;
import com.nanobaseai.actenora.meetingintelligence.api.dto.OpenQuestionCandidateInput;
import com.nanobaseai.actenora.meetingintelligence.api.dto.RiskCandidateInput;
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
import com.nanobaseai.actenora.meetingintelligence.domain.model.ActionItem;
import com.nanobaseai.actenora.meetingintelligence.domain.model.Commitment;
import com.nanobaseai.actenora.meetingintelligence.domain.model.Decision;
import com.nanobaseai.actenora.meetingintelligence.domain.model.EvidenceLink;
import com.nanobaseai.actenora.meetingintelligence.domain.model.EvidenceSubjectType;
import com.nanobaseai.actenora.meetingintelligence.domain.model.MeetingNote;
import com.nanobaseai.actenora.meetingintelligence.domain.model.MeetingNoteVersion;
import com.nanobaseai.actenora.meetingintelligence.domain.model.ModelPromptSchemaProvenance;
import com.nanobaseai.actenora.meetingintelligence.domain.model.NoteReviewStatus;
import com.nanobaseai.actenora.meetingintelligence.domain.model.OpenQuestion;
import com.nanobaseai.actenora.meetingintelligence.domain.model.QualityFlag;
import com.nanobaseai.actenora.meetingintelligence.domain.model.QualityFlagCode;
import com.nanobaseai.actenora.meetingintelligence.domain.model.Risk;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Sole place that turns AI candidate payloads into persisted corporate business objects.
 * Raw AI response entities are never stored.
 */
public final class MapAiCandidatesToNoteService {

    private final MeetingNoteRepository noteRepository;
    private final MeetingNoteVersionRepository versionRepository;
    private final DecisionRepository decisionRepository;
    private final ActionItemRepository actionItemRepository;
    private final RiskRepository riskRepository;
    private final CommitmentRepository commitmentRepository;
    private final OpenQuestionRepository openQuestionRepository;
    private final EvidenceLinkRepository evidenceLinkRepository;
    private final QualityFlagRepository qualityFlagRepository;
    private final ClockPort clock;

    public MapAiCandidatesToNoteService(
            MeetingNoteRepository noteRepository,
            MeetingNoteVersionRepository versionRepository,
            DecisionRepository decisionRepository,
            ActionItemRepository actionItemRepository,
            RiskRepository riskRepository,
            CommitmentRepository commitmentRepository,
            OpenQuestionRepository openQuestionRepository,
            EvidenceLinkRepository evidenceLinkRepository,
            QualityFlagRepository qualityFlagRepository,
            ClockPort clock
    ) {
        this.noteRepository = Objects.requireNonNull(noteRepository);
        this.versionRepository = Objects.requireNonNull(versionRepository);
        this.decisionRepository = Objects.requireNonNull(decisionRepository);
        this.actionItemRepository = Objects.requireNonNull(actionItemRepository);
        this.riskRepository = Objects.requireNonNull(riskRepository);
        this.commitmentRepository = Objects.requireNonNull(commitmentRepository);
        this.openQuestionRepository = Objects.requireNonNull(openQuestionRepository);
        this.evidenceLinkRepository = Objects.requireNonNull(evidenceLinkRepository);
        this.qualityFlagRepository = Objects.requireNonNull(qualityFlagRepository);
        this.clock = Objects.requireNonNull(clock);
    }

    public MeetingNote map(MapAiCandidatesCommand command) {
        Objects.requireNonNull(command, "command");
        Instant now = clock.now();
        TenantId tenantId = TenantId.of(command.tenantId());
        AiCandidateBundle candidates = command.candidates();

        MeetingNote note = MeetingNote.create(
                tenantId,
                command.meetingOccurrenceId(),
                NoteReviewStatus.ACTIVE,
                now
        );
        MeetingNoteVersion version = note.attachInitialAiVersion(
                candidates.executiveSummary(),
                ModelPromptSchemaProvenance.of(
                        command.modelId(),
                        command.promptVersionId(),
                        command.schemaId(),
                        command.aiConfidence()
                ),
                now
        );

        boolean anyMissingEvidence = false;
        List<QualityFlag> flags = new ArrayList<>();

        for (DecisionCandidateInput candidate : candidates.decisions()) {
            boolean missing = candidate.evidenceSegmentIds().isEmpty();
            anyMissingEvidence |= missing;
            Decision decision = Decision.createFromMapping(
                    tenantId, note.id(), version.id(), candidate.text(), missing, candidate.confidence(), now
            );
            decisionRepository.save(decision);
            linkEvidence(tenantId, note.id(), version.id(), EvidenceSubjectType.DECISION, decision.id(),
                    candidate.evidenceSegmentIds(), now);
            if (missing) {
                flags.add(QualityFlag.create(
                        tenantId, note.id(), version.id(), QualityFlagCode.MISSING_EVIDENCE,
                        "Decision lacks evidence", EvidenceSubjectType.DECISION, decision.id(), now
                ));
            }
        }

        for (ActionItemCandidateInput candidate : candidates.actionItems()) {
            boolean missing = candidate.evidenceSegmentIds().isEmpty();
            anyMissingEvidence |= missing;
            LocalDate due = parseDueDate(candidate.dueDate());
            ActionItem item = ActionItem.createFromMapping(
                    tenantId, note.id(), version.id(), candidate.text(), candidate.owner(), due,
                    missing, candidate.confidence(), now
            );
            actionItemRepository.save(item);
            linkEvidence(tenantId, note.id(), version.id(), EvidenceSubjectType.ACTION_ITEM, item.id(),
                    candidate.evidenceSegmentIds(), now);
            if (missing) {
                flags.add(QualityFlag.create(
                        tenantId, note.id(), version.id(), QualityFlagCode.MISSING_EVIDENCE,
                        "Action item lacks evidence", EvidenceSubjectType.ACTION_ITEM, item.id(), now
                ));
            }
        }

        for (RiskCandidateInput candidate : candidates.risks()) {
            boolean missing = candidate.evidenceSegmentIds().isEmpty();
            anyMissingEvidence |= missing;
            Risk risk = Risk.createFromMapping(
                    tenantId, note.id(), version.id(), candidate.text(), missing, candidate.confidence(), now
            );
            riskRepository.save(risk);
            linkEvidence(tenantId, note.id(), version.id(), EvidenceSubjectType.RISK, risk.id(),
                    candidate.evidenceSegmentIds(), now);
            if (missing) {
                flags.add(QualityFlag.create(
                        tenantId, note.id(), version.id(), QualityFlagCode.MISSING_EVIDENCE,
                        "Risk lacks evidence", EvidenceSubjectType.RISK, risk.id(), now
                ));
            }
        }

        for (CommitmentCandidateInput candidate : candidates.commitments()) {
            boolean missing = candidate.evidenceSegmentIds().isEmpty();
            anyMissingEvidence |= missing;
            Commitment commitment = Commitment.createFromMapping(
                    tenantId, note.id(), version.id(), candidate.text(), candidate.owner(),
                    missing, candidate.confidence(), now
            );
            commitmentRepository.save(commitment);
            linkEvidence(tenantId, note.id(), version.id(), EvidenceSubjectType.COMMITMENT, commitment.id(),
                    candidate.evidenceSegmentIds(), now);
            if (missing) {
                flags.add(QualityFlag.create(
                        tenantId, note.id(), version.id(), QualityFlagCode.MISSING_EVIDENCE,
                        "Commitment lacks evidence", EvidenceSubjectType.COMMITMENT, commitment.id(), now
                ));
            }
        }

        for (OpenQuestionCandidateInput candidate : candidates.openQuestions()) {
            boolean missing = candidate.evidenceSegmentIds().isEmpty();
            anyMissingEvidence |= missing;
            OpenQuestion question = OpenQuestion.createFromMapping(
                    tenantId, note.id(), version.id(), candidate.text(), missing, candidate.confidence(), now
            );
            openQuestionRepository.save(question);
            linkEvidence(tenantId, note.id(), version.id(), EvidenceSubjectType.OPEN_QUESTION, question.id(),
                    candidate.evidenceSegmentIds(), now);
            if (missing) {
                flags.add(QualityFlag.create(
                        tenantId, note.id(), version.id(), QualityFlagCode.MISSING_EVIDENCE,
                        "Open question lacks evidence", EvidenceSubjectType.OPEN_QUESTION, question.id(), now
                ));
            }
        }

        for (String flag : candidates.qualityFlags()) {
            flags.add(QualityFlag.create(
                    tenantId, note.id(), version.id(), QualityFlagCode.OTHER, flag, null, null, now
            ));
        }

        for (String segmentId : candidates.evidenceSegmentIds()) {
            evidenceLinkRepository.save(EvidenceLink.create(
                    tenantId, note.id(), version.id(), EvidenceSubjectType.NOTE_VERSION,
                    version.id(), segmentId, now
            ));
        }

        if (anyMissingEvidence) {
            note.markManualReviewWithoutLock(now);
        }

        for (QualityFlag flag : flags) {
            qualityFlagRepository.save(flag);
        }

        versionRepository.save(version);
        return noteRepository.save(note);
    }

    private void linkEvidence(
            TenantId tenantId,
            UUID noteId,
            UUID noteVersionId,
            EvidenceSubjectType subjectType,
            UUID subjectId,
            List<String> segmentIds,
            Instant now
    ) {
        for (String segmentId : segmentIds) {
            evidenceLinkRepository.save(EvidenceLink.create(
                    tenantId, noteId, noteVersionId, subjectType, subjectId, segmentId, now
            ));
        }
    }

    private static LocalDate parseDueDate(String dueDate) {
        if (dueDate == null || dueDate.isBlank()) {
            return null;
        }
        return LocalDate.parse(dueDate.trim());
    }
}
