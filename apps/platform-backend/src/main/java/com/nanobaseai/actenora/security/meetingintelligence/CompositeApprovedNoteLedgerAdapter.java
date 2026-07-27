package com.nanobaseai.actenora.security.meetingintelligence;

import com.nanobaseai.actenora.aiprocessing.application.pipeline.staged.PipelineGraphFactory;
import com.nanobaseai.actenora.meetingintelligence.application.port.ApprovedKnowledgeIndexerPort;
import com.nanobaseai.actenora.meetingintelligence.application.port.ApprovedNoteLedgerPort;
import com.nanobaseai.actenora.security.aiprocessing.AiPipelineProperties;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;
import org.springframework.beans.factory.ObjectProvider;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Chains continuity-ledger append with approved-knowledge indexing.
 * Staged mode enqueues an EMBEDDING job (indexer runs in that stage).
 * Legacy mode indexes synchronously in-process.
 *
 * <p>Resolves {@link PipelineGraphFactory} lazily to avoid a boot-time cycle with the event backbone.
 */
public final class CompositeApprovedNoteLedgerAdapter implements ApprovedNoteLedgerPort {

    private final ApprovedNoteLedgerPort ledger;
    private final ApprovedKnowledgeIndexerPort knowledgeIndexer;
    private final ObjectProvider<PipelineGraphFactory> graphFactory;
    private final ObjectProvider<AiPipelineProperties> pipelineProperties;

    public CompositeApprovedNoteLedgerAdapter(
            ApprovedNoteLedgerPort ledger,
            ApprovedKnowledgeIndexerPort knowledgeIndexer
    ) {
        this(ledger, knowledgeIndexer, emptyProvider(), emptyProvider());
    }

    public CompositeApprovedNoteLedgerAdapter(
            ApprovedNoteLedgerPort ledger,
            ApprovedKnowledgeIndexerPort knowledgeIndexer,
            ObjectProvider<PipelineGraphFactory> graphFactory,
            ObjectProvider<AiPipelineProperties> pipelineProperties
    ) {
        this.ledger = Objects.requireNonNull(ledger, "ledger");
        this.knowledgeIndexer = Objects.requireNonNull(knowledgeIndexer, "knowledgeIndexer");
        this.graphFactory = graphFactory == null ? emptyProvider() : graphFactory;
        this.pipelineProperties = pipelineProperties == null ? emptyProvider() : pipelineProperties;
    }

    @Override
    public void append(
            TenantId tenantId,
            UUID meetingOccurrenceId,
            UUID noteId,
            UUID noteVersionId
    ) {
        ledger.append(tenantId, meetingOccurrenceId, noteId, noteVersionId);
        PipelineGraphFactory graph = graphFactory.getIfAvailable();
        AiPipelineProperties props = pipelineProperties.getIfAvailable();
        if (graph != null && props != null && props.isStaged()) {
            try {
                graph.admitEmbedding(
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

    private static <T> ObjectProvider<T> emptyProvider() {
        return new ObjectProvider<>() {
            @Override
            public T getObject() {
                throw new IllegalStateException("No object available");
            }

            @Override
            public T getObject(Object... args) {
                throw new IllegalStateException("No object available");
            }

            @Override
            public T getIfAvailable() {
                return null;
            }

            @Override
            public T getIfUnique() {
                return null;
            }

            @Override
            public Stream<T> stream() {
                return Stream.empty();
            }

            @Override
            public Stream<T> orderedStream() {
                return Stream.empty();
            }
        };
    }
}
