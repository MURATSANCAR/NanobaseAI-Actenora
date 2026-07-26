package com.nanobaseai.actenora.security.microsoftconnection;

import com.nanobaseai.actenora.meeting.api.MeetingApi;
import com.nanobaseai.actenora.meeting.infrastructure.tenancy.FixedTenantContext;
import com.nanobaseai.actenora.microsoftconnection.api.MicrosoftConnectionApi;
import com.nanobaseai.actenora.microsoftconnection.application.port.SubscriptionStore;
import com.nanobaseai.actenora.microsoftconnection.infrastructure.config.MicrosoftGraphSpringProperties;
import com.nanobaseai.actenora.transcript.api.TranscriptApi;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Instant;
import java.util.Objects;

/**
 * Platform wiring for Graph calendar → meeting upsert and Teams transcript polling.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "actenora.microsoft-graph.enabled", havingValue = "true")
public class MicrosoftConnectionPlatformConfiguration {

    @Bean
    CalendarMeetingUpsertAdapter calendarMeetingUpsertAdapter(
            MeetingApi meetingApi,
            FixedTenantContext fixedTenantContext
    ) {
        return new CalendarMeetingUpsertAdapter(meetingApi, fixedTenantContext);
    }

    @Bean
    TeamsTranscriptIngestService teamsTranscriptIngestService(
            MicrosoftConnectionApi microsoftConnectionApi,
            TranscriptApi transcriptApi,
            MeetingApi meetingApi,
            FixedTenantContext fixedTenantContext,
            MicrosoftGraphSpringProperties graphProperties
    ) {
        return new TeamsTranscriptIngestService(
                microsoftConnectionApi,
                transcriptApi,
                meetingApi,
                fixedTenantContext,
                graphProperties.getDefaultMailboxUserId()
        );
    }

    @Bean
    TeamsTranscriptPollScheduler teamsTranscriptPollScheduler(
            TeamsTranscriptIngestService teamsTranscriptIngestService,
            MeetingApi meetingApi,
            FixedTenantContext fixedTenantContext,
            SubscriptionStore subscriptionStore
    ) {
        return new TeamsTranscriptPollScheduler(
                teamsTranscriptIngestService,
                meetingApi,
                fixedTenantContext,
                subscriptionStore
        );
    }

    @Bean
    @ConditionalOnProperty(name = "actenora.microsoft-graph.workers-enabled", havingValue = "true", matchIfMissing = true)
    TeamsTranscriptPollScheduledWorker teamsTranscriptPollScheduledWorker(
            TeamsTranscriptPollScheduler scheduler
    ) {
        return new TeamsTranscriptPollScheduledWorker(scheduler);
    }

    static final class TeamsTranscriptPollScheduledWorker {

        private final TeamsTranscriptPollScheduler scheduler;

        TeamsTranscriptPollScheduledWorker(TeamsTranscriptPollScheduler scheduler) {
            this.scheduler = Objects.requireNonNull(scheduler);
        }

        @Scheduled(fixedDelayString = "${actenora.microsoft-graph.transcript-poll-interval:PT5M}")
        void pollFallback() {
            scheduler.runScheduledFallback(Instant.now());
        }
    }
}
