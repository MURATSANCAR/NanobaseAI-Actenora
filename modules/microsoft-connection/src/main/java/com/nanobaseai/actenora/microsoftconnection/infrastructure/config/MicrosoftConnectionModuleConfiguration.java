package com.nanobaseai.actenora.microsoftconnection.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nanobaseai.actenora.microsoftconnection.api.MicrosoftConnectionApi;
import com.nanobaseai.actenora.microsoftconnection.application.CalendarSyncService;
import com.nanobaseai.actenora.microsoftconnection.application.MeetingTranscriptService;
import com.nanobaseai.actenora.microsoftconnection.application.PollingFallbackService;
import com.nanobaseai.actenora.microsoftconnection.application.ReconciliationJob;
import com.nanobaseai.actenora.microsoftconnection.application.SubscriptionLifecycleService;
import com.nanobaseai.actenora.microsoftconnection.application.port.CalendarGateway;
import com.nanobaseai.actenora.microsoftconnection.application.port.GraphTelemetry;
import com.nanobaseai.actenora.microsoftconnection.application.port.CalendarSyncCursorStore;
import com.nanobaseai.actenora.microsoftconnection.application.port.MailGateway;
import com.nanobaseai.actenora.microsoftconnection.application.port.MicrosoftTokenProvider;
import com.nanobaseai.actenora.microsoftconnection.application.port.NotificationInbox;
import com.nanobaseai.actenora.microsoftconnection.application.port.OnlineMeetingGateway;
import com.nanobaseai.actenora.microsoftconnection.application.port.SubscriptionGateway;
import com.nanobaseai.actenora.microsoftconnection.application.port.SubscriptionStore;
import com.nanobaseai.actenora.microsoftconnection.application.port.TranscriptGateway;
import com.nanobaseai.actenora.microsoftconnection.infrastructure.auth.CertificateCredential;
import com.nanobaseai.actenora.microsoftconnection.infrastructure.auth.CertificateMicrosoftTokenProvider;
import com.nanobaseai.actenora.microsoftconnection.infrastructure.auth.ClientSecretCredential;
import com.nanobaseai.actenora.microsoftconnection.infrastructure.auth.ClientSecretMicrosoftTokenProvider;
import com.nanobaseai.actenora.microsoftconnection.infrastructure.auth.PemCredentialsLoader;
import com.nanobaseai.actenora.microsoftconnection.infrastructure.graph.GraphCalendarGateway;
import com.nanobaseai.actenora.microsoftconnection.infrastructure.graph.GraphHttpClient;
import com.nanobaseai.actenora.microsoftconnection.infrastructure.graph.GraphEgressPolicy;
import com.nanobaseai.actenora.microsoftconnection.infrastructure.graph.GraphMailGateway;
import com.nanobaseai.actenora.microsoftconnection.infrastructure.graph.GraphOnlineMeetingGateway;
import com.nanobaseai.actenora.microsoftconnection.infrastructure.graph.GraphSleeper;
import com.nanobaseai.actenora.microsoftconnection.infrastructure.graph.GraphSubscriptionGateway;
import com.nanobaseai.actenora.microsoftconnection.infrastructure.graph.GraphTranscriptGateway;
import com.nanobaseai.actenora.microsoftconnection.infrastructure.mail.RateLimitedMailGateway;
import com.nanobaseai.actenora.microsoftconnection.infrastructure.notification.InMemoryNotificationInbox;
import com.nanobaseai.actenora.microsoftconnection.infrastructure.persistence.InMemoryCalendarSyncCursorStore;
import com.nanobaseai.actenora.microsoftconnection.infrastructure.persistence.InMemorySubscriptionStore;
import com.nanobaseai.actenora.sharedkernel.messaging.ExponentialBackoff;
import com.nanobaseai.actenora.sharedkernel.time.InstantClock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
@EnableConfigurationProperties(MicrosoftGraphSpringProperties.class)
@ConditionalOnProperty(name = "actenora.microsoft-graph.enabled", havingValue = "true", matchIfMissing = false)
public class MicrosoftConnectionModuleConfiguration {

    @Bean
    @ConditionalOnMissingBean(InstantClock.class)
    InstantClock microsoftInstantClock() {
        return InstantClock.systemUTC();
    }

    @Bean
    @ConditionalOnMissingBean(ObjectMapper.class)
    ObjectMapper microsoftObjectMapper() {
        return new ObjectMapper();
    }

    @Bean
    @ConditionalOnMissingBean(MicrosoftGraphProperties.class)
    MicrosoftGraphProperties microsoftGraphProperties(MicrosoftGraphSpringProperties spring) {
        return spring.toProperties();
    }

