package com.nanobaseai.actenora.security.meetingintelligence;

import com.nanobaseai.actenora.aiprocessing.application.port.MeetingNoteHandoffPort;
import com.nanobaseai.actenora.aiprocessing.application.port.TranscriptSegmentSourcePort;
import com.nanobaseai.actenora.audit.api.AuditApi;
import com.nanobaseai.actenora.meeting.api.MeetingApi;
import com.nanobaseai.actenora.meetingintelligence.api.EvidenceValidationApi;
import com.nanobaseai.actenora.meetingintelligence.api.MeetingIntelligenceApi;
import com.nanobaseai.actenora.meetingintelligence.application.MeetingIntelligenceApiFacade;
import com.nanobaseai.actenora.meetingintelligence.application.MeetingIntelligenceApplicationService;
import com.nanobaseai.actenora.meetingintelligence.application.mapping.MapAiCandidatesToNoteService;
import com.nanobaseai.actenora.meetingintelligence.application.port.ActionItemRepository;
import com.nanobaseai.actenora.meetingintelligence.application.port.ApprovedKnowledgeIndexerPort;
import com.nanobaseai.actenora.meetingintelligence.application.port.ApprovedNoteLedgerPort;
import com.nanobaseai.actenora.meetingintelligence.application.port.ArtifactMetadataStorePort;
import com.nanobaseai.actenora.meetingintelligence.application.port.ClockPort;
import com.nanobaseai.actenora.meetingintelligence.application.port.CommitmentRepository;
import com.nanobaseai.actenora.meetingintelligence.application.port.DecisionRepository;
import com.nanobaseai.actenora.meetingintelligence.application.port.EmbeddingPort;
import com.nanobaseai.actenora.meetingintelligence.application.port.EvidenceLinkRepository;
import com.nanobaseai.actenora.meetingintelligence.application.port.HybridKnowledgeSearchPort;
import com.nanobaseai.actenora.meetingintelligence.application.port.MeetingIntelligenceAuditPort;
import com.nanobaseai.actenora.meetingintelligence.application.port.MeetingKnowledgeStorePort;
import com.nanobaseai.actenora.meetingintelligence.application.port.MeetingNoteRepository;
import com.nanobaseai.actenora.meetingintelligence.application.port.MeetingNoteVersionRepository;
import com.nanobaseai.actenora.meetingintelligence.application.port.NoteArtifactStoragePort;
import com.nanobaseai.actenora.meetingintelligence.application.port.ImportantFactRepository;
import com.nanobaseai.actenora.meetingintelligence.application.port.IssueRepository;
import com.nanobaseai.actenora.meetingintelligence.application.port.OpenQuestionRepository;
import com.nanobaseai.actenora.meetingintelligence.application.port.ProposalRepository;
import com.nanobaseai.actenora.meetingintelligence.application.port.QualityFlagRepository;
import com.nanobaseai.actenora.meetingintelligence.application.port.RiskRepository;
import com.nanobaseai.actenora.meetingintelligence.application.port.TenantContextPort;
import com.nanobaseai.actenora.meetingintelligence.application.knowledge.ApprovedKnowledgeIndexer;
import com.nanobaseai.actenora.meetingintelligence.application.knowledge.HybridKnowledgeSearchService;
import com.nanobaseai.actenora.meetingintelligence.application.validation.DefaultEvidenceValidationApi;
import com.nanobaseai.actenora.meetingintelligence.application.validation.EvidenceValidationService;
import com.nanobaseai.actenora.meetingintelligence.application.validation.port.ManualReviewCaseRepository;
import com.nanobaseai.actenora.meetingintelligence.application.validation.port.QualityGatePolicyPort;
import com.nanobaseai.actenora.meetingintelligence.application.validation.port.ValidationAuditPort;
import com.nanobaseai.actenora.meetingintelligence.application.validation.port.ValidationRunRepository;
import com.nanobaseai.actenora.meetingintelligence.api.ledger.ContinuityLedgerApi;
import com.nanobaseai.actenora.meetingintelligence.application.ledger.ContinuityLedgerService;
import com.nanobaseai.actenora.meetingintelligence.application.ledger.port.LedgerEventStore;
import com.nanobaseai.actenora.meetingintelligence.application.ledger.port.LedgerProjectionRepository;
import com.nanobaseai.actenora.meetingintelligence.infrastructure.embedding.HashEmbeddingPort;
import com.nanobaseai.actenora.meetingintelligence.infrastructure.embedding.OpenAiCompatibleEmbeddingPort;
import com.nanobaseai.actenora.meetingintelligence.infrastructure.ledger.InMemoryLedgerEventStore;
import com.nanobaseai.actenora.meetingintelligence.infrastructure.ledger.InMemoryLedgerProjectionRepository;
import com.nanobaseai.actenora.meetingintelligence.infrastructure.persistence.InMemoryActionItemRepository;
import com.nanobaseai.actenora.meetingintelligence.infrastructure.persistence.InMemoryArtifactMetadataStore;
import com.nanobaseai.actenora.meetingintelligence.infrastructure.persistence.InMemoryCommitmentRepository;
import com.nanobaseai.actenora.meetingintelligence.infrastructure.persistence.InMemoryDecisionRepository;
import com.nanobaseai.actenora.meetingintelligence.infrastructure.persistence.InMemoryEvidenceLinkRepository;
import com.nanobaseai.actenora.meetingintelligence.infrastructure.persistence.InMemoryMeetingKnowledgeStore;
import com.nanobaseai.actenora.meetingintelligence.infrastructure.persistence.InMemoryMeetingNoteRepository;
import com.nanobaseai.actenora.meetingintelligence.infrastructure.persistence.InMemoryMeetingNoteVersionRepository;
import com.nanobaseai.actenora.meetingintelligence.infrastructure.persistence.InMemoryImportantFactRepository;
import com.nanobaseai.actenora.meetingintelligence.infrastructure.persistence.InMemoryIssueRepository;
import com.nanobaseai.actenora.meetingintelligence.infrastructure.persistence.InMemoryOpenQuestionRepository;
import com.nanobaseai.actenora.meetingintelligence.infrastructure.persistence.InMemoryProposalRepository;
import com.nanobaseai.actenora.meetingintelligence.infrastructure.persistence.InMemoryQualityFlagRepository;
import com.nanobaseai.actenora.meetingintelligence.infrastructure.persistence.InMemoryRiskRepository;
import com.nanobaseai.actenora.meetingintelligence.infrastructure.persistence.SystemClockPort;
import com.nanobaseai.actenora.meetingintelligence.infrastructure.validation.InMemoryManualReviewCaseRepository;
import com.nanobaseai.actenora.meetingintelligence.infrastructure.validation.InMemoryQualityGatePolicyPort;
import com.nanobaseai.actenora.meetingintelligence.infrastructure.validation.InMemoryValidationRunRepository;
import com.nanobaseai.actenora.sharedkernel.messaging.port.OutboxPublisher;
import com.nanobaseai.actenora.sharedkernel.port.storage.ObjectStorage;
import com.nanobaseai.actenora.security.notification.PlatformUserNotificationPublisher;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.time.Clock;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * FAZ 16 — Meeting Intelligence (InMemory) + AI extraction handoff.
 * FAZ 17 — Evidence validation / quality gate beans and gated handoff.
 * FAZ 29 — Approved-note → continuity ledger via transactional outbox.
 */
