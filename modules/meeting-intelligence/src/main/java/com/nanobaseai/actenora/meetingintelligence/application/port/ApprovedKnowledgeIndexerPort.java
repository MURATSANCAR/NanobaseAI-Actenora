package com.nanobaseai.actenora.meetingintelligence.application.port;

import com.nanobaseai.actenora.sharedkernel.domain.TenantId;

import java.util.UUID;

/**
 * Indexes human-approved note artifacts into the knowledge store (never raw transcript).
 */
public interface ApprovedKnowledgeIndexerPort {

    void indexApprovedNote(
            TenantId tenantId,
            UUID meetingOccurrenceId,
            UUID noteId,
            UUID noteVersionId
    );
}
