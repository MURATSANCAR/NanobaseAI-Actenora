package com.nanobaseai.actenora.security.meetingintelligence;

import com.nanobaseai.actenora.aiprocessing.application.pipeline.staged.PipelineGraphFactory;
import com.nanobaseai.actenora.meetingintelligence.application.port.ApprovedKnowledgeIndexerPort;
import com.nanobaseai.actenora.meetingintelligence.application.port.ApprovedNoteLedgerPort;
import com.nanobaseai.actenora.security.aiprocessing.AiPipelineProperties;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Chains continuity-ledger append with approved-knowledge indexing.
 * Staged mode enqueues an EMBEDDING job (indexer runs in that stage).
 * Legacy mode indexes synchronously in-process.
 */
public final class CompositeApprovedNoteLedgerAdapter implements ApprovedNoteLedgerPort {

    private final ApprovedNoteLedgerPort ledger;
    private final ApprovedKnowledgeIndexerPort knowledgeIndexer;
    private final PipelineGraphFactory graphFactory;
    private final AiPipelineProperties pipelineProperties;

    public CompositeApprovedNoteLedgerAdapter(
            ApprovedNoteLedgerPort ledger,
            ApprovedKnowledgeIndexerPort knowledgeIndexer
    ) {
        this(ledger, knowledgeIndexer, null, null);
    }

    public CompositeApprovedNoteLedgerAdapter(
            ApprovedNoteLedgerPort ledger,
            ApprovedKnowledgeIndexerPort knowledgeIndexer,
            PipelineGraphFactory graphFactory,
            AiPipelineProperties pipelineProperties
    ) {
        this.ledger = Objects.requireNonNull(ledger, "ledger");
        this.knowledgeIndexer = Objects.requireNonNull(knowledgeIndexer, "knowledgeIndexer");
        this.graphFactory = graphFactory;
        this.pipelineProperties = pipelineProperties;
    }

    @Override
    public void append(
            TenantId tenantId,
            UUID meetingOccurrenceId,
            UUID noteId,
            UUID noteVersionId
    ) {
        ledger.append(tenantId, meetingOccurrenceId, noteId, noteVersionId);
        if (graphFactory != null && pipelineProperties != null && pipelineProperties.isStaged()) {
            try {
                graphFactory.admitEmbedding(
                        tenantId.value(),
                        meetingOccurrenceId,
                        noteId,
                        noteVersionId,
                        "tr",
                        Instant.now()
                );
                return;
            } catch (RuntimeException ignored) {
                // fall through to sync indexer
            }
        }
        try {
            knowledgeIndexer.indexApprovedNote(tenantId, meetingOccurrenceId, noteId, noteVersionId);
        } catch (RuntimeException ignored) {
            // Ledger is source of carry-over truth; knowledge index retries on redelivery/ops replay.
        }
    }
}
