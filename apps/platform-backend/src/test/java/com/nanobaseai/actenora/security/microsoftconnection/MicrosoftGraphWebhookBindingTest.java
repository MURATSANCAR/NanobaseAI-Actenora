package com.nanobaseai.actenora.security.microsoftconnection;

import com.nanobaseai.actenora.microsoftconnection.api.MicrosoftConnectionApi;
import com.nanobaseai.actenora.microsoftconnection.application.CalendarSyncService;
import com.nanobaseai.actenora.microsoftconnection.application.MeetingTranscriptService;
import com.nanobaseai.actenora.microsoftconnection.application.PollingFallbackService;
import com.nanobaseai.actenora.microsoftconnection.application.ReconciliationJob;
import com.nanobaseai.actenora.microsoftconnection.application.SubscriptionLifecycleService;
import com.nanobaseai.actenora.microsoftconnection.application.model.CalendarEvent;
import com.nanobaseai.actenora.microsoftconnection.application.model.CalendarSyncCursor;
import com.nanobaseai.actenora.microsoftconnection.application.model.GraphSubscription;
import com.nanobaseai.actenora.microsoftconnection.application.model.MailSendRequest;
import com.nanobaseai.actenora.microsoftconnection.application.model.MailSendResult;
import com.nanobaseai.actenora.microsoftconnection.application.model.OnlineMeetingMetadata;
import com.nanobaseai.actenora.microsoftconnection.application.model.ParticipantMetadata;
import com.nanobaseai.actenora.microsoftconnection.application.model.SubscriptionCreateRequest;
import com.nanobaseai.actenora.microsoftconnection.application.model.TranscriptAvailability;
import com.nanobaseai.actenora.microsoftconnection.application.model.TranscriptContent;
import com.nanobaseai.actenora.microsoftconnection.application.port.CalendarGateway;
import com.nanobaseai.actenora.microsoftconnection.application.port.CalendarSyncCursorStore;
import com.nanobaseai.actenora.microsoftconnection.application.port.MailGateway;
import com.nanobaseai.actenora.microsoftconnection.application.port.OnlineMeetingGateway;
import com.nanobaseai.actenora.microsoftconnection.application.port.SubscriptionGateway;
import com.nanobaseai.actenora.microsoftconnection.application.port.TranscriptGateway;
import com.nanobaseai.actenora.microsoftconnection.infrastructure.notification.InMemoryNotificationInbox;
import com.nanobaseai.actenora.microsoftconnection.infrastructure.persistence.InMemorySubscriptionStore;
import com.nanobaseai.actenora.security.microsoftconnection.MicrosoftGraphWebhookController.GraphNotificationBatch;
import com.nanobaseai.actenora.security.microsoftconnection.MicrosoftGraphWebhookController.GraphNotificationItem;
import com.nanobaseai.actenora.security.microsoftconnection.MicrosoftGraphWebhookController.GraphResourceData;
import com.nanobaseai.actenora.security.microsoftconnection.MicrosoftGraphWebhookController.GraphWebhookResultView;
import com.nanobaseai.actenora.meeting.api.MeetingApi;
import com.nanobaseai.actenora.meeting.infrastructure.tenancy.FixedTenantContext;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;
import com.nanobaseai.actenora.tenant.api.TenantApi;
import com.nanobaseai.actenora.sharedkernel.error.ActenoraException;
import com.nanobaseai.actenora.sharedkernel.time.InstantClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * FAZ 32 — Microsoft Graph notification webhook binding: validation handshake, clientState auth,
 * and per-notification idempotent dispatch.
 */
class MicrosoftGraphWebhookBindingTest {

    private static final String CLIENT_STATE = "test-client-state";

    private MicrosoftGraphWebhookController controller;
    private MicrosoftConnectionApi api;

    @BeforeEach
    void setUp() {
        InMemorySubscriptionStore subscriptionStore = new InMemorySubscriptionStore();
        api = new MicrosoftConnectionApi(
                new CalendarSyncService(new StubCalendarGateway(), new StubCursorStore(), InstantClock.systemUTC()),
                new MeetingTranscriptService(new StubOnlineMeetingGateway(), new StubTranscriptGateway()),
                new SubscriptionLifecycleService(
                        new StubSubscriptionGateway(),
                        subscriptionStore,
                        new InMemoryNotificationInbox(),
                        InstantClock.systemUTC(),
                        Duration.ofHours(6),
                        Duration.ofHours(48)
                ),
                buildPollingFallback(),
                buildReconciliationJob(),
                new StubMailGateway(),
                subscriptionStore
        );
        controller = new MicrosoftGraphWebhookController(
                api,
                buildProcessor(api),
                new MockEnvironment(),
                CLIENT_STATE
        );
    }

    private static GraphChangeNotificationProcessor buildProcessor(MicrosoftConnectionApi api) {
        MeetingApi meetingApi = org.mockito.Mockito.mock(MeetingApi.class);
        org.mockito.Mockito.when(meetingApi.listBusinessContexts()).thenReturn(List.of());
        CalendarMeetingUpsertAdapter upsertAdapter =
                new CalendarMeetingUpsertAdapter(meetingApi, new FixedTenantContext(TenantId.random(), UUID.randomUUID()));
        TenantApi tenantApi = org.mockito.Mockito.mock(TenantApi.class);
        return new GraphChangeNotificationProcessor(api, upsertAdapter, tenantApi, emptyOutboxProvider());
    }

    @Test
    void validationHandshakeEchoesToken() {
        ResponseEntity<?> response = controller.notifications("abc-123", null);

        assertEquals(200, response.getStatusCode().value());
        assertEquals(MediaType.TEXT_PLAIN, response.getHeaders().getContentType());
        assertEquals("abc-123", response.getBody());
    }

