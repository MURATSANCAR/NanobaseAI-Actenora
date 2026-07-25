package com.nanobaseai.actenora.transcript.infrastructure.config;

import com.nanobaseai.actenora.sharedkernel.messaging.EventBackbone;
import com.nanobaseai.actenora.sharedkernel.messaging.EventMessagingConfig;
import com.nanobaseai.actenora.sharedkernel.port.storage.ObjectStorage;
import com.nanobaseai.actenora.sharedkernel.time.InstantClock;
import com.nanobaseai.actenora.transcript.api.TranscriptDeploymentMode;
import com.nanobaseai.actenora.transcript.api.TranscriptApi;
import com.nanobaseai.actenora.transcript.application.TranscriptIngestionService;
import com.nanobaseai.actenora.transcript.application.TranscriptNormalizationService;
import com.nanobaseai.actenora.transcript.application.TenantDictionaryApplicationService;
import com.nanobaseai.actenora.transcript.application.VttUploadValidator;
import com.nanobaseai.actenora.transcript.application.port.out.KnownMeetingOccurrenceStore;
import com.nanobaseai.actenora.transcript.application.port.out.NormalizationRunRepository;
import com.nanobaseai.actenora.transcript.application.port.out.TenantDictionaryRepository;
import com.nanobaseai.actenora.transcript.application.port.out.TranscriptEventPublisher;
import com.nanobaseai.actenora.transcript.application.port.out.TranscriptRepository;
import com.nanobaseai.actenora.transcript.application.port.out.TranscriptSegmentRepository;
import com.nanobaseai.actenora.transcript.infrastructure.messaging.MeetingOccurrenceUpsertedHandler;
import com.nanobaseai.actenora.transcript.infrastructure.messaging.OutboxTranscriptEventPublisher;
import com.nanobaseai.actenora.transcript.infrastructure.persistence.InMemoryKnownMeetingOccurrenceStore;
import com.nanobaseai.actenora.transcript.infrastructure.persistence.InMemoryNormalizationRunRepository;
import com.nanobaseai.actenora.transcript.infrastructure.persistence.InMemoryTenantDictionaryRepository;
import com.nanobaseai.actenora.transcript.infrastructure.persistence.InMemoryTranscriptRepository;
import com.nanobaseai.actenora.transcript.infrastructure.persistence.InMemoryTranscriptSegmentRepository;
import com.nanobaseai.actenora.transcript.infrastructure.storage.InMemoryObjectStorage;
import com.nanobaseai.actenora.transcript.infrastructure.storage.S3CompatibleObjectStorage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.URI;

/**
 * Embedded transcript wiring. Active when {@code actenora.transcript.mode=embedded}
 * (default). Disabled in platform-backend during extraction dual-publish / remote mode.
 */
@Configuration
@ConditionalOnProperty(
        name = TranscriptDeploymentMode.PROPERTY,
        havingValue = TranscriptDeploymentMode.EMBEDDED,
        matchIfMissing = true)
public class TranscriptModuleConfiguration {

    @Bean
    @ConditionalOnProperty(name = "actenora.object-storage.enabled", havingValue = "true")
    @ConditionalOnMissingBean(ObjectStorage.class)
    ObjectStorage s3CompatibleObjectStorage(
            @Value("${actenora.object-storage.endpoint:${OBJECT_STORAGE_ENDPOINT:http://localhost:9000}}") String endpoint,
            @Value("${actenora.object-storage.region:${OBJECT_STORAGE_REGION:us-east-1}}") String region,
            @Value("${actenora.object-storage.access-key:${OBJECT_STORAGE_ACCESS_KEY}}") String accessKey,
            @Value("${actenora.object-storage.secret-key:${OBJECT_STORAGE_SECRET_KEY}}") String secretKey,
            @Value("${actenora.object-storage.bucket:${OBJECT_STORAGE_BUCKET:actenora}}") String bucket) {
        return new S3CompatibleObjectStorage(URI.create(endpoint), region, accessKey, secretKey, bucket);
    }