@Configuration
public class MeetingIntelligencePlatformConfiguration {

    @ConditionalOnProperty(name = "actenora.persistence.mode", havingValue = "inmemory", matchIfMissing = true)
    @ConditionalOnMissingBean(MeetingNoteRepository.class)
    @Bean
    public MeetingNoteRepository inMemoryMeetingNoteRepository() {
        return new InMemoryMeetingNoteRepository();
    }

    @ConditionalOnProperty(name = "actenora.persistence.mode", havingValue = "inmemory", matchIfMissing = true)
    @ConditionalOnMissingBean(MeetingNoteVersionRepository.class)
    @Bean
    public MeetingNoteVersionRepository inMemoryMeetingNoteVersionRepository() {
        return new InMemoryMeetingNoteVersionRepository();
    }

    @ConditionalOnProperty(name = "actenora.persistence.mode", havingValue = "inmemory", matchIfMissing = true)
    @ConditionalOnMissingBean(DecisionRepository.class)
    @Bean
    public DecisionRepository inMemoryDecisionRepository() {
        return new InMemoryDecisionRepository();
    }

    @ConditionalOnProperty(name = "actenora.persistence.mode", havingValue = "inmemory", matchIfMissing = true)
    @ConditionalOnMissingBean(ActionItemRepository.class)
    @Bean
    public ActionItemRepository inMemoryActionItemRepository() {
        return new InMemoryActionItemRepository();
    }

