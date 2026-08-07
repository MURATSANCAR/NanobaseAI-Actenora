package com.nanobaseai.actenora.transcript.application.port.out;

import com.nanobaseai.actenora.sharedkernel.domain.TenantId;

import java.util.UUID;

/**
 * Records opaque meeting occurrence IDs observed via contract events.
 * Does not join or FK to {@code meeting.*}.
 */
public interface KnownMeetingOccurrenceStore {

    void remember(TenantId tenantId, UUID meetingOccurrenceId);

    boolean isKnown(TenantId tenantId, UUID meetingOccurrenceId);

    /** Test/bootstrap helper: accepts any meeting occurrence id. */
    static KnownMeetingOccurrenceStore allowAll() {
        return new KnownMeetingOccurrenceStore() {
            @Override
            public void remember(TenantId tenantId, UUID meetingOccurrenceId) {
                // no-op
            }

            @Override
            public boolean isKnown(TenantId tenantId, UUID meetingOccurrenceId) {
                return true;
            }
        };
    }
}
