package com.nanobaseai.actenora.security.meetingintelligence;

import com.nanobaseai.actenora.meetingintelligence.application.port.ApprovedKnowledgeIndexerPort;
import com.nanobaseai.actenora.meetingintelligence.application.port.ApprovedNoteLedgerPort;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;

import java.util.Objects;
import java.util.UUID;

/**
 * Chains continuity-ledger append with approved-knowledge indexing.
 */
public final class CompositeApprovedNoteLedgerAdapter implements ApprovedNoteLedgerPort {

    private final ApprovedNoteLedgerPort ledger;
    private final ApprovedKnowledgeIndexerPort knowledgeIndexer;

    public CompositeApprovedNoteLedgerAdapter(
            ApprovedNoteLedgerPort ledger,
            ApprovedKnowledgeIndexerPort knowledgeIndexer
    ) {
        this.ledger = Objects.requireNonNull(ledger, "ledger");
        this.knowledgeIndexer = Objects.requireNonNull(knowledgeIndexer, "knowledgeIndexer");
    }

    @Override
    public void append(
            TenantId tenantId,
            UUID meetingOccurrenceId,
            UUID noteId,
            UUID noteVersionId
    ) {
        ledger.append(tenantId, meetingOccurrenceId, noteId, noteVersionId);
        try {
            knowledgeIndexer.indexApprovedNote(tenantId, meetingOccurrenceId, noteId, noteVersionId);
        } catch (RuntimeException ignored) {
            // Ledger is source of carry-over truth; knowledge index retries on redelivery/ops replay.
        }
    }
}