    @ConditionalOnProperty(name = "actenora.persistence.mode", havingValue = "inmemory", matchIfMissing = true)
    @ConditionalOnMissingBean(RiskRepository.class)
    @Bean
    public RiskRepository inMemoryRiskRepository() {
        return new InMemoryRiskRepository();
    }

    @ConditionalOnProperty(name = "actenora.persistence.mode", havingValue = "inmemory", matchIfMissing = true)
    @ConditionalOnMissingBean(CommitmentRepository.class)
    @Bean
    public CommitmentRepository inMemoryCommitmentRepository() {
        return new InMemoryCommitmentRepository();
    }

    @ConditionalOnProperty(name = "actenora.persistence.mode", havingValue = "inmemory", matchIfMissing = true)
    @ConditionalOnMissingBean(OpenQuestionRepository.class)
    @Bean
    public OpenQuestionRepository inMemoryOpenQuestionRepository() {
        return new InMemoryOpenQuestionRepository();
    }

    @ConditionalOnProperty(name = "actenora.persistence.mode", havingValue = "inmemory", matchIfMissing = true)
    @ConditionalOnMissingBean(IssueRepository.class)
    @Bean
    public IssueRepository inMemoryIssueRepository() {
        return new InMemoryIssueRepository();
    }

    @ConditionalOnProperty(name = "actenora.persistence.mode", havingValue = "inmemory", matchIfMissing = true)
    @ConditionalOnMissingBean(ProposalRepository.class)
    @Bean
    public ProposalRepository inMemoryProposalRepository() {
        return new InMemoryProposalRepository();
    }

    @ConditionalOnProperty(name = "actenora.persistence.mode", havingValue = "inmemory", matchIfMissing = true)
    @ConditionalOnMissingBean(ImportantFactRepository.class)
    @Bean
    public ImportantFactRepository inMemoryImportantFactRepository() {
        return new InMemoryImportantFactRepository();
    }

    @ConditionalOnProperty(name = "actenora.persistence.mode", havingValue = "inmemory", matchIfMissing = true)
    @ConditionalOnMissingBean(EvidenceLinkRepository.class)
    @Bean
    public EvidenceLinkRepository inMemoryEvidenceLinkRepository() {
        return new InMemoryEvidenceLinkRepository();
    }

    @ConditionalOnProperty(name = "actenora.persistence.mode", havingValue = "inmemory", matchIfMissing = true)
    @ConditionalOnMissingBean(QualityFlagRepository.class)
    @Bean
    public QualityFlagRepository inMemoryQualityFlagRepository() {
        return new InMemoryQualityFlagRepository();
    }

    @Bean
    public ClockPort meetingIntelligenceClockPort() {
        return new SystemClockPort();
    }

    @Bean
    public TenantContextPort meetingIntelligenceTenantContextPort() {
        return new SecurityContextMeetingIntelligenceTenantPort();
    }

    @Bean
    @Primary
    public MeetingIntelligenceAuditPort auditBackedMeetingIntelligenceAuditPort(AuditApi auditApi) {
        return (tenantId, actorId, action, resourceType, resourceId, metadata, occurredAt) ->
                auditApi.append(
                        tenantId,
                        actorId == null ? "system" : actorId,
                        action,
                        resourceType,
                        resourceId == null ? UUID.randomUUID() : resourceId,
                        metadata == null ? Map.of() : metadata,
                        occurredAt
                );
    }

