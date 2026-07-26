package com.nanobaseai.actenora.operations.config;

import com.nanobaseai.actenora.operations.api.OperationsApi;
import com.nanobaseai.actenora.operations.application.LegalHoldService;
import com.nanobaseai.actenora.operations.application.OperationsCenterService;
import com.nanobaseai.actenora.operations.application.RetentionJobService;
import com.nanobaseai.actenora.operations.application.port.LegalHoldRepository;
import com.nanobaseai.actenora.operations.application.port.OpsTelemetryPort;
import com.nanobaseai.actenora.operations.application.port.RetentionAuditSink;
import com.nanobaseai.actenora.operations.application.port.RetentionCandidateSource;
import com.nanobaseai.actenora.operations.application.port.RetentionDeleter;
import com.nanobaseai.actenora.operations.domain.AlertThresholds;
import com.nanobaseai.actenora.operations.infrastructure.InMemoryOpsTelemetryPort;
import com.nanobaseai.actenora.operations.infrastructure.persistence.InMemoryLegalHoldRepository;
import com.nanobaseai.actenora.operations.infrastructure.retention.InMemoryRetentionSupport;
import com.nanobaseai.actenora.sharedkernel.messaging.infrastructure.InMemoryDeadLetterStore;
import com.nanobaseai.actenora.sharedkernel.messaging.infrastructure.InMemoryInboxStore;
import com.nanobaseai.actenora.sharedkernel.messaging.infrastructure.InMemoryOutboxStore;
import com.nanobaseai.actenora.sharedkernel.messaging.port.DeadLetterStore;
import com.nanobaseai.actenora.sharedkernel.messaging.port.InboxStore;
import com.nanobaseai.actenora.sharedkernel.messaging.port.OutboxStore;
import com.nanobaseai.actenora.sharedkernel.messaging.replay.EventReplayer;
import com.nanobaseai.actenora.sharedkernel.messaging.support.TenantFairnessTracker;
import com.nanobaseai.actenora.sharedkernel.time.InstantClock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring wiring for FAZ 25 Operations Center and FAZ 27 retention.
 */
@Configuration
public class OperationsConfiguration {

    @Bean
    @ConditionalOnMissingBean(OpsTelemetryPort.class)
    OpsTelemetryPort opsTelemetryPort() {
        // Empty until a real telemetry adapter is wired — never preload demo metrics.
        return new InMemoryOpsTelemetryPort();
    }

    @Bean
    @ConditionalOnMissingBean(DeadLetterStore.class)
    DeadLetterStore deadLetterStore() {
        return new InMemoryDeadLetterStore();
    }

    @Bean
    @ConditionalOnMissingBean(OutboxStore.class)
    OutboxStore outboxStore() {
        return new InMemoryOutboxStore(new TenantFairnessTracker());
    }

    @Bean
    @ConditionalOnMissingBean(InboxStore.class)
    InboxStore inboxStore() {
        return new InMemoryInboxStore();
    }

    @Bean
    @ConditionalOnMissingBean(EventReplayer.class)
    EventReplayer eventReplayer(
            OutboxStore outboxStore,
            InboxStore inboxStore,
            DeadLetterStore deadLetterStore,
            InstantClock clock
    ) {
        return new EventReplayer(outboxStore, inboxStore, deadLetterStore, clock);
    }

    @Bean
    @ConditionalOnMissingBean(AlertThresholds.class)
    AlertThresholds alertThresholds() {
        return AlertThresholds.defaults();
    }

    @Bean
    @ConditionalOnMissingBean(InstantClock.class)
    InstantClock instantClock() {
        return InstantClock.systemUTC();
    }

    @Bean
    @ConditionalOnMissingBean(OperationsCenterService.class)
    OperationsCenterService operationsCenterService(
            OpsTelemetryPort telemetry,
            DeadLetterStore deadLetterStore,
            EventReplayer eventReplayer,
            AlertThresholds thresholds,
            InstantClock clock
    ) {
        return new OperationsCenterService(telemetry, deadLetterStore, eventReplayer, thresholds, clock);
    }

    @Bean
    @ConditionalOnMissingBean(InMemoryRetentionSupport.class)
    InMemoryRetentionSupport inMemoryRetentionSupport() {
        return new InMemoryRetentionSupport();
    }

    @Bean
    @ConditionalOnMissingBean(RetentionCandidateSource.class)
    RetentionCandidateSource retentionCandidateSource(InMemoryRetentionSupport support) {
        return support;
    }

    @Bean
    @ConditionalOnMissingBean(RetentionDeleter.class)
    RetentionDeleter retentionDeleter(InMemoryRetentionSupport support) {
        return support;
    }

    @Bean
    @ConditionalOnMissingBean(RetentionAuditSink.class)
    RetentionAuditSink retentionAuditSink(InMemoryRetentionSupport support) {
        return support;
    }

    @Bean
    @ConditionalOnProperty(name = "actenora.persistence.mode", havingValue = "inmemory", matchIfMissing = true)
    @ConditionalOnMissingBean(LegalHoldRepository.class)
    LegalHoldRepository legalHoldRepository() {
        return new InMemoryLegalHoldRepository();
    }

    @Bean
    @ConditionalOnMissingBean(RetentionJobService.class)
    RetentionJobService retentionJobService(
            RetentionCandidateSource candidateSource,
            LegalHoldRepository legalHoldRepository,
            RetentionDeleter deleter,
            RetentionAuditSink auditSink,
            InstantClock clock
    ) {
        return new RetentionJobService(candidateSource, legalHoldRepository, deleter, auditSink, clock);
    }

    @Bean
    @ConditionalOnMissingBean(LegalHoldService.class)
    LegalHoldService legalHoldService(
            LegalHoldRepository legalHoldRepository,
            RetentionAuditSink auditSink,
            InstantClock clock
    ) {
        return new LegalHoldService(legalHoldRepository, auditSink, clock);
    }

    @Bean
    @ConditionalOnMissingBean(OperationsApi.class)
    OperationsApi operationsApi(
            OperationsCenterService operationsCenter,
            RetentionJobService retentionJobService,
            LegalHoldService legalHoldService
    ) {
        return new OperationsApi(operationsCenter, retentionJobService, legalHoldService);
    }
}
