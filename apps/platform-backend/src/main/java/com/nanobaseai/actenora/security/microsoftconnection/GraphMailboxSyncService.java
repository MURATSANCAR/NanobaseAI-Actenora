package com.nanobaseai.actenora.security.microsoftconnection;

import com.nanobaseai.actenora.microsoftconnection.api.MicrosoftConnectionApi;
import com.nanobaseai.actenora.microsoftconnection.application.model.CalendarEvent;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Pulls Graph calendar delta for a mailbox and upserts Actenora meetings.
 */
public final class GraphMailboxSyncService {

    private final MicrosoftConnectionApi microsoftConnectionApi;
    private final CalendarMeetingUpsertAdapter calendarMeetingUpsertAdapter;

    public GraphMailboxSyncService(
            MicrosoftConnectionApi microsoftConnectionApi,
            CalendarMeetingUpsertAdapter calendarMeetingUpsertAdapter
    ) {
        this.microsoftConnectionApi = Objects.requireNonNull(microsoftConnectionApi);
        this.calendarMeetingUpsertAdapter = Objects.requireNonNull(calendarMeetingUpsertAdapter);
    }

    public SyncResult syncMailbox(UUID tenantId, String mailboxUserId) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(mailboxUserId, "mailboxUserId");
        List<CalendarEvent> events = microsoftConnectionApi.syncCalendar(tenantId, mailboxUserId);
        calendarMeetingUpsertAdapter.upsertEvents(TenantId.of(tenantId), events);
        microsoftConnectionApi.ensureTranscriptionForCalendarEvents(tenantId, mailboxUserId, events);
        return new SyncResult(mailboxUserId, events.size());
    }

    public record SyncResult(String mailboxUserId, int eventsSynced) {
    }
}