    @Test
    void emptyBatchRejected() {
        assertThrows(ActenoraException.class, () -> controller.notifications(null, null));
        assertThrows(
                ActenoraException.class,
                () -> controller.notifications(null, new GraphNotificationBatch(List.of()))
        );
    }

    @Test
    void changeNotificationDispatchedThenDeduplicated() {
        GraphNotificationBatch batch = new GraphNotificationBatch(List.of(changeItem(CLIENT_STATE)));

        GraphWebhookResultView first = process(batch);
        assertEquals(1, first.received());
        assertEquals(1, first.processed());
        assertEquals(0, first.duplicates());
        assertEquals(0, first.rejected());

        GraphWebhookResultView second = process(batch);
        assertEquals(1, second.received());
        assertEquals(0, second.processed());
        assertEquals(1, second.duplicates());
    }

    @Test
    void wrongClientStateRejected() {
        GraphWebhookResultView result = process(new GraphNotificationBatch(List.of(changeItem("wrong-secret"))));
        assertEquals(1, result.received());
        assertEquals(0, result.processed());
        assertEquals(1, result.rejected());
    }

    @Test
    void lifecycleNotificationDispatched() {
        GraphNotificationItem lifecycle = new GraphNotificationItem(
                "sub-1", null, null, null, "reauthorizationRequired", CLIENT_STATE,
                UUID.randomUUID().toString(), null
        );
        GraphWebhookResultView result = process(new GraphNotificationBatch(List.of(lifecycle)));
        assertEquals(1, result.received());
        assertEquals(1, result.processed());
    }

    @Test
    void blankClientStateRejectedOnProdProfile() {
        MockEnvironment prod = new MockEnvironment();
        prod.setActiveProfiles("prod");
        MicrosoftGraphWebhookController prodController = new MicrosoftGraphWebhookController(
                api,
                buildProcessor(api),
                prod,
                ""
        );
        GraphWebhookResultView result = process(prodController, new GraphNotificationBatch(List.of(changeItem(CLIENT_STATE))));
        assertEquals(1, result.rejected());
        assertEquals(0, result.processed());
    }

    private static ObjectProvider<com.nanobaseai.actenora.sharedkernel.messaging.port.OutboxPublisher> emptyOutboxProvider() {
        DefaultListableBeanFactory factory = new DefaultListableBeanFactory();
        return factory.getBeanProvider(com.nanobaseai.actenora.sharedkernel.messaging.port.OutboxPublisher.class);
    }

    private GraphWebhookResultView process(MicrosoftGraphWebhookController target, GraphNotificationBatch batch) {
        ResponseEntity<?> response = target.notifications(null, batch);
        assertEquals(202, response.getStatusCode().value());
        return (GraphWebhookResultView) response.getBody();
    }

    private GraphWebhookResultView process(GraphNotificationBatch batch) {
        return process(controller, batch);
    }

    private static GraphNotificationItem changeItem(String clientState) {
        return new GraphNotificationItem(
                "sub-1",
                "created",
                "communications/onlineMeetings('abc')/transcripts",
                new GraphResourceData("meeting/transcripts('t-1')", "t-1"),
                null,
                clientState,
                UUID.randomUUID().toString(),
                null
        );
    }

    private static PollingFallbackService buildPollingFallback() {
        return new PollingFallbackService(
                new CalendarSyncService(new StubCalendarGateway(), new StubCursorStore(), InstantClock.systemUTC())
        );
    }

    private static ReconciliationJob buildReconciliationJob() {
        SubscriptionLifecycleService lifecycle = new SubscriptionLifecycleService(
                new StubSubscriptionGateway(),
                new InMemorySubscriptionStore(),
                new InMemoryNotificationInbox(),
                InstantClock.systemUTC(),
                Duration.ofHours(6),
                Duration.ofHours(48)
        );
        return new ReconciliationJob(lifecycle, buildPollingFallback());
    }

    private static final class StubCalendarGateway implements CalendarGateway {
        @Override
        public com.nanobaseai.actenora.microsoftconnection.application.model.CalendarDeltaPage syncDelta(
                UUID tenantId, String userId, CalendarSyncCursor cursor) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<CalendarEvent> getEvent(UUID tenantId, String userId, String eventId) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class StubCursorStore implements CalendarSyncCursorStore {
        @Override
        public Optional<CalendarSyncCursor> find(UUID tenantId, String userId) {
            return Optional.empty();
        }

        @Override
        public void save(CalendarSyncCursor cursor) {
        }
    }

    private static final class StubOnlineMeetingGateway implements OnlineMeetingGateway {
        @Override
        public Optional<OnlineMeetingMetadata> getByJoinWebUrl(UUID tenantId, String userId, String joinWebUrl) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<OnlineMeetingMetadata> getByMeetingId(UUID tenantId, String userId, String meetingId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<ParticipantMetadata> listParticipants(UUID tenantId, String userId, String meetingId) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class StubTranscriptGateway implements TranscriptGateway {
        @Override
        public TranscriptAvailability checkAvailability(UUID tenantId, String userId, String meetingId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<TranscriptContent> download(
                UUID tenantId, String userId, String meetingId, String transcriptId) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class StubMailGateway implements MailGateway {
        @Override
        public MailSendResult send(UUID tenantId, MailSendRequest request) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class StubSubscriptionGateway implements SubscriptionGateway {
        @Override
        public GraphSubscription create(UUID tenantId, SubscriptionCreateRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public GraphSubscription renew(UUID tenantId, String subscriptionId, Instant newExpiration) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<GraphSubscription> get(UUID tenantId, String subscriptionId) {
            return Optional.empty();
        }

        @Override
        public void delete(UUID tenantId, String subscriptionId) {
        }
    }
}
