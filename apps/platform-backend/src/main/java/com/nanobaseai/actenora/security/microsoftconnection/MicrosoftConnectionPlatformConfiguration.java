package com.nanobaseai.actenora.security.microsoftconnection;

import com.nanobaseai.actenora.meeting.api.MeetingApi;
import com.nanobaseai.actenora.meeting.application.port.MeetingOccurrenceRepository;
import com.nanobaseai.actenora.meeting.infrastructure.tenancy.FixedTenantContext;
import com.nanobaseai.actenora.meetingintelligence.api.ledger.ContinuityLedgerApi;
import com.nanobaseai.actenora.microsoftconnection.api.MicrosoftConnectionApi;
import com.nanobaseai.actenora.microsoftconnection.application.PollingFallbackService;
import com.nanobaseai.actenora.microsoftconnection.application.port.SubscriptionStore;
import com.nanobaseai.actenora.microsoftconnection.infrastructure.config.MicrosoftGraphSpringProperties;
import com.nanobaseai.actenora.sharedkernel.messaging.ExponentialBackoff;
import com.nanobaseai.actenora.transcript.api.TranscriptApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Platform wiring for Graph calendar → meeting upsert and Teams transcript polling.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "actenora.microsoft-graph.enabled", havingValue = "true")
public class MicrosoftConnectionPlatformConfiguration {

    @Bean
    GraphMailboxSyncService graphMailboxSyncService(
            MicrosoftConnectionApi microsoftConnectionApi,
            CalendarMeetingUpsertAdapter calendarMeetingUpsertAdapter,
            com.nanobaseai.actenora.microsoftconnection.application.port.CalendarSyncCursorStore calendarSyncCursorStore
    ) {
        return new GraphMailboxSyncService(microsoftConnectionApi, calendarMeetingUpsertAdapter, calendarSyncCursorStore);
    }

    @Bean
    GraphSubscribedMailboxResolver graphSubscribedMailboxResolver(
            SubscriptionStore subscriptionStore,
            MicrosoftGraphSpringProperties graphProperties
    ) {
        return new GraphSubscribedMailboxResolver(subscriptionStore, graphProperties);
    }

    @Bean
    GraphMailboxSyncRunner graphMailboxSyncRunner(
            GraphSubscribedMailboxResolver graphSubscribedMailboxResolver,
            GraphMailboxSyncService graphMailboxSyncService
    ) {
        return new GraphMailboxSyncRunner(graphSubscribedMailboxResolver, graphMailboxSyncService);
    }

    @Bean
    CalendarMeetingUpsertAdapter calendarMeetingUpsertAdapter(
            MeetingApi meetingApi,
            FixedTenantContext fixedTenantContext,
            ContinuityLedgerApi continuityLedgerApi,
            MeetingOccurrenceRepository meetingOccurrenceRepository
    ) {
        return new CalendarMeetingUpsertAdapter(
                meetingApi, fixedTenantContext, continuityLedgerApi, meetingOccurrenceRepository);
    }

    @Bean
    MeetingAttendanceSyncService meetingAttendanceSyncService(
            MicrosoftConnectionApi microsoftConnectionApi,
            MeetingApi meetingApi
    ) {
        return new MeetingAttendanceSyncService(microsoftConnectionApi, meetingApi);
    }

    @Bean
    TeamsTranscriptIngestService teamsTranscriptIngestService(
            MicrosoftConnectionApi microsoftConnectionApi,
            @Lazy TranscriptApi transcriptApi,
            MeetingApi meetingApi,
            FixedTenantContext fixedTenantContext,
            SubscriptionStore subscriptionStore,
            MicrosoftGraphSpringProperties graphProperties,
            MeetingAttendanceSyncService meetingAttendanceSyncService
    ) {
        return new TeamsTranscriptIngestService(
                microsoftConnectionApi,
                transcriptApi,
                meetingApi,
                fixedTenantContext,
                subscriptionStore,
                graphProperties.getDefaultMailboxUserId(),
                meetingAttendanceSyncService
        );
    }

    @Bean
    @Lazy
    TeamsTranscriptPollScheduler teamsTranscriptPollScheduler(
            TeamsTranscriptIngestService teamsTranscriptIngestService,
            MeetingApi meetingApi,
            FixedTenantContext fixedTenantContext,
            SubscriptionStore subscriptionStore,
            @Lazy TranscriptApi transcriptApi,
            TranscriptPollWorkStore workStore,
            GraphObservability observability,
            @Value("${actenora.microsoft-graph.transcript-poll-max-attempts:24}") int maxAttempts,
            @Value("${actenora.microsoft-graph.transcript-poll-max-age:PT48H}") Duration maxAge,
            @Value("${actenora.microsoft-graph.transcript-poll-stale-claim:PT15M}") Duration staleClaim,
            @Value("${actenora.microsoft-graph.transcript-poll-batch-size:50}") int batchSize,
            @Value("${actenora.microsoft-graph.transcript-poll-backoff-base:PT1M}") Duration backoffBase,
            @Value("${actenora.microsoft-graph.transcript-poll-backoff-cap:PT1H}") Duration backoffCap
    ) {
        return new TeamsTranscriptPollScheduler(
                teamsTranscriptIngestService,
                meetingApi,
                fixedTenantContext,
                subscriptionStore,
                transcriptApi,
                workStore,
                new ExponentialBackoff(backoffBase, backoffCap),
                maxAttempts,
                maxAge,
                staleClaim,
                batchSize,
                observability
        );
    }

