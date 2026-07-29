package com.nanobaseai.actenora.meetingintelligence.application;

import com.nanobaseai.actenora.meetingintelligence.api.dto.ActionItemResponse;
import com.nanobaseai.actenora.meetingintelligence.api.dto.CommitmentResponse;
import com.nanobaseai.actenora.meetingintelligence.api.dto.DecisionResponse;
import com.nanobaseai.actenora.meetingintelligence.api.dto.EvidenceLinkResponse;
import com.nanobaseai.actenora.meetingintelligence.api.dto.ImportantFactResponse;
import com.nanobaseai.actenora.meetingintelligence.api.dto.IssueResponse;
import com.nanobaseai.actenora.meetingintelligence.api.dto.MeetingNoteDetailResponse;
import com.nanobaseai.actenora.meetingintelligence.api.dto.MeetingNoteVersionResponse;
import com.nanobaseai.actenora.meetingintelligence.api.dto.OpenQuestionResponse;
import com.nanobaseai.actenora.meetingintelligence.api.dto.ProposalResponse;
import com.nanobaseai.actenora.meetingintelligence.api.dto.ProvenanceResponse;
import com.nanobaseai.actenora.meetingintelligence.api.dto.QualityFlagResponse;
import com.nanobaseai.actenora.meetingintelligence.api.dto.RiskResponse;
import com.nanobaseai.actenora.meetingintelligence.domain.model.ActionItem;
import com.nanobaseai.actenora.meetingintelligence.domain.model.Commitment;
import com.nanobaseai.actenora.meetingintelligence.domain.model.Decision;
import com.nanobaseai.actenora.meetingintelligence.domain.model.EvidenceLink;
import com.nanobaseai.actenora.meetingintelligence.domain.model.ImportantFact;
import com.nanobaseai.actenora.meetingintelligence.domain.model.Issue;
import com.nanobaseai.actenora.meetingintelligence.domain.model.MeetingNote;
import com.nanobaseai.actenora.meetingintelligence.domain.model.MeetingNoteVersion;
import com.nanobaseai.actenora.meetingintelligence.domain.model.ModelPromptSchemaProvenance;
import com.nanobaseai.actenora.meetingintelligence.domain.model.OpenQuestion;
import com.nanobaseai.actenora.meetingintelligence.domain.model.Proposal;
import com.nanobaseai.actenora.meetingintelligence.domain.model.QualityFlag;
import com.nanobaseai.actenora.meetingintelligence.domain.model.Risk;

import java.util.List;

final class IntelligenceMapper {

    private IntelligenceMapper() {
    }

    static MeetingNoteDetailResponse toDetailResponse(
            MeetingNote note,
            MeetingNoteVersion current,
            List<Decision> decisions,
            List<ActionItem> actionItems,
            List<Risk> risks,
            List<Commitment> commitments,
            List<OpenQuestion> openQuestions,
            List<Issue> issues,
            List<Proposal> proposals,
            List<ImportantFact> importantFacts,
            List<EvidenceLink> evidenceLinks,
            List<QualityFlag> qualityFlags
    ) {
        return new MeetingNoteDetailResponse(
                note.id(),
                note.tenantId().value(),
                note.meetingOccurrenceId(),
                note.reviewStatus(),
                note.currentVersionNumber(),
                note.version(),
                toVersionResponse(current),
                decisions.stream().map(IntelligenceMapper::toDecisionResponse).toList(),
                actionItems.stream().map(IntelligenceMapper::toActionItemResponse).toList(),
                risks.stream().map(IntelligenceMapper::toRiskResponse).toList(),
                commitments.stream().map(IntelligenceMapper::toCommitmentResponse).toList(),
                openQuestions.stream().map(IntelligenceMapper::toOpenQuestionResponse).toList(),
                issues.stream().map(IntelligenceMapper::toIssueResponse).toList(),
                proposals.stream().map(IntelligenceMapper::toProposalResponse).toList(),
                importantFacts.stream().map(IntelligenceMapper::toImportantFactResponse).toList(),
                evidenceLinks.stream().map(IntelligenceMapper::toEvidenceResponse).toList(),
                qualityFlags.stream().map(IntelligenceMapper::toQualityFlagResponse).toList(),
                note.createdAt(),
                note.updatedAt()
        );
    }