    @Bean
    public MapAiCandidatesToNoteService mapAiCandidatesToNoteService(
            MeetingNoteRepository notes,
            MeetingNoteVersionRepository versions,
            DecisionRepository decisions,
            ActionItemRepository actionItems,
            RiskRepository risks,
            CommitmentRepository commitments,
            OpenQuestionRepository openQuestions,
            IssueRepository issues,
            ProposalRepository proposals,
            ImportantFactRepository importantFacts,
            EvidenceLinkRepository evidenceLinks,
            QualityFlagRepository qualityFlags,
            ClockPort clock
    ) {
        return new MapAiCandidatesToNoteService(
                notes, versions, decisions, actionItems, risks, commitments,
                openQuestions, issues, proposals, importantFacts, evidenceLinks, qualityFlags, clock
        );
    }

    @Bean
    public MeetingIntelligenceApplicationService meetingIntelligenceApplicationService(
            TenantContextPort tenantContext,
            ClockPort clock,
            MapAiCandidatesToNoteService mappingService,
            MeetingNoteRepository notes,
            MeetingNoteVersionRepository versions,
            DecisionRepository decisions,
            ActionItemRepository actionItems,
            RiskRepository risks,
            CommitmentRepository commitments,
            OpenQuestionRepository openQuestions,
            IssueRepository issues,
            ProposalRepository proposals,
            ImportantFactRepository importantFacts,
            EvidenceLinkRepository evidenceLinks,
            QualityFlagRepository qualityFlags
    ) {
        return new MeetingIntelligenceApplicationService(
                tenantContext, clock, mappingService, notes, versions, decisions, actionItems,
                risks, commitments, openQuestions, issues, proposals, importantFacts, evidenceLinks, qualityFlags
        );
    }

    @ConditionalOnMissingBean(MeetingIntelligenceApi.class)
    @Bean
    public MeetingIntelligenceApi meetingIntelligenceApi(MeetingIntelligenceApplicationService service) {
        return new MeetingIntelligenceApiFacade(service);
    }

    @ConditionalOnProperty(name = "actenora.persistence.mode", havingValue = "inmemory", matchIfMissing = true)
    @ConditionalOnMissingBean(ValidationRunRepository.class)
    @Bean
    public ValidationRunRepository inMemoryValidationRunRepository() {
        return new InMemoryValidationRunRepository();
    }

    @ConditionalOnProperty(name = "actenora.persistence.mode", havingValue = "inmemory", matchIfMissing = true)
    @ConditionalOnMissingBean(ManualReviewCaseRepository.class)
    @Bean
    public ManualReviewCaseRepository inMemoryManualReviewCaseRepository() {
        return new InMemoryManualReviewCaseRepository();
    }

    @ConditionalOnMissingBean(QualityGatePolicyPort.class)
    @Bean
    public QualityGatePolicyPort inMemoryQualityGatePolicyPort() {
        return new InMemoryQualityGatePolicyPort();
    }

    @Bean
    @Primary
    public ValidationAuditPort auditBackedValidationAuditPort(AuditApi auditApi) {
        return (tenantId, actor, action, resourceType, resourceId, metadata, occurredAt) ->
                auditApi.append(
                        tenantId,
                        actor == null ? "system" : actor,
                        action,
                        resourceType,
                        resourceId == null ? UUID.randomUUID() : resourceId,
                        metadata == null ? Map.of() : metadata,
                        occurredAt
                );
    }

    @Bean
    public EvidenceValidationService evidenceValidationService(
            ValidationRunRepository runs,
            ManualReviewCaseRepository reviews,
            QualityGatePolicyPort policy,
            ValidationAuditPort validationAudit
    ) {
        return new EvidenceValidationService(runs, reviews, policy, validationAudit, Clock.systemUTC());
    }

    @Bean
    public EvidenceValidationApi evidenceValidationApi(EvidenceValidationService service) {
        return new DefaultEvidenceValidationApi(service);
    }

