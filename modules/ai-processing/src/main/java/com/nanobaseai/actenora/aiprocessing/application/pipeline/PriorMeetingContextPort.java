package com.nanobaseai.actenora.aiprocessing.application.pipeline;

import com.nanobaseai.actenora.sharedkernel.domain.TenantId;

import java.util.Optional;
import java.util.UUID;

/**
 * Loads continuity brief for the current occurrence to seed final minutes synthesis.
 */
public interface PriorMeetingContextPort {

    Optional<PriorMeetingContext> load(TenantId tenantId, UUID meetingOccurrenceId);

    static PriorMeetingContextPort noop() {
        return (tenantId, meetingOccurrenceId) -> Optional.empty();
    }
}
