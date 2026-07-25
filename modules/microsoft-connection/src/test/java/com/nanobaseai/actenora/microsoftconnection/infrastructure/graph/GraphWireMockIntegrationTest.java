package com.nanobaseai.actenora.microsoftconnection.infrastructure.graph;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.nanobaseai.actenora.microsoftconnection.application.CalendarSyncService;
import com.nanobaseai.actenora.microsoftconnection.application.MeetingTranscriptService;
import com.nanobaseai.actenora.microsoftconnection.application.ReconciliationJob;
import com.nanobaseai.actenora.microsoftconnection.application.SubscriptionLifecycleService;
import com.nanobaseai.actenora.microsoftconnection.application.model.CalendarEvent;
import com.nanobaseai.actenora.microsoftconnection.application.model.GraphChangeNotification;
import com.nanobaseai.actenora.microsoftconnection.application.model.GraphSubscription;
import com.nanobaseai.actenora.microsoftconnection.application.model.SubscriptionCreateRequest;
import com.nanobaseai.actenora.microsoftconnection.application.model.TranscriptAvailability;
import com.nanobaseai.actenora.microsoftconnection.application.model.TranscriptContent;
import com.nanobaseai.actenora.microsoftconnection.application.port.MicrosoftTokenProvider;
import com.nanobaseai.actenora.microsoftconnection.domain.identity.SeriesOccurrenceKind;
import com.nanobaseai.actenora.microsoftconnection.infrastructure.auth.ClientSecretCredential;
import com.nanobaseai.actenora.microsoftconnection.infrastructure.auth.ClientSecretMicrosoftTokenProvider;
import com.nanobaseai.actenora.microsoftconnection.infrastructure.notification.InMemoryNotificationInbox;
import com.nanobaseai.actenora.microsoftconnection.infrastructure.persistence.InMemoryCalendarSyncCursorStore;
import com.nanobaseai.actenora.microsoftconnection.infrastructure.persistence.InMemorySubscriptionStore;
import com.nanobaseai.actenora.sharedkernel.messaging.ExponentialBackoff;
import com.nanobaseai.actenora.sharedkernel.time.InstantClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.patch;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GraphWireMockIntegrationTest {

    @RegisterExtension
    static WireMockExtension wm = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    private final InstantClock clock = InstantClock.systemUTC();
    private final ObjectMapper mapper = new ObjectMapper();
    private final UUID tenantId = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private final String userId = "user-1";

    private MicrosoftTokenProvider tokenProvider;
    private GraphHttpClient http;

    @BeforeEach
    void setUp() {
        wm.resetAll();
        stubTokenSuccess("token-1");
        ClientSecretCredential credential = new ClientSecretCredential(
                "tenant-x",
                "client-x",
                "secret-x",
                wm.baseUrl(),
                "https://graph.microsoft.com/.default"
        );
        tokenProvider = new ClientSecretMicrosoftTokenProvider(credential, clock);
        http = new GraphHttpClient(
                URI.create(wm.baseUrl()),
                tokenProvider,
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build(),
                GraphSleeper.NOOP,
                new ExponentialBackoff(Duration.ofMillis(1), Duration.ofMillis(5), 0.0),
                5,
                3,
                GraphEgressPolicy.localTesting()
        );
    }

    @Test
    void tokenFailure() {
        wm.resetAll();
        wm.stubFor(post(urlPathMatching("/.*/oauth2/v2.0/token"))
                .willReturn(aResponse().withStatus(500).withBody("{\"error\":\"server_error\"}")));
        assertThrows(GraphApiException.class, tokenProvider::getAccessToken);
    }

    @Test
    void unauthorizedThenRefreshSucceeds() {
        wm.stubFor(get(urlPathEqualTo("/v1.0/users/user-1/onlineMeetings/m1"))
                .inScenario("401")
                .whenScenarioStateIs("Started")
                .willSetStateTo("refreshed")
                .willReturn(aResponse().withStatus(401).withBody("{\"error\":\"invalid\"}")));
        wm.stubFor(get(urlPathEqualTo("/v1.0/users/user-1/onlineMeetings/m1"))
                .inScenario("401")
                .whenScenarioStateIs("refreshed")
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"id\":\"m1\",\"joinWebUrl\":\"https://teams.example/join\",\"subject\":\"Sync\"}")));

        GraphOnlineMeetingGateway gateway = new GraphOnlineMeetingGateway(http, mapper);
        assertEquals("m1", gateway.getByMeetingId(tenantId, userId, "m1").orElseThrow().meetingId());
        wm.verify(2, postRequestedFor(urlPathMatching("/.*/oauth2/v2.0/token")));
    }

    @Test
    void configurationErrorOn403() {
        wm.stubFor(get(urlPathEqualTo("/v1.0/users/user-1/onlineMeetings/m1"))
                .willReturn(aResponse().withStatus(403).withBody("{\"error\":{\"code\":\"Authorization_RequestDenied\"}}")));
        GraphOnlineMeetingGateway gateway = new GraphOnlineMeetingGateway(http, mapper);
        GraphApiException ex = assertThrows(
                GraphApiException.class,
                () -> gateway.getByMeetingId(tenantId, userId, "m1")
        );
        assertEquals(GraphApiException.CODE_CONFIGURATION, ex.code());
        assertFalse(ex.retryable());
    }

    @Test
    void notFoundThenSuccessForDelayedTranscript() {
        wm.stubFor(get(urlPathEqualTo("/v1.0/users/user-1/onlineMeetings/m1/transcripts"))
                .inScenario("transcript")
                .whenScenarioStateIs("Started")
                .willSetStateTo("ready")
                .willReturn(aResponse().withStatus(404)));
        wm.stubFor(get(urlPathEqualTo("/v1.0/users/user-1/onlineMeetings/m1/transcripts"))
                .inScenario("transcript")
                .whenScenarioStateIs("ready")
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"value\":[{\"id\":\"t1\",\"createdDateTime\":\"2026-07-25T10:00:00Z\"}]}")));

        GraphTranscriptGateway gateway = new GraphTranscriptGateway(http, mapper);
        TranscriptAvailability availability = gateway.checkAvailability(tenantId, userId, "m1");
        assertTrue(availability.available());
        assertEquals("t1", availability.firstTranscript().orElseThrow().transcriptId());
    }

    @Test
    void rateLimitedHonorsRetryAfter() {
        wm.stubFor(get(urlPathEqualTo("/v1.0/users/user-1/onlineMeetings/m1"))
                .inScenario("429")
                .whenScenarioStateIs("Started")
                .willSetStateTo("ok")
                .willReturn(aResponse().withStatus(429).withHeader("Retry-After", "0")));
        wm.stubFor(get(urlPathEqualTo("/v1.0/users/user-1/onlineMeetings/m1"))
                .inScenario("429")
                .whenScenarioStateIs("ok")
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"id\":\"m1\",\"subject\":\"Ok\"}")));

        GraphOnlineMeetingGateway gateway = new GraphOnlineMeetingGateway(http, mapper);
        assertEquals("m1", gateway.getByMeetingId(tenantId, userId, "m1").orElseThrow().meetingId());
    }

    @Test
    void serverErrorBackoffThenSuccess() {
        wm.stubFor(get(urlPathEqualTo("/v1.0/users/user-1/onlineMeetings/m1"))
                .inScenario("500")
                .whenScenarioStateIs("Started")
                .willSetStateTo("ok")
                .willReturn(aResponse().withStatus(500).withBody("boom")));
        wm.stubFor(get(urlPathEqualTo("/v1.0/users/user-1/onlineMeetings/m1"))
                .inScenario("500")
                .whenScenarioStateIs("ok")
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"id\":\"m1\",\"subject\":\"Recovered\"}")));

        GraphOnlineMeetingGateway gateway = new GraphOnlineMeetingGateway(http, mapper);
        assertEquals("Recovered", gateway.getByMeetingId(tenantId, userId, "m1").orElseThrow().subject());
    }

    @Test
    void duplicateNotificationIdempotency() {
        SubscriptionLifecycleService lifecycle = new SubscriptionLifecycleService(
                new GraphSubscriptionGateway(http, mapper, clock),
                new InMemorySubscriptionStore(),
                new InMemoryNotificationInbox(),
                clock,
                Duration.ofHours(1),
                Duration.ofHours(24)
        );
        GraphChangeNotification notification = new GraphChangeNotification(
                "notif-1", "sub-1", "updated", "users/user-1/events/e1", "e1", "state", tenantId.toString()
        );
        AtomicInteger handled = new AtomicInteger();
        assertTrue(lifecycle.handleChangeNotification(notification, n -> handled.incrementAndGet()));
        assertFalse(lifecycle.handleChangeNotification(notification, n -> handled.incrementAndGet()));
        assertEquals(1, handled.get());
    }

    @Test
    void subscriptionCreateAndRenew() {
        wm.stubFor(post(urlEqualTo("/v1.0/subscriptions"))
                .willReturn(aResponse()
                        .withStatus(201)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "id":"sub-42",
                                  "resource":"users/user-1/events",
                                  "changeType":"created,updated",
                                  "notificationUrl":"https://app.example/hooks/graph",
                                  "clientState":"cs",
                                  "expirationDateTime":"2026-07-26T00:00:00Z",
                                  "applicationId":"app-1"
                                }
                                """)));
        Instant renewedExp = Instant.parse("2026-07-28T00:00:00Z");
        wm.stubFor(patch(urlEqualTo("/v1.0/subscriptions/sub-42"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "id":"sub-42",
                                  "resource":"users/user-1/events",
                                  "changeType":"created,updated",
                                  "notificationUrl":"https://app.example/hooks/graph",
                                  "clientState":"cs",
                                  "expirationDateTime":"%s",
                                  "applicationId":"app-1"
                                }
                                """.formatted(renewedExp))));

        InMemorySubscriptionStore store = new InMemorySubscriptionStore();
        SubscriptionLifecycleService lifecycle = new SubscriptionLifecycleService(
                new GraphSubscriptionGateway(http, mapper, clock),
                store,
                new InMemoryNotificationInbox(),
                clock,
                Duration.ofDays(30),
                Duration.ofHours(48)
        );
        GraphSubscription created = lifecycle.create(tenantId, new SubscriptionCreateRequest(
                "users/user-1/events",
                "created,updated",
                "https://app.example/hooks/graph",
                "https://app.example/hooks/lifecycle",
                "cs",
                Duration.ofHours(48)
        ));
        assertEquals("sub-42", created.subscriptionId());
        store.save(created.withExpiration(clock.now().plusSeconds(60)));
        List<GraphSubscription> renewed = lifecycle.renewExpiring();
        assertEquals(1, renewed.size());
        assertEquals(renewedExp, renewed.getFirst().expirationDateTime());
    }

    @Test
    void recurringSeriesMasterAndOccurrenceResolution() {
        wm.stubFor(get(urlPathMatching("/v1.0/users/user-1/calendarView/delta.*"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "value":[
                                    {
                                      "id":"master-1",
                                      "iCalUId":"ical-series",
                                      "type":"seriesMaster",
                                      "subject":"Weekly",
                                      "start":{"dateTime":"2026-07-08T09:00:00","timeZone":"UTC"},
                                      "end":{"dateTime":"2026-07-08T10:00:00","timeZone":"UTC"},
                                      "onlineMeeting":{"joinUrl":"https://teams.example/join"}
                                    },
                                    {
                                      "id":"occ-1",
                                      "iCalUId":"ical-series",
                                      "seriesMasterId":"master-1",
                                      "type":"occurrence",
                                      "originalStart":"2026-07-15T09:00:00Z",
                                      "subject":"Weekly",
                                      "start":{"dateTime":"2026-07-15T09:00:00","timeZone":"UTC"},
                                      "end":{"dateTime":"2026-07-15T10:00:00","timeZone":"UTC"},
                                      "onlineMeeting":{"joinUrl":"https://teams.example/join"}
                                    }
                                  ],
                                  "@odata.deltaLink":"https://graph.example/delta?token=abc"
                                }
                                """)));

        CalendarSyncService sync = new CalendarSyncService(
                new GraphCalendarGateway(http, mapper),
                new InMemoryCalendarSyncCursorStore(),
                clock
        );
        List<CalendarEvent> events = sync.syncMailbox(tenantId, userId);
        assertEquals(2, events.size());
        assertEquals(SeriesOccurrenceKind.SERIES_MASTER, events.get(0).occurrenceKind());
        assertEquals(SeriesOccurrenceKind.OCCURRENCE, events.get(1).occurrenceKind());
        assertEquals("master-1", events.get(1).seriesMasterId());
        assertEquals("occ-1", events.get(1).immutableIdentity().graphEventImmutableId());
        assertTrue(events.get(1).joinWebUrlOptional().isPresent());
    }

    @Test
    void transcriptDelayedThenDownload() {
        wm.stubFor(get(urlPathEqualTo("/v1.0/users/user-1/onlineMeetings/m1/transcripts"))
                .inScenario("delayed")
                .whenScenarioStateIs("Started")
                .willSetStateTo("listed")
                .willReturn(aResponse().withStatus(404)));
        wm.stubFor(get(urlPathEqualTo("/v1.0/users/user-1/onlineMeetings/m1/transcripts"))
                .inScenario("delayed")
                .whenScenarioStateIs("listed")
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"value\":[{\"id\":\"t-late\",\"createdDateTime\":\"2026-07-25T12:00:00Z\"}]}")));
        wm.stubFor(get(urlPathEqualTo("/v1.0/users/user-1/onlineMeetings/m1/transcripts/t-late/content"))
                .withQueryParam("$format", equalTo("text/vtt"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/vtt")
                        .withBody("WEBVTT\n\n00:00:00.000 --> 00:00:01.000\nHello")));

        MeetingTranscriptService service = new MeetingTranscriptService(
                new GraphOnlineMeetingGateway(http, mapper),
                new GraphTranscriptGateway(http, mapper)
        );
        TranscriptAvailability availability = service.transcriptAvailability(tenantId, userId, "m1");
        TranscriptContent content = service.downloadTranscript(
                tenantId, userId, "m1", availability.firstTranscript().orElseThrow().transcriptId()
        ).orElseThrow();
        assertTrue(content.bodyAsUtf8().contains("Hello"));
    }

    @Test
    void reconciliationJobRenewsAndPolls() {
        wm.stubFor(post(urlEqualTo("/v1.0/subscriptions"))
                .willReturn(aResponse()
                        .withStatus(201)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"id":"sub-r","resource":"users/user-1/events","changeType":"updated",
                                 "notificationUrl":"https://app.example/h","expirationDateTime":"2026-07-26T00:00:00Z"}
                                """)));
        wm.stubFor(patch(urlEqualTo("/v1.0/subscriptions/sub-r"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"id":"sub-r","resource":"users/user-1/events","changeType":"updated",
                                 "notificationUrl":"https://app.example/h","expirationDateTime":"2026-07-29T00:00:00Z"}
                                """)));
        wm.stubFor(get(urlPathMatching("/v1.0/users/user-1/calendarView/delta.*"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"value":[{"id":"e1","type":"singleInstance","subject":"One",
                                  "start":{"dateTime":"2026-07-25T09:00:00","timeZone":"UTC"},
                                  "end":{"dateTime":"2026-07-25T10:00:00","timeZone":"UTC"}}],
                                 "@odata.deltaLink":"https://graph.example/delta?token=z"}
                                """)));

        InMemorySubscriptionStore store = new InMemorySubscriptionStore();
        SubscriptionLifecycleService lifecycle = new SubscriptionLifecycleService(
                new GraphSubscriptionGateway(http, mapper, clock),
                store,
                new InMemoryNotificationInbox(),
                clock,
                Duration.ofDays(30),
                Duration.ofHours(48)
        );
        GraphSubscription created = lifecycle.create(tenantId, new SubscriptionCreateRequest(
                "users/user-1/events",
                "updated",
                "https://app.example/h",
                null,
                null,
                Duration.ofHours(1)
        ));
        store.save(created.withExpiration(clock.now().plusSeconds(30)));

        CalendarSyncService sync = new CalendarSyncService(
                new GraphCalendarGateway(http, mapper),
                new InMemoryCalendarSyncCursorStore(),
                clock
        );
        ReconciliationJob job = new ReconciliationJob(
                lifecycle,
                new com.nanobaseai.actenora.microsoftconnection.application.PollingFallbackService(sync)
        );
        ReconciliationJob.ReconciliationResult result = job.run(List.of(
                new com.nanobaseai.actenora.microsoftconnection.application.PollingFallbackService.MailboxRef(tenantId, userId)
        ));
        assertEquals(1, result.subscriptionsRenewed());
        assertEquals(1, result.eventsPolled());
    }

    private void stubTokenSuccess(String token) {
        wm.stubFor(post(urlPathMatching("/.*/oauth2/v2.0/token"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"access_token\":\"" + token + "\",\"expires_in\":3600,\"token_type\":\"Bearer\"}")));
    }
}
