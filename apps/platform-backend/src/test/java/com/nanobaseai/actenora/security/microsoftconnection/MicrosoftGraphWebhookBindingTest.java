package com.nanobaseai.actenora.security.microsoftconnection;

import com.nanobaseai.actenora.microsoftconnection.api.MicrosoftConnectionApi;
import com.nanobaseai.actenora.microsoftconnection.application.CalendarSyncService;
import com.nanobaseai.actenora.microsoftconnection.application.MeetingTranscriptService;
import com.nanobaseai.actenora.microsoftconnection.application.PollingFallbackService;
import com.nanobaseai.actenora.microsoftconnection.application.OnlineMeetingTranscriptionEnabler;
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
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.nanobaseai.actenora.meeting.infrastructure.tenancy.FixedTenantContext;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;
import com.nanobaseai.actenora.tenant.api.TenantApi;
import com.nanobaseai.actenora.sharedkernel.error.ActenoraException;
import com.nanobaseai.actenora.sharedkernel.time.InstantClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
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
    private InMemorySubscriptionStore subscriptionStore;

    @BeforeEach
    void setUp() {
        subscriptionStore = new InMemorySubscriptionStore();
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
                subscriptionStore,
                new OnlineMeetingTranscriptionEnabler(
                        new StubOnlineMeetingGateway(), InstantClock.systemUTC(), false)
        );
        controller = new MicrosoftGraphWebhookController(
                api,
                buildProcessor(),
                subscriptionStore,
                emptyProvider(GraphObservability.class),
                JsonMapper.builder().findAndAddModules().build(),
                new MockEnvironment(),
                CLIENT_STATE
        );
    }

    private static GraphChangeNotificationProcessor buildProcessor() {
        TenantApi tenantApi = org.mockito.Mockito.mock(TenantApi.class);
        org.mockito.Mockito.when(tenantApi.findById(org.mockito.ArgumentMatchers.any())).thenAnswer(inv -> {
            TenantId id = inv.getArgument(0);
            return Optional.of(new com.nanobaseai.actenora.tenant.api.TenantView(
                    id,
                    "sandbox",
                    com.nanobaseai.actenora.tenant.domain.TenantStatus.ACTIVE,
                    "UTC",
                    "en",
                    365,
                    id.value().toString(),
                    java.time.Instant.now(),
                    java.time.Instant.now(),
                    0L
            ));
        });
        org.mockito.Mockito.when(tenantApi.findByEntraTenantId(org.mockito.ArgumentMatchers.anyString()))
                .thenAnswer(inv -> {
                    TenantId id = TenantId.random();
                    return Optional.of(new com.nanobaseai.actenora.tenant.api.TenantView(
                            id,
                            "sandbox",
                            com.nanobaseai.actenora.tenant.domain.TenantStatus.ACTIVE,
                            "UTC",
                            "en",
                            365,
                            inv.getArgument(0),
                            java.time.Instant.now(),
                            java.time.Instant.now(),
                            0L
                    ));
                });
        return new GraphChangeNotificationProcessor(
                tenantApi,
                outboxProvider(),
                emptyProvider(GraphObservability.class),
                emptyProvider(GraphChangeWorkConsumer.class),
                "jdbc-rabbit");
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
        assertThrows(ActenoraException.class, () -> controller.dispatchNotifications(null));
        assertThrows(
                ActenoraException.class,
                () -> controller.dispatchNotifications(new GraphNotificationBatch(List.of()))
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
    void subscriptionSpecificClientStateOverridesGlobalSecret() {
        UUID tenantId = UUID.randomUUID();
        subscriptionStore.save(new GraphSubscription(
                tenantId,
                "sub-1",
                "users/organizer@contoso.com/events",
                "created,updated",
                "https://notifications.example.test/graph",
                "subscription-secret",
                Instant.now().plus(Duration.ofHours(12)),
                "app-id"));

        GraphWebhookResultView globalSecret =
                process(new GraphNotificationBatch(List.of(changeItem(CLIENT_STATE))));
        GraphWebhookResultView subscriptionSecret =
                process(new GraphNotificationBatch(List.of(changeItem("subscription-secret"))));

        assertEquals(1, globalSecret.rejected());
        assertEquals(1, subscriptionSecret.processed());
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
                buildProcessor(),
                new InMemorySubscriptionStore(),
                emptyProvider(GraphObservability.class),
                JsonMapper.builder().findAndAddModules().build(),
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

    private static ObjectProvider<com.nanobaseai.actenora.sharedkernel.messaging.port.OutboxPublisher> outboxProvider() {
        StaticListableBeanFactory factory = new StaticListableBeanFactory();
        factory.addBean(
                "outboxPublisher",
                org.mockito.Mockito.mock(
                        com.nanobaseai.actenora.sharedkernel.messaging.port.OutboxPublisher.class));
        return factory.getBeanProvider(
                com.nanobaseai.actenora.sharedkernel.messaging.port.OutboxPublisher.class);
    }

    private static <T> ObjectProvider<T> emptyProvider(Class<T> type) {
        DefaultListableBeanFactory factory = new DefaultListableBeanFactory();
        return factory.getBeanProvider(type);
    }

    private GraphWebhookResultView process(MicrosoftGraphWebhookController target, GraphNotificationBatch batch) {
        ResponseEntity<?> response = target.dispatchNotifications(batch);
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

        @Override
        public void delete(UUID tenantId, String userId) {
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

        @Override
        public void enableTranscription(UUID tenantId, String userId, String meetingId) {
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