    @ConditionalOnMissingBean(MeetingNoteHandoffPort.class)
    @Bean
    public MeetingNoteHandoffPort meetingNoteHandoffPort(
            MeetingIntelligenceApi meetingIntelligenceApi,
            EvidenceValidationApi evidenceValidationApi,
            TranscriptSegmentSourcePort segmentSource,
            MeetingIntelligenceAuditPort auditPort,
            MeetingApi meetingApi,
            NoteArtifactStoragePort noteArtifactStorage,
            ObjectProvider<PlatformUserNotificationPublisher> notificationPublisher,
            ObjectProvider<com.nanobaseai.actenora.delivery.api.DeliveryApi> deliveryApi,
            ObjectProvider<com.nanobaseai.actenora.delivery.application.worker.DeliveryWorker> deliveryWorker
    ) {
        return new MeetingIntelligenceHandoffAdapter(
                meetingIntelligenceApi,
                evidenceValidationApi,
                segmentSource,
                auditPort,
                Optional.of(meetingApi),
                noteArtifactStorage,
                Optional.ofNullable(notificationPublisher.getIfAvailable()),
                Optional.ofNullable(deliveryApi.getIfAvailable()),
                Optional.ofNullable(deliveryWorker.getIfAvailable())
        );
    }

    @ConditionalOnProperty(name = "actenora.persistence.mode", havingValue = "inmemory", matchIfMissing = true)
    @ConditionalOnMissingBean(LedgerEventStore.class)
    @Bean
    public LedgerEventStore inMemoryLedgerEventStore() {
        return new InMemoryLedgerEventStore();
    }

    @ConditionalOnProperty(name = "actenora.persistence.mode", havingValue = "inmemory", matchIfMissing = true)
    @ConditionalOnMissingBean(LedgerProjectionRepository.class)
    @Bean
    public LedgerProjectionRepository inMemoryLedgerProjectionRepository() {
        return new InMemoryLedgerProjectionRepository();
    }

    @Bean
    public ContinuityLedgerService continuityLedgerService(
            LedgerEventStore eventStore,
            LedgerProjectionRepository projections
    ) {
        return new ContinuityLedgerService(eventStore, projections, Clock.systemUTC());
    }

    @Bean
    public ContinuityLedgerApi continuityLedgerApi(ContinuityLedgerService service) {
        return new ContinuityLedgerApi(service);
    }

    @Bean
    public ApprovedNoteLedgerAdapter approvedNoteLedgerWriter(
            DecisionRepository decisions,
            ActionItemRepository actionItems,
            CommitmentRepository commitments,
            RiskRepository risks,
            OpenQuestionRepository openQuestions,
            ContinuityLedgerApi ledgerApi
    ) {
        return new ApprovedNoteLedgerAdapter(
                decisions, actionItems, commitments, risks, openQuestions, ledgerApi);
    }

    @Bean
    @ConditionalOnMissingBean(ApprovedKnowledgeIndexerPort.class)
    public ApprovedKnowledgeIndexerPort approvedKnowledgeIndexerPort(
            DecisionRepository decisions,
            ActionItemRepository actionItems,
            CommitmentRepository commitments,
            RiskRepository risks,
            OpenQuestionRepository openQuestions,
            MeetingKnowledgeStorePort knowledgeStore,
            EmbeddingPort embeddingPort
    ) {
        return new ApprovedKnowledgeIndexer(
                decisions,
                actionItems,
                commitments,
                risks,
                openQuestions,
                knowledgeStore,
                embeddingPort,
                Clock.systemUTC()
        );
    }

    @Bean
    @ConditionalOnMissingBean(MeetingKnowledgeStorePort.class)
    @ConditionalOnProperty(name = "actenora.persistence.mode", havingValue = "inmemory", matchIfMissing = true)
    public MeetingKnowledgeStorePort inMemoryMeetingKnowledgeStore() {
        return new InMemoryMeetingKnowledgeStore();
    }

