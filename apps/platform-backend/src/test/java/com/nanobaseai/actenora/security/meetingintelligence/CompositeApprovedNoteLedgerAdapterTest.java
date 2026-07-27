package com.nanobaseai.actenora.security.meetingintelligence;

import com.nanobaseai.actenora.meetingintelligence.api.ledger.ContinuityLedgerApi;
import com.nanobaseai.actenora.meetingintelligence.application.knowledge.ApprovedKnowledgeIndexer;
import com.nanobaseai.actenora.meetingintelligence.application.ledger.ContinuityLedgerService;
import com.nanobaseai.actenora.meetingintelligence.domain.knowledge.KnowledgeItemKind;
import com.nanobaseai.actenora.meetingintelligence.domain.model.ActionItem;
import com.nanobaseai.actenora.meetingintelligence.domain.model.Decision;
import com.nanobaseai.actenora.meetingintelligence.infrastructure.embedding.HashEmbeddingPort;
import com.nanobaseai.actenora.meetingintelligence.infrastructure.ledger.InMemoryLedgerEventStore;
import com.nanobaseai.actenora.meetingintelligence.infrastructure.ledger.InMemoryLedgerProjectionRepository;
import com.nanobaseai.actenora.meetingintelligence.infrastructure.persistence.InMemoryActionItemRepository;
import com.nanobaseai.actenora.meetingintelligence.infrastructure.persistence.InMemoryCommitmentRepository;
import com.nanobaseai.actenora.meetingintelligence.infrastructure.persistence.InMemoryDecisionRepository;
import com.nanobaseai.actenora.meetingintelligence.infrastructure.persistence.InMemoryMeetingKnowledgeStore;
import com.nanobaseai.actenora.meetingintelligence.infrastructure.persistence.InMemoryOpenQuestionRepository;
import com.nanobaseai.actenora.meetingintelligence.infrastructure.persistence.InMemoryRiskRepository;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompositeApprovedNoteLedgerAdapterTest {

    @Test
    void appendsLedgerCarryOversAndIndexesKnowledge() {
        Instant now = Instant.parse("2026-07-27T10:00:00Z");
        Clock clock = Clock.fixed(now, ZoneOffset.UTC);
        TenantId tenant = TenantId.of(UUID.randomUUID());
        UUID occurrence = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        UUID noteVersionId = UUID.randomUUID();

        InMemoryDecisionRepository decisions = new InMemoryDecisionRepository();
        InMemoryActionItemRepository actionItems = new InMemoryActionItemRepository();
        InMemoryCommitmentRepository commitments = new InMemoryCommitmentRepository();
        InMemoryRiskRepository risks = new InMemoryRiskRepository();
        InMemoryOpenQuestionRepository openQuestions = new InMemoryOpenQuestionRepository();
        ContinuityLedgerApi ledgerApi = new ContinuityLedgerApi(new ContinuityLedgerService(
                new InMemoryLedgerEventStore(),
                new InMemoryLedgerProjectionRepository(),
                clock
        ));
        InMemoryMeetingKnowledgeStore knowledge = new InMemoryMeetingKnowledgeStore();

        Decision decision = Decision.createFromMapping(tenant, noteId, noteVersionId, "Ship feature X", false, 0.9, now);
        decisions.save(decision);
        ActionItem action = ActionItem.createFromMapping(
                tenant, noteId, noteVersionId, "Write release notes", null, null, false, 0.8, now);
        actionItems.save(action);

        ApprovedNoteLedgerAdapter ledger = new ApprovedNoteLedgerAdapter(
                decisions, actionItems, commitments, risks, openQuestions, ledgerApi);
        ApprovedKnowledgeIndexer indexer = new ApprovedKnowledgeIndexer(
                decisions, actionItems, commitments, risks, openQuestions,
                knowledge, new HashEmbeddingPort(32), clock);

        new CompositeApprovedNoteLedgerAdapter(ledger, indexer)
                .append(tenant, occurrence, noteId, noteVersionId);

        assertEquals(1, ledgerApi.listActionItems(tenant).size());
        assertFalse(knowledge.findByOccurrence(tenant, occurrence).isEmpty());
        assertEquals(
                KnowledgeItemKind.DECISION,
                knowledge.findBySource(tenant, decision.id(), KnowledgeItemKind.DECISION).getFirst().itemKind()
        );
    }

    @Test
    void knowledgeFailureDoesNotUnwindLedger() {
        AtomicBoolean ledgerCalled = new AtomicBoolean();
        CompositeApprovedNoteLedgerAdapter composite = new CompositeApprovedNoteLedgerAdapter(
                (t, m, n, v) -> ledgerCalled.set(true),
                (t, m, n, v) -> {
                    throw new IllegalStateException("embed down");
                }
        );
        composite.append(TenantId.of(UUID.randomUUID()), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        assertTrue(ledgerCalled.get());
    }
}