    @Bean
    @ConditionalOnMissingBean(ObjectStorage.class)
    ObjectStorage inMemoryObjectStorage() {
        return new InMemoryObjectStorage();
    }

    @Bean
    @ConditionalOnMissingBean(TranscriptRepository.class)
    TranscriptRepository transcriptRepository() {
        return new InMemoryTranscriptRepository();
    }

    @Bean
    @ConditionalOnMissingBean(TranscriptSegmentRepository.class)
    TranscriptSegmentRepository transcriptSegmentRepository() {
        return new InMemoryTranscriptSegmentRepository();
    }

    @Bean
    @ConditionalOnMissingBean(KnownMeetingOccurrenceStore.class)
    KnownMeetingOccurrenceStore knownMeetingOccurrenceStore() {
        return new InMemoryKnownMeetingOccurrenceStore();
    }

    @Bean
    VttUploadValidator vttUploadValidator(
            @Value("${actenora.transcript.max-upload-bytes:26214400}") long maxBytes) {
        return new VttUploadValidator(maxBytes);
    }

    @Bean
    @ConditionalOnMissingBean(InstantClock.class)
    InstantClock instantClock() {
        return InstantClock.systemUTC();
    }

    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean(EventBackbone.class)
    EventBackbone transcriptEventBackbone() {
        return EventBackbone.inMemory(EventMessagingConfig.defaults("transcript"));
    }

    @Bean
    @ConditionalOnMissingBean(TranscriptEventPublisher.class)
    TranscriptEventPublisher transcriptEventPublisher(EventBackbone backbone, InstantClock clock) {
        return new OutboxTranscriptEventPublisher(backbone.outboxPublisher(), clock, "transcript");
    }

    @Bean
    MeetingOccurrenceUpsertedHandler meetingOccurrenceUpsertedHandler(KnownMeetingOccurrenceStore store) {
        return new MeetingOccurrenceUpsertedHandler(store);
    }

    @Bean
    @ConditionalOnMissingBean(TenantDictionaryRepository.class)
    TenantDictionaryRepository tenantDictionaryRepository() {
        return new InMemoryTenantDictionaryRepository();
    }

    @Bean
    @ConditionalOnMissingBean(NormalizationRunRepository.class)
    NormalizationRunRepository normalizationRunRepository() {
        return new InMemoryNormalizationRunRepository();
    }

    @Bean
    TenantDictionaryApplicationService tenantDictionaryApplicationService(
            TenantDictionaryRepository dictionaryRepository,
            InstantClock clock) {
        return new TenantDictionaryApplicationService(dictionaryRepository, clock);
    }

    @Bean
    TranscriptIngestionService transcriptIngestionService(
            TranscriptRepository transcriptRepository,
            TranscriptSegmentRepository transcriptSegmentRepository,
            ObjectStorage objectStorage,
            VttUploadValidator validator,
            InstantClock clock,
            TranscriptEventPublisher eventPublisher,
            KnownMeetingOccurrenceStore knownMeetingOccurrenceStore) {
        return new TranscriptIngestionService(
                transcriptRepository,
                transcriptSegmentRepository,
                objectStorage,
                validator,
                clock,
                eventPublisher,
                knownMeetingOccurrenceStore);
    }

    @Bean
    TranscriptNormalizationService transcriptNormalizationService(
            TranscriptRepository transcriptRepository,
            TranscriptSegmentRepository transcriptSegmentRepository,
            TenantDictionaryRepository dictionaryRepository,
            NormalizationRunRepository normalizationRunRepository,
            ObjectStorage objectStorage,
            InstantClock clock) {
        return new TranscriptNormalizationService(
                transcriptRepository,
                transcriptSegmentRepository,
                dictionaryRepository,
                normalizationRunRepository,
                objectStorage,
                clock);
    }

    @Bean
    TranscriptApi transcriptApi(
            TranscriptIngestionService ingestionService,
            TranscriptNormalizationService normalizationService) {
        return new TranscriptApi(ingestionService, normalizationService);
    }
}
