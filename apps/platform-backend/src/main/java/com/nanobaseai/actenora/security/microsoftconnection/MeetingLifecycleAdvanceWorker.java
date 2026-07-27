package com.nanobaseai.actenora.security.microsoftconnection;

import com.nanobaseai.actenora.meeting.api.MeetingApi;
import com.nanobaseai.actenora.meeting.api.dto.MeetingResponse;
import com.nanobaseai.actenora.meeting.infrastructure.tenancy.FixedTenantContext;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Periodically advances meeting occurrence status from schedule/clock when Graph
 * emits no delta (start/end pass with no calendar change).
 */
public final class MeetingLifecycleAdvanceWorker {

    private static final Logger log = LoggerFactory.getLogger(MeetingLifecycleAdvanceWorker.class);

    private final MeetingApi meetingApi;
    private final FixedTenantContext tenantContext;
    private final int batchSize;

    public MeetingLifecycleAdvanceWorker(
            MeetingApi meetingApi,
            FixedTenantContext tenantContext,
            int batchSize
    ) {
        this.meetingApi = Objects.requireNonNull(meetingApi, "meetingApi");
        this.tenantContext = Objects.requireNonNull(tenantContext, "tenantContext");
        this.batchSize = Math.max(1, batchSize);
    }

    public int runOnce() {
        List<MeetingResponse> due = meetingApi.listMeetingsDueForLifecycleAdvance(batchSize);
        int advanced = 0;
        for (MeetingResponse meeting : due) {
            try {
                tenantContext.use(TenantId.of(meeting.tenantId()), CalendarMeetingUpsertAdapter.SYSTEM_ACTOR);
                MeetingResponse after = meetingApi.advanceMeetingLifecycle(meeting.id(), false);
                if (after.status() != meeting.status()) {
                    advanced++;
                    log.info(
                            "Lifecycle worker advanced meetingId={} {} -> {}",
                            after.id(),
                            meeting.status(),
                            after.status()
                    );
                }
            } catch (RuntimeException ex) {
                log.warn(
                        "Lifecycle worker failed meetingId={} reason={}",
                        meeting.id(),
                        ex.getMessage()
                );
            }
        }
        return advanced;
    }
}
