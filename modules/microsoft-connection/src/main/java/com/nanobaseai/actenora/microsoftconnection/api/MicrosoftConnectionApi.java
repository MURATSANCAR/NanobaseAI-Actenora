package com.nanobaseai.actenora.microsoftconnection.api;

import com.nanobaseai.actenora.microsoftconnection.application.CalendarSyncService;
import com.nanobaseai.actenora.microsoftconnection.application.MeetingTranscriptService;
import com.nanobaseai.actenora.microsoftconnection.application.OnlineMeetingTranscriptionEnabler;
import com.nanobaseai.actenora.microsoftconnection.application.PollingFallbackService;
import com.nanobaseai.actenora.microsoftconnection.application.ReconciliationJob;
import com.nanobaseai.actenora.microsoftconnection.application.SubscriptionLifecycleService;
import com.nanobaseai.actenora.microsoftconnection.application.model.CalendarEvent;
import com.nanobaseai.actenora.microsoftconnection.application.model.GraphChangeNotification;
import com.nanobaseai.actenora.microsoftconnection.application.model.GraphSubscription;
import com.nanobaseai.actenora.microsoftconnection.application.model.LifecycleNotification;
import com.nanobaseai.actenora.microsoftconnection.application.model.MailSendRequest;
import com.nanobaseai.actenora.microsoftconnection.application.model.MailSendResult;
import com.nanobaseai.actenora.microsoftconnection.application.model.OnlineMeetingMetadata;
import com.nanobaseai.actenora.microsoftconnection.application.model.ParticipantMetadata;
import com.nanobaseai.actenora.microsoftconnection.application.model.SubscriptionCreateRequest;
import com.nanobaseai.actenora.microsoftconnection.application.model.TranscriptAvailability;
import com.nanobaseai.actenora.microsoftconnection.application.model.TranscriptContent;
import com.nanobaseai.actenora.microsoftconnection.application.port.MailGateway;
import com.nanobaseai.actenora.microsoftconnection.application.port.SubscriptionStore;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.BiConsumer;

/**
 * Public façade for the Microsoft Connection bounded context.
 * Cross-module callers use types in this package / application.model only.
 */
public final class MicrosoftConnectionApi {

    private final CalendarSyncService calendarSyncService;
    private final MeetingTranscriptService meetingTranscriptService;
    private final SubscriptionLifecycleService subscriptionLifecycleService;
    private final PollingFallbackService pollingFallbackService;
    private final ReconciliationJob reconciliationJob;
    private final MailGateway mailGateway;
    private final SubscriptionStore subscriptionStore;
    private final OnlineMeetingTranscriptionEnabler transcriptionEnabler;

    public MicrosoftConnectionApi(
            CalendarSyncService calendarSyncService,
            MeetingTranscriptService meetingTranscriptService,
            SubscriptionLifecycleService subscriptionLifecycleService,
            PollingFallbackService pollingFallbackService,
            ReconciliationJob reconciliationJob,
            MailGateway mailGateway,
            SubscriptionStore subscriptionStore,
            OnlineMeetingTranscriptionEnabler transcriptionEnabler
    ) {
        this.calendarSyncService = Objects.requireNonNull(calendarSyncService, "calendarSyncService");
        this.meetingTranscriptService = Objects.requireNonNull(meetingTranscriptService, "meetingTranscriptService");
        this.subscriptionLifecycleService = Objects.requireNonNull(
                subscriptionLifecycleService, "subscriptionLifecycleService");
        this.pollingFallbackService = Objects.requireNonNull(pollingFallbackService, "pollingFallbackService");
        this.reconciliationJob = Objects.requireNonNull(reconciliationJob, "reconciliationJob");
        this.mailGateway = Objects.requireNonNull(mailGateway, "mailGateway");
        this.subscriptionStore = Objects.requireNonNull(subscriptionStore, "subscriptionStore");
        this.transcriptionEnabler = Objects.requireNonNull(transcriptionEnabler, "transcriptionEnabler");
    }

    public List<CalendarEvent> syncCalendar(UUID tenantId, String userId) {
        return calendarSyncService.syncMailbox(tenantId, userId);
    }

    /**
     * Delta sync with process-then-advance: {@code onPage} runs before the cursor is persisted.
     *
     * @return total events processed across pages
     */
    public int syncCalendar(UUID tenantId, String userId, Consumer<List<CalendarEvent>> onPage) {
        return calendarSyncService.syncMailbox(tenantId, userId, onPage);
    }

    public void ensureTranscriptionForCalendarEvents(UUID tenantId, String userId, List<CalendarEvent> events) {
        transcriptionEnabler.enableForUpcomingMeetings(tenantId, userId, events);
    }

    public Optional<OnlineMeetingMetadata> getMeeting(UUID tenantId, String userId, String meetingId) {
        return meetingTranscriptService.meetingMetadata(tenantId, userId, meetingId);
    }

    public Optional<OnlineMeetingMetadata> getMeetingByJoinWebUrl(
            UUID tenantId,
            String userId,
            String joinWebUrl
    ) {
        return meetingTranscriptService.meetingMetadataByJoinWebUrl(tenantId, userId, joinWebUrl);
    }

    public List<ParticipantMetadata> listParticipants(UUID tenantId, String userId, String meetingId) {
        return meetingTranscriptService.participants(tenantId, userId, meetingId);
    }

    public TranscriptAvailability checkTranscript(UUID tenantId, String userId, String meetingId) {
        return meetingTranscriptService.transcriptAvailability(tenantId, userId, meetingId);
    }

    public Optional<TranscriptContent> downloadTranscript(
            UUID tenantId,
            String userId,
            String meetingId,
            String transcriptId
    ) {
        return meetingTranscriptService.downloadTranscript(tenantId, userId, meetingId, transcriptId);
    }

    public GraphSubscription createSubscription(UUID tenantId, SubscriptionCreateRequest request) {
        return subscriptionLifecycleService.create(tenantId, request);
    }

    public List<GraphSubscription> listSubscriptions(UUID tenantId) {
        return subscriptionStore.findAllForTenant(tenantId);
    }

    public List<GraphSubscription> renewExpiringSubscriptions() {
        return subscriptionLifecycleService.renewExpiring();
    }

    public boolean onChangeNotification(
            GraphChangeNotification notification,
            Consumer<GraphChangeNotification> handler
    ) {
        return subscriptionLifecycleService.handleChangeNotification(notification, handler);
    }

    public boolean onLifecycleNotification(
            LifecycleNotification notification,
            Consumer<LifecycleNotification> handler
    ) {
        return subscriptionLifecycleService.handleLifecycleNotification(notification, handler);
    }

    public List<CalendarEvent> pollFallback(UUID tenantId, String userId) {
        return pollingFallbackService.pollMailbox(tenantId, userId);
    }

    public ReconciliationJob.ReconciliationResult reconcile(List<PollingFallbackService.MailboxRef> mailboxes) {
        return reconciliationJob.run(mailboxes);
    }

    public ReconciliationJob.ReconciliationResult reconcile(
            List<PollingFallbackService.MailboxRef> mailboxes,
            BiConsumer<PollingFallbackService.MailboxRef, List<CalendarEvent>> eventConsumer
    ) {
        return reconciliationJob.run(mailboxes, eventConsumer);
    }

    public MailSendResult sendMail(UUID tenantId, MailSendRequest request) {
        return mailGateway.send(tenantId, request);
    }
}