    @Bean
    @ConditionalOnMissingBean(ArtifactMetadataStorePort.class)
    @ConditionalOnProperty(name = "actenora.persistence.mode", havingValue = "inmemory", matchIfMissing = true)
    public ArtifactMetadataStorePort inMemoryArtifactMetadataStore() {
        return new InMemoryArtifactMetadataStore();
    }

    @Bean
    @ConditionalOnMissingBean(EmbeddingPort.class)
    public EmbeddingPort embeddingPort(
            @org.springframework.beans.factory.annotation.Value(
                    "${actenora.knowledge.embedding.dimensions:1024}") int dimensions,
            @org.springframework.beans.factory.annotation.Value(
                    "${actenora.knowledge.embedding.mode:hash}") String mode,
            @org.springframework.beans.factory.annotation.Value(
                    "${actenora.knowledge.embedding.base-url:}") String baseUrl,
            @org.springframework.beans.factory.annotation.Value(
                    "${actenora.knowledge.embedding.model-id:}") String modelId,
            @org.springframework.beans.factory.annotation.Value(
                    "${actenora.knowledge.embedding.api-key:}") String apiKey,
            @org.springframework.beans.factory.annotation.Value(
                    "${actenora.env:local}") String env
    ) {
        boolean productionLike = env != null && (
                env.equalsIgnoreCase("production")
                        || env.equalsIgnoreCase("prod")
                        || env.equalsIgnoreCase("staging"));
        if (productionLike && "hash".equalsIgnoreCase(mode)) {
            throw new IllegalStateException(
                    "actenora.knowledge.embedding.mode=hash is forbidden in production; "
                            + "set openai-compatible with base-url and model-id");
        }
        if ("openai-compatible".equalsIgnoreCase(mode) && baseUrl != null && !baseUrl.isBlank()) {
            return new OpenAiCompatibleEmbeddingPort(
                    java.net.URI.create(baseUrl),
                    modelId == null || modelId.isBlank() ? "embedding" : modelId,
                    dimensions,
                    java.time.Duration.ofSeconds(30),
                    apiKey == null || apiKey.isBlank() ? null : apiKey
            );
        }
        if ("openai-compatible".equalsIgnoreCase(mode)) {
            throw new IllegalStateException(
                    "actenora.knowledge.embedding.base-url is required when mode=openai-compatible");
        }
        return new HashEmbeddingPort(dimensions);
    }

    @Bean
    @ConditionalOnMissingBean(NoteArtifactStoragePort.class)
    public NoteArtifactStoragePort noteArtifactStoragePort(
            ObjectProvider<ObjectStorage> objectStorage
    ) {
        ObjectStorage storage = objectStorage.getIfAvailable();
        return storage == null ? NoteArtifactStoragePort.noop() : new ObjectStorageNoteArtifactAdapter(storage);
    }

    @Bean
    public HybridKnowledgeSearchPort hybridKnowledgeSearchPort(
            MeetingKnowledgeStorePort knowledgeStore,
            EmbeddingPort embeddingPort
    ) {
        return new HybridKnowledgeSearchService(knowledgeStore, embeddingPort);
    }

    @Bean
    @Primary
    public ApprovedNoteLedgerPort approvedNoteLedgerPort(OutboxPublisher outboxPublisher) {
        return new OutboxApprovedNoteLedgerAdapter(outboxPublisher, "meeting-intelligence");
    }

    @Bean
    public NoteApprovedForLedgerHandler noteApprovedForLedgerHandler(
            ApprovedNoteLedgerAdapter approvedNoteLedgerWriter,
            ApprovedKnowledgeIndexerPort knowledgeIndexer,
            ObjectProvider<com.nanobaseai.actenora.aiprocessing.application.pipeline.staged.PipelineGraphFactory> pipelineGraphFactory,
            ObjectProvider<com.nanobaseai.actenora.security.aiprocessing.AiPipelineProperties> pipelineProperties
    ) {
        return new NoteApprovedForLedgerHandler(
                new CompositeApprovedNoteLedgerAdapter(
                        approvedNoteLedgerWriter,
                        knowledgeIndexer,
                        pipelineGraphFactory,
                        pipelineProperties
                )
        );
    }
}
