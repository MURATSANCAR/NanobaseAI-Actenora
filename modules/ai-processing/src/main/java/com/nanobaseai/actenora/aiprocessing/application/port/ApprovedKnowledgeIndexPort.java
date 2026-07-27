package com.nanobaseai.actenora.aiprocessing.application.port;

import java.util.Objects;
import java.util.UUID;

/**
 * Composition-root seam for post-approval knowledge embedding (staged EMBEDDING jobs).
 * Avoids aiprocessing → meetingintelligence Modulith dependency.
 */
public interface ApprovedKnowledgeIndexPort {

    void indexApprovedNote(
            UUID tenantId,
            UUID meetingOccurrenceId,
            UUID noteId,
            UUID noteVersionId
    );

    static ApprovedKnowledgeIndexPort noop() {
        return (tenantId, meetingOccurrenceId, noteId, noteVersionId) -> {
        };
    }

    static ApprovedKnowledgeIndexPort requireNonNull(ApprovedKnowledgeIndexPort port) {
        return Objects.requireNonNullElseGet(port, ApprovedKnowledgeIndexPort::noop);
    }
}