    @Bean
    @ConditionalOnProperty(name = "actenora.persistence.mode", havingValue = "jdbc")
    TranscriptPollWorkStore jdbcTranscriptPollWorkStore(JdbcTemplate jdbcTemplate) {
        return new JdbcTranscriptPollWorkStore(jdbcTemplate);
    }

    @Bean
    @ConditionalOnProperty(
            name = "actenora.persistence.mode",
            havingValue = "inmemory",
            matchIfMissing = true)
    TranscriptPollWorkStore inMemoryTranscriptPollWorkStore() {
        return new InMemoryTranscriptPollWorkStore();
    }

    @Bean
    @ConditionalOnProperty(name = "actenora.persistence.mode", havingValue = "jdbc")
    GraphWorkerLeaseStore jdbcGraphWorkerLeaseStore(JdbcTemplate jdbcTemplate) {
        return new JdbcGraphWorkerLeaseStore(jdbcTemplate);
    }

    @Bean
    @ConditionalOnProperty(
            name = "actenora.persistence.mode",
            havingValue = "inmemory",
            matchIfMissing = true)
    GraphWorkerLeaseStore inMemoryGraphWorkerLeaseStore() {
        return new InMemoryGraphWorkerLeaseStore();
    }

    // Eager: @Scheduled is only registered after bean creation; @Lazy left these workers idle.
    @Bean
    @ConditionalOnProperty(name = "actenora.microsoft-graph.workers-enabled", havingValue = "true", matchIfMissing = true)
    TeamsTranscriptPollScheduledWorker teamsTranscriptPollScheduledWorker(
            TeamsTranscriptPollScheduler scheduler,
            GraphWorkerLeaseStore leaseStore
    ) {
        return new TeamsTranscriptPollScheduledWorker(scheduler, leaseStore, UUID.randomUUID().toString());
    }

    @Bean
    @ConditionalOnExpression("'${actenora.microsoft-graph.workers-enabled:true}' == 'true' && '${actenora.microsoft-graph.mailbox-sync-enabled:true}' == 'true'")
    GraphPeriodicMailboxSyncWorker graphPeriodicMailboxSyncWorker(
            GraphMailboxSyncRunner graphMailboxSyncRunner,
            GraphWorkerLeaseStore leaseStore,
            @Value("${actenora.microsoft-graph.mailbox-sync-recover-empty-delta:true}") boolean recoverEmptyDelta
    ) {
        return new GraphPeriodicMailboxSyncWorker(
                graphMailboxSyncRunner,
                leaseStore,
                recoverEmptyDelta,
                UUID.randomUUID().toString());
    }

    @Bean
    @ConditionalOnProperty(name = "actenora.microsoft-graph.workers-enabled", havingValue = "true", matchIfMissing = true)
    GraphReconciliationScheduledWorker graphReconciliationScheduledWorker(
            MicrosoftConnectionApi api,
            SubscriptionStore subscriptionStore,
            GraphWorkerLeaseStore leaseStore,
            GraphObservability observability,
            CalendarMeetingUpsertAdapter calendarMeetingUpsertAdapter
    ) {
        return new GraphReconciliationScheduledWorker(
                api,
                subscriptionStore,
                leaseStore,
                observability,
                calendarMeetingUpsertAdapter,
                UUID.randomUUID().toString());
    }

    static final class TeamsTranscriptPollScheduledWorker {

        private final TeamsTranscriptPollScheduler scheduler;
        private final GraphWorkerLeaseStore leaseStore;
        private final String ownerId;

        TeamsTranscriptPollScheduledWorker(
                TeamsTranscriptPollScheduler scheduler,
                GraphWorkerLeaseStore leaseStore,
                String ownerId
        ) {
            this.scheduler = Objects.requireNonNull(scheduler);
            this.leaseStore = Objects.requireNonNull(leaseStore);
            this.ownerId = Objects.requireNonNull(ownerId);
        }

        @Scheduled(fixedDelayString = "${actenora.microsoft-graph.transcript-poll-interval:PT5M}")
        void pollFallback() {
            Instant now = Instant.now();
            if (!leaseStore.tryAcquire("teams-transcript-poll", ownerId, now, Duration.ofMinutes(14))) {
                return;
            }
            try {
                scheduler.runScheduledFallback(now);
            } finally {
                leaseStore.release("teams-transcript-poll", ownerId, Instant.now());
            }
        }
    }

