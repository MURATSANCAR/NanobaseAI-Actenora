package com.nanobaseai.actenora.meetingintelligence.application.knowledge;

import com.nanobaseai.actenora.meetingintelligence.application.port.ActionItemRepository;
import com.nanobaseai.actenora.meetingintelligence.application.port.ApprovedKnowledgeIndexerPort;
import com.nanobaseai.actenora.meetingintelligence.application.port.CommitmentRepository;
import com.nanobaseai.actenora.meetingintelligence.application.port.DecisionRepository;
import com.nanobaseai.actenora.meetingintelligence.application.port.EmbeddingPort;
import com.nanobaseai.actenora.meetingintelligence.application.port.MeetingKnowledgeStorePort;
import com.nanobaseai.actenora.meetingintelligence.application.port.OpenQuestionRepository;
import com.nanobaseai.actenora.meetingintelligence.application.port.RiskRepository;
import com.nanobaseai.actenora.meetingintelligence.domain.knowledge.KnowledgeItemKind;
import com.nanobaseai.actenora.meetingintelligence.domain.knowledge.MeetingKnowledgeItem;
import com.nanobaseai.actenora.meetingintelligence.domain.model.ActionItem;
import com.nanobaseai.actenora.meetingintelligence.domain.model.Commitment;
import com.nanobaseai.actenora.meetingintelligence.domain.model.Decision;
import com.nanobaseai.actenora.meetingintelligence.domain.model.OpenQuestion;
import com.nanobaseai.actenora.meetingintelligence.domain.model.Risk;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Embeds and upserts approved note items only. Embedding failures are non-fatal:
 * the item is still stored for FTS-only retrieval.
 */
public final class ApprovedKnowledgeIndexer implements ApprovedKnowledgeIndexerPort {

    private final DecisionRepository decisions;
    private final ActionItemRepository actionItems;
    private final CommitmentRepository commitments;
    private final RiskRepository risks;
    private final OpenQuestionRepository openQuestions;
    private final MeetingKnowledgeStorePort store;
    private final EmbeddingPort embeddings;
    private final Clock clock;

    public ApprovedKnowledgeIndexer(
            DecisionRepository decisions,
            ActionItemRepository actionItems,
            CommitmentRepository commitments,
            RiskRepository risks,
            OpenQuestionRepository openQuestions,
            MeetingKnowledgeStorePort store,
            EmbeddingPort embeddings,
            Clock clock
    ) {
        this.decisions = Objects.requireNonNull(decisions, "decisions");
        this.actionItems = Objects.requireNonNull(actionItems, "actionItems");
        this.commitments = Objects.requireNonNull(commitments, "commitments");
        this.risks = Objects.requireNonNull(risks, "risks");
        this.openQuestions = Objects.requireNonNull(openQuestions, "openQuestions");
        this.store = Objects.requireNonNull(store, "store");
        this.embeddings = Objects.requireNonNull(embeddings, "embeddings");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public void indexApprovedNote(
            TenantId tenantId,
            UUID meetingOccurrenceId,
            UUID noteId,
            UUID noteVersionId
    ) {
        Instant now = clock.instant();
        for (Decision decision : decisions.findByNoteId(noteId, tenantId)) {
            if (!decision.noteVersionId().equals(noteVersionId)) {
                continue;
            }
            upsert(tenantId, meetingOccurrenceId, noteId, noteVersionId,
                    decision.id(), KnowledgeItemKind.DECISION, decision.text(), now);
        }
        for (ActionItem item : actionItems.findByNoteId(noteId, tenantId)) {
            if (!item.noteVersionId().equals(noteVersionId)) {
                continue;
            }
            upsert(tenantId, meetingOccurrenceId, noteId, noteVersionId,
                    item.id(), KnowledgeItemKind.ACTION_ITEM, item.text(), now);
        }
        for (Commitment commitment : commitments.findByNoteId(noteId, tenantId)) {
            if (!commitment.noteVersionId().equals(noteVersionId)) {
                continue;
            }
            upsert(tenantId, meetingOccurrenceId, noteId, noteVersionId,
                    commitment.id(), KnowledgeItemKind.COMMITMENT, commitment.text(), now);
        }
        for (Risk risk : risks.findByNoteId(noteId, tenantId)) {
            if (!risk.noteVersionId().equals(noteVersionId)) {
                continue;
            }
            upsert(tenantId, meetingOccurrenceId, noteId, noteVersionId,
                    risk.id(), KnowledgeItemKind.RISK, risk.text(), now);
        }
        for (OpenQuestion question : openQuestions.findByNoteId(noteId, tenantId)) {
            if (!question.noteVersionId().equals(noteVersionId)) {
                continue;
            }
            upsert(tenantId, meetingOccurrenceId, noteId, noteVersionId,
                    question.id(), KnowledgeItemKind.OPEN_QUESTION, question.text(), now);
        }
    }

    private void upsert(
            TenantId tenantId,
            UUID meetingOccurrenceId,
            UUID noteId,
            UUID noteVersionId,
            UUID sourceItemId,
            KnowledgeItemKind kind,
            String content,
            Instant now
    ) {
        float[] vector = null;
        try {
            vector = embeddings.embed(content);
        } catch (RuntimeException ignored) {
            // FTS-only fallback
        }
        store.upsert(MeetingKnowledgeItem.create(
                tenantId,
                meetingOccurrenceId,
                noteId,
                noteVersionId,
                sourceItemId,
                kind,
                content,
                vector,
                now
        ));
    }
}
