package com.nanobaseai.actenora.meetingintelligence.application.port;

import com.nanobaseai.actenora.sharedkernel.domain.TenantId;

import java.util.UUID;

/**
 * Publishes artifacts from a human-approved note into the continuity ledger.
 */
public interface ApprovedNoteLedgerPort {

    void append(
            TenantId tenantId,
            UUID meetingOccurrenceId,
            UUID noteId,
            UUID noteVersionId
    );
}