    static final class GraphPeriodicMailboxSyncWorker {

        private final GraphMailboxSyncRunner syncRunner;
        private final GraphWorkerLeaseStore leaseStore;
        private final boolean recoverEmptyDelta;
        private final String ownerId;

        GraphPeriodicMailboxSyncWorker(
                GraphMailboxSyncRunner syncRunner,
                GraphWorkerLeaseStore leaseStore,
                boolean recoverEmptyDelta,
                String ownerId
        ) {
            this.syncRunner = Objects.requireNonNull(syncRunner);
            this.leaseStore = Objects.requireNonNull(leaseStore);
            this.recoverEmptyDelta = recoverEmptyDelta;
            this.ownerId = Objects.requireNonNull(ownerId);
        }

        @Scheduled(fixedDelayString = "${actenora.microsoft-graph.mailbox-sync-interval:PT15M}")
        void syncMailboxes() {
            Instant now = Instant.now();
            if (!leaseStore.tryAcquire("graph-mailbox-sync", ownerId, now, Duration.ofMinutes(14))) {
                return;
            }
            try {
                syncRunner.syncAll("periodic", recoverEmptyDelta);
            } finally {
                leaseStore.release("graph-mailbox-sync", ownerId, Instant.now());
            }
        }
    }

    static final class GraphReconciliationScheduledWorker {

        private final MicrosoftConnectionApi api;
        private final SubscriptionStore subscriptionStore;
        private final GraphWorkerLeaseStore leaseStore;
        private final GraphObservability observability;
        private final CalendarMeetingUpsertAdapter calendarMeetingUpsertAdapter;
        private final String ownerId;

        GraphReconciliationScheduledWorker(
                MicrosoftConnectionApi api,
                SubscriptionStore subscriptionStore,
                GraphWorkerLeaseStore leaseStore,
                GraphObservability observability,
                CalendarMeetingUpsertAdapter calendarMeetingUpsertAdapter,
                String ownerId
        ) {
            this.api = Objects.requireNonNull(api);
            this.subscriptionStore = Objects.requireNonNull(subscriptionStore);
            this.leaseStore = Objects.requireNonNull(leaseStore);
            this.observability = Objects.requireNonNull(observability);
            this.calendarMeetingUpsertAdapter = Objects.requireNonNull(calendarMeetingUpsertAdapter);
            this.ownerId = Objects.requireNonNull(ownerId);
        }

        @Scheduled(fixedDelayString = "${actenora.microsoft-graph.reconcile-interval:PT30M}")
        void reconcile() {
            Instant now = Instant.now();
            if (!leaseStore.tryAcquire("graph-reconciliation", ownerId, now, Duration.ofMinutes(25))) {
                return;
            }
            try {
                var result = api.reconcile(
                        mailboxes(),
                        (mailbox, events) -> {
                            calendarMeetingUpsertAdapter.upsertEvents(
                                    com.nanobaseai.actenora.sharedkernel.domain.TenantId.of(mailbox.tenantId()),
                                    events);
                            api.ensureTranscriptionForCalendarEvents(
                                    mailbox.tenantId(), mailbox.userId(), events);
                        });
                observability.recordSubscriptionRenew(
                        result.subscriptionsRenewed(), result.subscriptionRenewFailures());
                observability.recordMailboxPoll(result.eventsPolled(), result.mailboxPollFailures());
                observability.recordReconciliation(true);
                observability.updateExpiringSubscriptions(
                        subscriptionStore.findExpiringBefore(Instant.now().plus(Duration.ofHours(6))).size());
            } catch (com.nanobaseai.actenora.microsoftconnection.application.ReconciliationJob.ReconciliationFailedException ex) {
                var result = ex.result();
                observability.recordSubscriptionRenew(
                        result.subscriptionsRenewed(), result.subscriptionRenewFailures());
                observability.recordMailboxPoll(result.eventsPolled(), result.mailboxPollFailures());
                observability.recordReconciliation(false);
                throw ex;
            } catch (RuntimeException ex) {
                observability.recordReconciliation(false);
                throw ex;
            } finally {
                leaseStore.release("graph-reconciliation", ownerId, Instant.now());
            }
        }

        private List<PollingFallbackService.MailboxRef> mailboxes() {
            Set<PollingFallbackService.MailboxRef> refs = new LinkedHashSet<>();
            for (UUID tenantId : subscriptionStore.distinctTenantIds()) {
                subscriptionStore.findAllForTenant(tenantId).forEach(subscription ->
                        GraphChangeNotificationProcessor.parseMailboxUserId(subscription.resource())
                                .ifPresent(userId -> refs.add(
                                        new PollingFallbackService.MailboxRef(tenantId, userId))));
            }
            return List.copyOf(refs);
        }
    }
}
