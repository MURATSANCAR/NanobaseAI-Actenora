package com.nanobaseai.actenora.meetingintelligence.application.mapping;

import com.nanobaseai.actenora.meetingintelligence.api.dto.ActionItemCandidateInput;
import com.nanobaseai.actenora.meetingintelligence.api.dto.AiCandidateBundle;
import com.nanobaseai.actenora.meetingintelligence.api.dto.CommitmentCandidateInput;
import com.nanobaseai.actenora.meetingintelligence.api.dto.DecisionCandidateInput;
import com.nanobaseai.actenora.meetingintelligence.api.dto.ImportantFactCandidateInput;
import com.nanobaseai.actenora.meetingintelligence.api.dto.IssueCandidateInput;
import com.nanobaseai.actenora.meetingintelligence.api.dto.MapAiCandidatesCommand;
import com.nanobaseai.actenora.meetingintelligence.api.dto.OpenQuestionCandidateInput;
import com.nanobaseai.actenora.meetingintelligence.api.dto.ProposalCandidateInput;
import com.nanobaseai.actenora.meetingintelligence.api.dto.RiskCandidateInput;
import com.nanobaseai.actenora.meetingintelligence.application.port.ActionItemRepository;
import com.nanobaseai.actenora.meetingintelligence.application.port.ClockPort;
import com.nanobaseai.actenora.meetingintelligence.application.port.CommitmentRepository;
import com.nanobaseai.actenora.meetingintelligence.application.port.DecisionRepository;
import com.nanobaseai.actenora.meetingintelligence.application.port.EvidenceLinkRepository;
import com.nanobaseai.actenora.meetingintelligence.application.port.ImportantFactRepository;
import com.nanobaseai.actenora.meetingintelligence.application.port.IssueRepository;
import com.nanobaseai.actenora.meetingintelligence.application.port.MeetingNoteRepository;
import com.nanobaseai.actenora.meetingintelligence.application.port.MeetingNoteVersionRepository;
import com.nanobaseai.actenora.meetingintelligence.application.port.OpenQuestionRepository;
import com.nanobaseai.actenora.meetingintelligence.application.port.ProposalRepository;
import com.nanobaseai.actenora.meetingintelligence.application.port.QualityFlagRepository;
import com.nanobaseai.actenora.meetingintelligence.application.port.RiskRepository;
import com.nanobaseai.actenora.meetingintelligence.domain.model.ActionItem;
import com.nanobaseai.actenora.meetingintelligence.domain.model.Commitment;
import com.nanobaseai.actenora.meetingintelligence.domain.model.Decision;
import com.nanobaseai.actenora.meetingintelligence.domain.model.EvidenceLink;
import com.nanobaseai.actenora.meetingintelligence.domain.model.EvidenceSubjectType;
import com.nanobaseai.actenora.meetingintelligence.domain.model.ImportantFact;
import com.nanobaseai.actenora.meetingintelligence.domain.model.Issue;
import com.nanobaseai.actenora.meetingintelligence.domain.model.MeetingNote;
import com.nanobaseai.actenora.meetingintelligence.domain.model.MeetingNoteVersion;
import com.nanobaseai.actenora.meetingintelligence.domain.model.ModelPromptSchemaProvenance;
import com.nanobaseai.actenora.meetingintelligence.domain.model.NoteReviewStatus;
import com.nanobaseai.actenora.meetingintelligence.domain.model.OpenQuestion;
import com.nanobaseai.actenora.meetingintelligence.domain.model.Proposal;
import com.nanobaseai.actenora.meetingintelligence.domain.model.QualityFlag;
import com.nanobaseai.actenora.meetingintelligence.domain.model.QualityFlagCode;
import com.nanobaseai.actenora.meetingintelligence.domain.model.Risk;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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
    private final IssueRepository issueRepository;
    private final ProposalRepository proposalRepository;
    private final ImportantFactRepository importantFactRepository;
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
            IssueRepository issueRepository,
            ProposalRepository proposalRepository,
            ImportantFactRepository importantFactRepository,
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
        this.issueRepository = Objects.requireNonNull(issueRepository);
        this.proposalRepository = Objects.requireNonNull(proposalRepository);
        this.importantFactRepository = Objects.requireNonNull(importantFactRepository);
        this.evidenceLinkRepository = Objects.requireNonNull(evidenceLinkRepository);
        this.qualityFlagRepository = Objects.requireNonNull(qualityFlagRepository);
        this.clock = Objects.requireNonNull(clock);
    }

    public MeetingNote map(MapAiCandidatesCommand command) {
        Objects.requireNonNull(command, "command");
        Instant now = clock.now();
        TenantId tenantId = TenantId.of(command.tenantId());
        AiCandidateBundle candidates = command.candidates();

        List<MeetingNote> existing = noteRepository.findByMeetingOccurrenceIdAndTenantId(
                command.meetingOccurrenceId(), tenantId);
        MeetingNote note;
        MeetingNoteVersion version;
        ModelPromptSchemaProvenance provenance = ModelPromptSchemaProvenance.of(
                command.modelId(),
                command.promptVersionId(),
                command.schemaId(),
                command.aiConfidence()
        );
        if (existing.isEmpty()) {
            note = MeetingNote.create(
                    tenantId,
                    command.meetingOccurrenceId(),
                    NoteReviewStatus.ACTIVE,
                    now
            );
            version = note.attachInitialAiVersion(candidates.executiveSummary(), provenance, now);
        } else {
            note = existing.getFirst();
            version = note.appendAiRemap(candidates.executiveSummary(), provenance, now);
        }

        boolean anyMissingEvidence = false;
        List<QualityFlag> flags = new ArrayList<>();

        for (DecisionCandidateInput candidate : candidates.decisions()) {
            boolean missing = candidate.evidenceSegmentIds().isEmpty();
            anyMissingEvidence |= missing;
            Decision decision = Decision.createFromMapping(
                    tenantId, note.id(), version.id(), candidate.text(), missing, candidate.confidence(),
                    candidate.rationale(), candidate.status(), now
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
            Instant dueAt = parseDueAt(candidate.dueAt());
            if (due == null && dueAt != null) {
                due = dueAt.atZone(java.time.ZoneId.of("Europe/Istanbul")).toLocalDate();
            }
            ActionItem item = ActionItem.createFromMapping(
                    tenantId, note.id(), version.id(), candidate.text(), candidate.owner(), due,
                    missing, candidate.confidence(),
                    candidate.ownerType(), candidate.priority(), candidate.relativeDate(), dueAt, now
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
                    tenantId, note.id(), version.id(), candidate.text(), missing, candidate.confidence(),
                    candidate.likelihood(), candidate.mitigation(), now
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

        for (IssueCandidateInput candidate : candidates.issues()) {
            boolean missing = candidate.evidenceSegmentIds().isEmpty();
            anyMissingEvidence |= missing;
            Issue issue = Issue.createFromMapping(
                    tenantId, note.id(), version.id(), candidate.text(), missing, candidate.confidence(), now
            );
            issueRepository.save(issue);
            linkEvidence(tenantId, note.id(), version.id(), EvidenceSubjectType.ISSUE, issue.id(),
                    candidate.evidenceSegmentIds(), now);
            if (missing) {
                flags.add(QualityFlag.create(
                        tenantId, note.id(), version.id(), QualityFlagCode.MISSING_EVIDENCE,
                        "Issue lacks evidence", EvidenceSubjectType.ISSUE, issue.id(), now
                ));
            }
        }

        for (ProposalCandidateInput candidate : candidates.proposals()) {
            boolean missing = candidate.evidenceSegmentIds().isEmpty();
            anyMissingEvidence |= missing;
            Proposal proposal = Proposal.createFromMapping(
                    tenantId, note.id(), version.id(), candidate.text(), missing, candidate.confidence(), now
            );
            proposalRepository.save(proposal);
            linkEvidence(tenantId, note.id(), version.id(), EvidenceSubjectType.PROPOSAL, proposal.id(),
                    candidate.evidenceSegmentIds(), now);
            if (missing) {
                flags.add(QualityFlag.create(
                        tenantId, note.id(), version.id(), QualityFlagCode.MISSING_EVIDENCE,
                        "Proposal lacks evidence", EvidenceSubjectType.PROPOSAL, proposal.id(), now
                ));
            }
        }

        for (ImportantFactCandidateInput candidate : candidates.importantFacts()) {
            boolean missing = candidate.evidenceSegmentIds().isEmpty();
            anyMissingEvidence |= missing;
            ImportantFact fact = ImportantFact.createFromMapping(
                    tenantId, note.id(), version.id(), candidate.text(), missing, candidate.confidence(), now
            );
            importantFactRepository.save(fact);
            linkEvidence(tenantId, note.id(), version.id(), EvidenceSubjectType.IMPORTANT_FACT, fact.id(),
                    candidate.evidenceSegmentIds(), now);
            if (missing) {
                flags.add(QualityFlag.create(
                        tenantId, note.id(), version.id(), QualityFlagCode.MISSING_EVIDENCE,
                        "Important fact lacks evidence", EvidenceSubjectType.IMPORTANT_FACT, fact.id(), now
                ));
            }
        }

        for (String flag : candidates.qualityFlags()) {
            flags.add(QualityFlag.create(
                    tenantId, note.id(), version.id(), resolvePipelineQualityFlagCode(flag), flag, null, null, now
            ));
        }

        for (String segmentId : candidates.evidenceSegmentIds()) {
            evidenceLinkRepository.save(EvidenceLink.create(
                    tenantId, note.id(), version.id(), EvidenceSubjectType.NOTE_VERSION,
                    version.id(), segmentId, now
            ));
        }

        if (requiresManualReview(anyMissingEvidence, candidates.qualityFlags())) {
            note.markManualReviewWithoutLock(now);
        }

        for (QualityFlag flag : flags) {
            qualityFlagRepository.save(flag);
        }

        versionRepository.save(version);
        return noteRepository.save(note);
    }

    static boolean requiresManualReview(boolean anyMissingEvidence, List<String> qualityFlags) {
        if (anyMissingEvidence) {
            return true;
        }
        return qualityFlags != null && qualityFlags.stream().anyMatch(f ->
                f != null && (
                        f.equalsIgnoreCase("REQUIRES_MANUAL_REVIEW")
                                || f.equalsIgnoreCase("SYNTHESIS_FALLBACK")
                                || f.equalsIgnoreCase("AUDIT_FALLBACK")
                                || f.toUpperCase(Locale.ROOT).endsWith("_FALLBACK")
                ));
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
        try {
            return LocalDate.parse(dueDate.trim());
        } catch (RuntimeException ex) {
            // Relative phrases ("gelecek hafta cuma") stay in text; do not fail the mapping.
            return null;
        }
    }

    private static Instant parseDueAt(String dueAt) {
        if (dueAt == null || dueAt.isBlank()) {
            return null;
        }
        try {
            return java.time.OffsetDateTime.parse(dueAt.trim()).toInstant();
        } catch (RuntimeException ex) {
            try {
                return Instant.parse(dueAt.trim());
            } catch (RuntimeException ignored) {
                return null;
            }
        }
    }

    /**
     * Maps pipeline quality-flag tokens onto durable codes so portal/ops can surface soft-degrades.
     */
    static QualityFlagCode resolvePipelineQualityFlagCode(String flag) {
        if (flag == null || flag.isBlank()) {
            return QualityFlagCode.OTHER;
        }
        String normalized = flag.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "SYNTHESIS_FALLBACK" -> QualityFlagCode.SYNTHESIS_FALLBACK;
            case "AUDIT_FALLBACK" -> QualityFlagCode.AUDIT_FALLBACK;
            case "LOW_CONFIDENCE" -> QualityFlagCode.LOW_CONFIDENCE;
            case "MISSING_EVIDENCE" -> QualityFlagCode.MISSING_EVIDENCE;
            case "SCHEMA_WARNING" -> QualityFlagCode.SCHEMA_WARNING;
            case "TYPE_LAUNDER_DROPPED" -> QualityFlagCode.TYPE_LAUNDER_DROPPED;
            default -> QualityFlagCode.OTHER;
        };
    }
}