    static MeetingNoteVersionResponse toVersionResponse(MeetingNoteVersion version) {
        return new MeetingNoteVersionResponse(
                version.id(),
                version.noteId(),
                version.versionNumber(),
                version.executiveSummary(),
                version.source(),
                toProvenance(version.provenance()),
                version.correctionReason(),
                version.createdByUserId(),
                version.createdAt(),
                version.approvalStatus()
        );
    }

    static DecisionResponse toDecisionResponse(Decision decision) {
        return new DecisionResponse(
                decision.id(),
                decision.noteId(),
                decision.text(),
                decision.supersedesDecisionId(),
                decision.supersededByDecisionId(),
                decision.requiresManualReview(),
                decision.aiConfidence(),
                decision.humanApprovalStatus(),
                decision.rationale(),
                decision.decisionStatus(),
                decision.version(),
                decision.updatedAt()
        );
    }

    static ActionItemResponse toActionItemResponse(ActionItem item) {
        return new ActionItemResponse(
                item.id(),
                item.noteId(),
                item.text(),
                item.owner(),
                item.dueDate(),
                item.status(),
                item.requiresManualReview(),
                item.aiConfidence(),
                item.humanApprovalStatus(),
                item.ownerType(),
                item.priority(),
                item.relativeDate(),
                item.dueAt(),
                item.version(),
                item.updatedAt()
        );
    }

    static RiskResponse toRiskResponse(Risk risk) {
        return new RiskResponse(
                risk.id(),
                risk.noteId(),
                risk.text(),
                risk.requiresManualReview(),
                risk.aiConfidence(),
                risk.humanApprovalStatus(),
                risk.likelihood(),
                risk.mitigation(),
                risk.version(),
                risk.updatedAt()
        );
    }

    static CommitmentResponse toCommitmentResponse(Commitment commitment) {
        return new CommitmentResponse(
                commitment.id(),
                commitment.noteId(),
                commitment.text(),
                commitment.owner(),
                commitment.confirmationStatus(),
                commitment.requiresManualReview(),
                commitment.aiConfidence(),
                commitment.decidedByUserId(),
                commitment.decidedAt(),
                commitment.version(),
                commitment.updatedAt()
        );
    }

    static OpenQuestionResponse toOpenQuestionResponse(OpenQuestion question) {
        return new OpenQuestionResponse(
                question.id(),
                question.noteId(),
                question.text(),
                question.requiresManualReview(),
                question.aiConfidence(),
                question.version(),
                question.updatedAt()
        );
    }

    static IssueResponse toIssueResponse(Issue issue) {
        return new IssueResponse(
                issue.id(),
                issue.noteId(),
                issue.text(),
                issue.requiresManualReview(),
                issue.aiConfidence(),
                issue.humanApprovalStatus(),
                issue.version(),
                issue.updatedAt()
        );
    }

    static ProposalResponse toProposalResponse(Proposal proposal) {
        return new ProposalResponse(
                proposal.id(),
                proposal.noteId(),
                proposal.text(),
                proposal.requiresManualReview(),
                proposal.aiConfidence(),
                proposal.humanApprovalStatus(),
                proposal.version(),
                proposal.updatedAt()
        );
    }

    static ImportantFactResponse toImportantFactResponse(ImportantFact fact) {
        return new ImportantFactResponse(
                fact.id(),
                fact.noteId(),
                fact.text(),
                fact.requiresManualReview(),
                fact.aiConfidence(),
                fact.humanApprovalStatus(),
                fact.version(),
                fact.updatedAt()
        );
    }

    static EvidenceLinkResponse toEvidenceResponse(EvidenceLink link) {
        return new EvidenceLinkResponse(
                link.id(),
                link.noteId(),
                link.noteVersionId(),
                link.subjectType(),
                link.subjectId(),
                link.evidenceSegmentId(),
                link.createdAt()
        );
    }

    static QualityFlagResponse toQualityFlagResponse(QualityFlag flag) {
        return new QualityFlagResponse(
                flag.id(),
                flag.code(),
                flag.detail(),
                flag.subjectType(),
                flag.subjectId(),
                flag.createdAt()
        );
    }

    private static ProvenanceResponse toProvenance(ModelPromptSchemaProvenance provenance) {
        if (provenance == null) {
            return null;
        }
        return new ProvenanceResponse(
                provenance.modelId(),
                provenance.promptVersionId(),
                provenance.schemaId(),
                provenance.aiConfidence()
        );
    }
}