    @Bean
    @ConditionalOnMissingBean(MicrosoftTokenProvider.class)
    MicrosoftTokenProvider microsoftTokenProvider(MicrosoftGraphProperties props, InstantClock clock) {
        if (props.authMode() == MicrosoftGraphProperties.AuthMode.CERTIFICATE) {
            var material = PemCredentialsLoader.load(
                    props.certificatePemPath().orElseThrow(),
                    props.privateKeyPemPath().orElseThrow()
            );
            CertificateCredential credential = new CertificateCredential(
                    props.tenantId(),
                    props.clientId(),
                    props.authorityHost().toString(),
                    material.certificate(),
                    material.privateKey(),
                    props.scope()
            );
            return new CertificateMicrosoftTokenProvider(credential, clock);
        }
        ClientSecretCredential credential = new ClientSecretCredential(
                props.tenantId(),
                props.clientId(),
                props.clientSecret().orElseThrow(() -> new IllegalStateException(
                        "Client secret required when auth-mode=CLIENT_SECRET (local/test only)")),
                props.authorityHost().toString(),
                props.scope()
        );
        return new ClientSecretMicrosoftTokenProvider(credential, clock);
    }

    @Bean
    @ConditionalOnMissingBean(GraphHttpClient.class)
    GraphHttpClient graphHttpClient(
            MicrosoftGraphProperties props,
            MicrosoftTokenProvider tokenProvider,
            ObjectProvider<GraphTelemetry> telemetry
    ) {
        return new GraphHttpClient(
                props.graphBaseUrl(),
                tokenProvider,
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(),
                GraphSleeper.THREAD,
                ExponentialBackoff.defaults(),
                5,
                3,
                GraphEgressPolicy.defaults(),
                telemetry.getIfAvailable(() -> GraphTelemetry.NOOP)
        );
    }

    @Bean
    @ConditionalOnMissingBean(CalendarSyncCursorStore.class)
    CalendarSyncCursorStore calendarSyncCursorStore() {
        return new InMemoryCalendarSyncCursorStore();
    }

    @Bean
    @ConditionalOnMissingBean(SubscriptionStore.class)
    SubscriptionStore subscriptionStore() {
        return new InMemorySubscriptionStore();
    }

    @Bean
    @ConditionalOnMissingBean(NotificationInbox.class)
    NotificationInbox notificationInbox() {
        return new InMemoryNotificationInbox();
    }

    @Bean
    CalendarGateway calendarGateway(GraphHttpClient http, ObjectMapper mapper) {
        return new GraphCalendarGateway(http, mapper);
    }

    @Bean
    OnlineMeetingGateway onlineMeetingGateway(GraphHttpClient http, ObjectMapper mapper) {
        return new GraphOnlineMeetingGateway(http, mapper);
    }

    @Bean
    TranscriptGateway transcriptGateway(GraphHttpClient http, ObjectMapper mapper) {
        return new GraphTranscriptGateway(http, mapper);
    }

    @Bean
    SubscriptionGateway subscriptionGateway(GraphHttpClient http, ObjectMapper mapper, InstantClock clock) {
        return new GraphSubscriptionGateway(http, mapper, clock);
    }

    @Bean
    MailGateway mailGateway(
            GraphHttpClient http,
            ObjectMapper mapper,
            @Value("${actenora.microsoft-graph.mail.max-per-window:100}") int maxPerWindow,
            @Value("${actenora.microsoft-graph.mail.window:PT1M}") Duration window
    ) {
        return new RateLimitedMailGateway(
                new GraphMailGateway(http, mapper),
                maxPerWindow,
                window);
    }

    @Bean
    CalendarSyncService calendarSyncService(
            CalendarGateway calendarGateway,
            CalendarSyncCursorStore cursorStore,
            InstantClock clock
    ) {
        return new CalendarSyncService(calendarGateway, cursorStore, clock);
    }

    @Bean
    MeetingTranscriptService meetingTranscriptService(
            OnlineMeetingGateway onlineMeetingGateway,
            TranscriptGateway transcriptGateway
    ) {
        return new MeetingTranscriptService(onlineMeetingGateway, transcriptGateway);
    }

    @Bean
    SubscriptionLifecycleService subscriptionLifecycleService(
            SubscriptionGateway subscriptionGateway,
            SubscriptionStore subscriptionStore,
            NotificationInbox notificationInbox,
            InstantClock clock,
            MicrosoftGraphProperties props
    ) {
        return new SubscriptionLifecycleService(
                subscriptionGateway,
                subscriptionStore,
                notificationInbox,
                clock,
                props.subscriptionRenewBefore(),
                props.subscriptionRenewWindow()
        );
    }

    @Bean
    PollingFallbackService pollingFallbackService(CalendarSyncService calendarSyncService) {
        return new PollingFallbackService(calendarSyncService);
    }

    @Bean
    ReconciliationJob reconciliationJob(
            SubscriptionLifecycleService subscriptionLifecycleService,
            PollingFallbackService pollingFallbackService
    ) {
        return new ReconciliationJob(subscriptionLifecycleService, pollingFallbackService);
    }

    @Bean
    MicrosoftConnectionApi microsoftConnectionApi(
            CalendarSyncService calendarSyncService,
            MeetingTranscriptService meetingTranscriptService,
            SubscriptionLifecycleService subscriptionLifecycleService,
            PollingFallbackService pollingFallbackService,
            ReconciliationJob reconciliationJob,
            MailGateway mailGateway,
            SubscriptionStore subscriptionStore
    ) {
        return new MicrosoftConnectionApi(
                calendarSyncService,
                meetingTranscriptService,
                subscriptionLifecycleService,
                pollingFallbackService,
                reconciliationJob,
                mailGateway,
                subscriptionStore
        );
    }
}
