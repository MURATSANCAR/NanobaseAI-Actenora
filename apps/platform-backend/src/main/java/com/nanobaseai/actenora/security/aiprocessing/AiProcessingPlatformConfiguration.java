package com.nanobaseai.actenora.security.aiprocessing;

import com.nanobaseai.actenora.ActenoraProfiles;
import com.nanobaseai.actenora.aiprocessing.api.AiProcessingApi;
import com.nanobaseai.actenora.security.messaging.TranscriptReadyAiAdmissionHandler;
import com.nanobaseai.actenora.tenant.api.TenantApi;
import com.nanobaseai.actenora.aiprocessing.api.ExtractionPipelineApi;
import com.nanobaseai.actenora.aiprocessing.api.MultiModelRoutingApi;
import com.nanobaseai.actenora.aiprocessing.application.AiJobService;
import com.nanobaseai.actenora.aiprocessing.application.AiProcessingFacade;
import com.nanobaseai.actenora.aiprocessing.application.MultiModelRoutingService;
import com.nanobaseai.actenora.aiprocessing.application.admission.DefaultAdmissionController;
import com.nanobaseai.actenora.aiprocessing.application.execution.AiJobInferenceExecutor;
import com.nanobaseai.actenora.aiprocessing.application.execution.MultiModelRoutingJobCoordinator;
import com.nanobaseai.actenora.aiprocessing.application.pipeline.ExtractionPipelineFacade;
import com.nanobaseai.actenora.aiprocessing.application.pipeline.ExtractionPipelineService;
import com.nanobaseai.actenora.aiprocessing.application.pipeline.ModelRuntimePort;
import com.nanobaseai.actenora.aiprocessing.application.pipeline.PriorMeetingContextPort;
import com.nanobaseai.actenora.aiprocessing.application.pipeline.PromptRegistryPort;
import com.nanobaseai.actenora.aiprocessing.application.pipeline.staged.PipelineGraphFactory;
import com.nanobaseai.actenora.aiprocessing.application.port.AdmissionController;
import com.nanobaseai.actenora.aiprocessing.application.port.AiAttemptRepository;
import com.nanobaseai.actenora.aiprocessing.application.port.AiJobDeadNotifier;
import com.nanobaseai.actenora.aiprocessing.application.port.AiJobRepository;
import com.nanobaseai.actenora.aiprocessing.application.port.AttemptHistoryPort;
import com.nanobaseai.actenora.aiprocessing.application.port.InferenceInputResolverPort;
import com.nanobaseai.actenora.aiprocessing.application.port.JobRoutingCoordinatorPort;
import com.nanobaseai.actenora.aiprocessing.application.port.JobScheduler;
import com.nanobaseai.actenora.aiprocessing.application.port.LocalDeploymentCatalogPort;
import com.nanobaseai.actenora.aiprocessing.application.port.LocalModelProvider;
import com.nanobaseai.actenora.aiprocessing.application.port.LocalModelProviderLocator;
import com.nanobaseai.actenora.aiprocessing.application.port.MeetingNoteHandoffPort;
import com.nanobaseai.actenora.aiprocessing.application.port.ModelCatalogPort;
import com.nanobaseai.actenora.aiprocessing.application.port.ModelQualityMetricsPort;
import com.nanobaseai.actenora.aiprocessing.application.port.ModelRouter;
import com.nanobaseai.actenora.aiprocessing.application.port.PipelineQualityMetricsPort;
import com.nanobaseai.actenora.aiprocessing.application.port.RetryQueuePort;
import com.nanobaseai.actenora.aiprocessing.application.port.RoutableCandidate;
import com.nanobaseai.actenora.aiprocessing.application.port.RoutingDecisionStorePort;
import com.nanobaseai.actenora.aiprocessing.application.port.ServedModelResolverPort;
import com.nanobaseai.actenora.aiprocessing.application.port.ShadowExecutionStorePort;
import com.nanobaseai.actenora.aiprocessing.application.port.TenantAiPolicyPort;
import com.nanobaseai.actenora.aiprocessing.application.port.TranscriptSegmentSourcePort;
import com.nanobaseai.actenora.aiprocessing.application.routing.CapabilityModelRouter;
import com.nanobaseai.actenora.aiprocessing.application.scheduling.FairJobScheduler;
import com.nanobaseai.actenora.aiprocessing.domain.job.AiCapability;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.MeetingLlmBudgets;
import com.nanobaseai.actenora.aiprocessing.domain.routing.MultiModelRouter;
import com.nanobaseai.actenora.aiprocessing.infrastructure.adapter.LocalProviderModelRuntimeAdapter;
import com.nanobaseai.actenora.aiprocessing.infrastructure.adapter.Qwen27BModelAdapter;
import com.nanobaseai.actenora.aiprocessing.infrastructure.persistence.DefaultModelCatalogBootstrap;
import com.nanobaseai.actenora.aiprocessing.infrastructure.persistence.InMemoryAiAttemptRepository;
import com.nanobaseai.actenora.aiprocessing.infrastructure.persistence.InMemoryAiJobRepository;
import com.nanobaseai.actenora.aiprocessing.infrastructure.persistence.InMemoryModelCatalog;
import com.nanobaseai.actenora.aiprocessing.infrastructure.prompt.InMemoryPromptRegistry;
import com.nanobaseai.actenora.aiprocessing.infrastructure.prompt.PromptRegistryInferenceInputResolver;
import com.nanobaseai.actenora.aiprocessing.infrastructure.routing.DefaultModelRoleBootstrap;
import com.nanobaseai.actenora.aiprocessing.infrastructure.routing.InMemoryAttemptHistoryStore;
import com.nanobaseai.actenora.aiprocessing.infrastructure.routing.InMemoryModelQualityMetricsStore;
import com.nanobaseai.actenora.aiprocessing.infrastructure.routing.InMemoryRetryQueue;
import com.nanobaseai.actenora.aiprocessing.infrastructure.routing.InMemoryRoutingDecisionStore;
import com.nanobaseai.actenora.aiprocessing.infrastructure.routing.InMemoryShadowExecutionStore;
import com.nanobaseai.actenora.modelmanagement.application.DeploymentHealthSettings;
import com.nanobaseai.actenora.modelmanagement.application.ModelDefinitionRepository;
import com.nanobaseai.actenora.modelmanagement.application.ModelDeploymentRepository;
import com.nanobaseai.actenora.modelmanagement.application.ModelRegistryService;
import com.nanobaseai.actenora.modelmanagement.domain.ModelCapability;
import com.nanobaseai.actenora.modelmanagement.domain.ModelDefinition;
import com.nanobaseai.actenora.modelmanagement.domain.ModelDeployment;
import com.nanobaseai.actenora.modelmanagement.domain.ModelStatus;
import com.nanobaseai.actenora.observability.metrics.MetricRecorder;
import com.nanobaseai.actenora.sharedkernel.coordination.DistributedLock;
import com.nanobaseai.actenora.sharedkernel.coordination.FixedWindowRateLimiter;
import com.nanobaseai.actenora.sharedkernel.coordination.JobProgressCache;
import com.nanobaseai.actenora.sharedkernel.time.InstantClock;
import com.nanobaseai.actenora.transcript.application.port.out.TranscriptSegmentRepository;
import com.nanobaseai.actenora.meetingintelligence.api.ledger.ContinuityLedgerApi;
import com.nanobaseai.actenora.meetingintelligence.application.port.HybridKnowledgeSearchPort;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * FAZ 12 — wire AI job admission/routing/scheduling and FAZ 15 multi-model façade (InMemory).
 * FAZ 13 — wire the configured local provider and the claim → infer → complete executor.
 * FAZ 14 — bind extraction pipeline + transcript segment source into the job path.
 * FAZ 15 — bind role-based routing, provenance, and quality metrics to the claim → execute path.
 * FAZ 16 — optional MeetingNoteHandoffPort persists FinalNoteDraft via Meeting Intelligence.
 */
@Configuration
@EnableScheduling
@EnableConfigurationProperties({
        LocalProviderProperties.class,
        AiRoutingProperties.class,
        AiPipelineProperties.class,
        MeetingSignalGateProperties.class
})
public class AiProcessingPlatformConfiguration {

    /**
     * Single {@link LocalModelProvider} bean (swappable). Do not also expose a
     * second {@code LocalModelProvider} alias — Spring would fail with NoUniqueBeanDefinitionException.
     */
    @Bean
    @Primary
    SwappableLocalModelProvider swappableLocalModelProvider(
            LocalProviderProperties properties,
            Environment environment,
            MetricRecorder metricRecorder,
            FixedWindowRateLimiter fixedWindowRateLimiter,
            @Value("${actenora.ai.provider.rate-limit-per-minute:60}") int rateLimitPerMinute
    ) {
        LocalModelProvider initial = LocalProviderFactory.create(properties, ActenoraProfiles.isStrictProduction(environment));
        LocalModelProvider metered = new MetricsRecordingLocalModelProvider(initial, metricRecorder);
        LocalModelProvider limited = new RateLimitedLocalModelProvider(
                metered,
                fixedWindowRateLimiter,
                Math.max(1, rateLimitPerMinute),
                Duration.ofMinutes(1)
        );
        return new SwappableLocalModelProvider(limited);
    }

    @Bean
    NanobaseAiModelRegistrySync nanobaseAiModelRegistrySync(
            ModelRegistryService modelRegistryService,
            ModelDefinitionRepository modelDefinitions,
            ModelDeploymentRepository deployments,
            com.fasterxml.jackson.databind.ObjectMapper objectMapper
    ) {
        return new NanobaseAiModelRegistrySync(
                modelRegistryService,
                modelDefinitions,
                deployments,
                objectMapper
        );
    }

    @Bean
    NanobaseAiConnectionService nanobaseAiConnectionService(
            SwappableLocalModelProvider swappable,
            LocalProviderProperties properties,
            Environment environment,
            NanobaseAiModelRegistrySync modelRegistrySync,
            MetricRecorder metricRecorder,
            FixedWindowRateLimiter fixedWindowRateLimiter,
            @Value("${actenora.ai.provider.rate-limit-per-minute:60}") int rateLimitPerMinute
    ) {
        return new NanobaseAiConnectionService(
                swappable,
                properties,
                ActenoraProfiles.isStrictProduction(environment),
                modelRegistrySync,
                metricRecorder,
                fixedWindowRateLimiter,
                rateLimitPerMinute
        );
    }

    @Bean
    NanobaseAiModelRegistryStartupSync nanobaseAiModelRegistryStartupSync(
            NanobaseAiConnectionService connectionService,
            LocalProviderProperties properties
    ) {
        return new NanobaseAiModelRegistryStartupSync(connectionService, properties);
    }

    @Bean
    NanobaseAiDeploymentHeartbeatScheduler nanobaseAiDeploymentHeartbeatScheduler(
            NanobaseAiConnectionService connectionService
    ) {
        return new NanobaseAiDeploymentHeartbeatScheduler(connectionService);
    }

    @Bean
    LocalModelProviderLocator localModelProviderLocator(SwappableLocalModelProvider provider) {
        return LocalModelProviderLocator.single(provider);
    }

    @Bean
    @ConditionalOnMissingBean(PromptRegistryPort.class)
    PromptRegistryPort inMemoryPromptRegistry() {
        return new InMemoryPromptRegistry();
    }

    @Bean
    InferenceInputResolverPort promptRegistryInferenceInputResolver(PromptRegistryPort promptRegistry) {
        return new PromptRegistryInferenceInputResolver(promptRegistry);
    }

    @Bean
    ServedModelResolverPort registryServedModelResolver(
            ModelDefinitionRepository modelDefinitions,
            LocalProviderProperties properties
    ) {
        return modelDefinitionId -> {
            if (properties.hasFastExtractionServedModelId()
                    && DefaultModelRoleBootstrap.FAST_EXTRACTION_MODEL_ID.equals(modelDefinitionId)) {
                return Optional.of(properties.getFastExtractionServedModelId());
            }
            return modelDefinitions.findById(modelDefinitionId).map(ModelDefinition::servedModelId);
        };
    }

    @Bean
    TranscriptSegmentSourcePort transcriptSegmentSource(TranscriptSegmentRepository segments) {
        return new TranscriptSegmentSourceAdapter(segments);
    }

    @Bean(name = "singleModelRuntimePort")
    ModelRuntimePort localModelRuntimePort(
            SwappableLocalModelProvider provider,
            ModelDefinitionRepository modelDefinitions,
            LocalProviderProperties properties
    ) {
        var known = provider.capabilities() == null
                ? Set.<String>of()
                : provider.capabilities().servedModelIds();
        java.util.function.Predicate<String> concreteServed = id -> id != null
                && !id.isBlank()
                && !id.equals("nanobaseai-primary")
                && !id.equals("nanobaseai-local");
        var selected = modelDefinitions.findAll().stream()
                .filter(ModelDefinition::acceptsNewWork)
                .filter(definition -> definition.supportsCapability(
                        com.nanobaseai.actenora.modelmanagement.domain.ModelCapabilityType.TRANSCRIPT_EXTRACTION)
                        || definition.supportsCapability(
                        com.nanobaseai.actenora.modelmanagement.domain.ModelCapabilityType.FINAL_NOTE))
                .sorted(java.util.Comparator
                        .comparing((ModelDefinition definition) -> !concreteServed.test(definition.servedModelId()))
                        .thenComparing((ModelDefinition definition) ->
                                known == null || known.isEmpty() || !known.contains(definition.servedModelId()))
                        .thenComparingInt(ModelDefinition::priority)
                        .thenComparing(ModelDefinition::modelKey))
                .findFirst();
        UUID modelDefinitionId = selected
                .map(ModelDefinition::id)
                .orElse(DefaultModelRoleBootstrap.QWEN27_FINAL_MODEL_ID);
        String servedModelId = selected
                .map(ModelDefinition::servedModelId)
                .filter(concreteServed)
                .or(() -> known == null ? java.util.Optional.empty() : known.stream().filter(concreteServed).findFirst())
                .orElse(Qwen27BModelAdapter.SERVED_MODEL_ID);
        String modelVersion = servedModelId + "@local-v1";
        int timeoutSeconds = (int) Math.max(1, properties.getReadTimeout().toSeconds());
        return new LocalProviderModelRuntimeAdapter(
                provider,
                new com.nanobaseai.actenora.aiprocessing.application.pipeline.ModelDescriptor(
                        selected.map(ModelDefinition::modelKey).orElse(Qwen27BModelAdapter.CATALOG_ID),
                        servedModelId,
                        modelVersion,
                        MeetingLlmBudgets.operationalContextWindow(
                                selected.map(ModelDefinition::contextWindow)
                                        .orElse(MeetingLlmBudgets.OPERATIONAL_CTX_SIZE)
                        ),
                        MeetingLlmBudgets.operationalMaxOutput(
                                selected.map(ModelDefinition::maxOutputTokens)
                                        .orElse(MeetingLlmBudgets.DEFAULT_MAX_TOKENS)
                        )
                ),
                modelDefinitionId,
                timeoutSeconds
        );
    }

    @Bean
    com.nanobaseai.actenora.aiprocessing.domain.pipeline.signal.ChunkExtractionService chunkExtractionService(
            MeetingSignalGateProperties signalGateProperties,
            MetricRecorder metricRecorder
    ) {
        return com.nanobaseai.actenora.aiprocessing.domain.pipeline.signal.ChunkExtractionService.create(
                signalGateProperties.toConfig(),
                new MetricRecorderChunkGateListener(metricRecorder)
        );
    }

    @Bean
    ExtractionPipelineService extractionPipelineService(
            PromptRegistryPort promptRegistry,
            ModelRuntimePort modelRuntime,
            com.nanobaseai.actenora.aiprocessing.domain.pipeline.signal.ChunkExtractionService chunkExtractionService,
            PipelineQualityMetricsPort pipelineQualityMetrics,
            MeetingSignalGateProperties meetingSignalGateProperties
    ) {
        MeetingQualityProperties.install(meetingSignalGateProperties.toMeetingQualityProperties());
        return new ExtractionPipelineService(
                promptRegistry,
                modelRuntime,
                new com.nanobaseai.actenora.aiprocessing.domain.pipeline.SegmentNormalizer(),
                new com.nanobaseai.actenora.aiprocessing.domain.pipeline.TranscriptChunker(),
                new com.nanobaseai.actenora.aiprocessing.domain.pipeline.ContextWindowGuard(),
                new com.nanobaseai.actenora.aiprocessing.infrastructure.json.LimitedJsonRepair(),
                new com.nanobaseai.actenora.aiprocessing.infrastructure.json.ExtractionJsonSchemaValidator(),
                new com.nanobaseai.actenora.aiprocessing.infrastructure.json.ExtractionBundleMapper(),
                new com.nanobaseai.actenora.aiprocessing.domain.pipeline.DeterministicExtractionValidator(),
                new com.nanobaseai.actenora.aiprocessing.domain.pipeline.ExtractionMerger(),
                new com.nanobaseai.actenora.aiprocessing.domain.pipeline.FinalNoteAssembler(),
                new com.nanobaseai.actenora.aiprocessing.domain.pipeline.RetryClassifier(),
                new com.nanobaseai.actenora.aiprocessing.domain.pipeline.PromptInjectionGuard(),
                chunkExtractionService,
                pipelineQualityMetrics
        );
    }

    @Bean
    ExtractionPipelineApi extractionPipelineApi(ExtractionPipelineService pipelineService) {
        return new ExtractionPipelineFacade(pipelineService);
    }

    @Bean
    JobRoutingCoordinatorPort jobRoutingCoordinator(
            MultiModelRoutingService routingService,
            TenantAiPolicyPort tenantAiPolicy,
            AiRoutingProperties routingProperties
    ) {
        return new MultiModelRoutingJobCoordinator(
                routingService, tenantAiPolicy, routingProperties.isShadowExecutionEnabled());
    }

    @Bean
    PipelineQualityMetricsPort pipelineQualityMetricsPort(MetricRecorder metricRecorder) {
        return new MetricRecorderPipelineQualityMetrics(metricRecorder);
    }

    @Bean
    AiJobInferenceExecutor aiJobInferenceExecutor(
            AiJobService aiJobService,
            LocalModelProviderLocator providers,
            InferenceInputResolverPort inputResolver,
            ServedModelResolverPort servedModels,
            ExtractionPipelineService extractionPipeline,
            TranscriptSegmentSourcePort segmentSource,
            JobRoutingCoordinatorPort routingCoordinator,
            AiRoutingProperties routingProperties,
            MeetingNoteHandoffPort noteHandoff,
            LocalProviderProperties properties,
            PipelineQualityMetricsPort pipelineQualityMetrics,
            PriorMeetingContextPort priorMeetingContext,
            ObjectProvider<com.nanobaseai.actenora.aiprocessing.application.pipeline.staged.StagedPipelineRunner> stagedRunner,
            AiPipelineProperties pipelineProperties
    ) {
        return new AiJobInferenceExecutor(
                aiJobService,
                providers,
                inputResolver,
                servedModels,
                extractionPipeline,
                segmentSource,
                routingProperties.isEnabled() ? routingCoordinator : null,
                noteHandoff,
                pipelineQualityMetrics,
                priorMeetingContext,
                pipelineProperties.isStaged() ? stagedRunner.getIfAvailable() : null,
                properties.getMaxAttempts(),
                (int) Math.max(1, properties.getReadTimeout().toSeconds())
        );
    }

    @Bean
    @ConditionalOnMissingBean(PriorMeetingContextPort.class)
    PriorMeetingContextPort priorMeetingContextPort(
            ObjectProvider<ContinuityLedgerApi> continuityLedgerApi,
            ObjectProvider<HybridKnowledgeSearchPort> knowledgeSearch
    ) {
        // Temporary isolation: prior-context load was implicated in EVAL OOM before first infer.
        // Re-enable LedgerPriorMeetingContextAdapter after the allocator is fixed.
        return PriorMeetingContextPort.noop();
    }

    @Bean
    @ConditionalOnProperty(name = "actenora.persistence.mode", havingValue = "inmemory", matchIfMissing = true)
    @ConditionalOnMissingBean(AiJobRepository.class)
    AiJobRepository inMemoryAiJobRepository() {
        return new InMemoryAiJobRepository();
    }

    @Bean
    @ConditionalOnProperty(name = "actenora.persistence.mode", havingValue = "inmemory", matchIfMissing = true)
    @ConditionalOnMissingBean(AiAttemptRepository.class)
    AiAttemptRepository inMemoryAiAttemptRepository() {
        return new InMemoryAiAttemptRepository();
    }

    @Bean
    @Primary
    @ConditionalOnMissingBean(ModelCatalogPort.class)
    ModelCatalogPort preferRegistryModelCatalog(
            ModelDefinitionRepository modelDefinitions,
            ModelDeploymentRepository deployments,
            DeploymentHealthSettings healthSettings,
            InstantClock clock
    ) {
        InMemoryModelCatalog seed = new InMemoryModelCatalog();
        DefaultModelCatalogBootstrap.seed(seed);
        return new PreferRegistryModelCatalog(
                modelDefinitions, deployments, healthSettings, clock, seed);
    }

    @Bean
    ModelRouter capabilityModelRouter(ModelCatalogPort catalog, TenantAiPolicyPort tenantAiPolicy) {
        return new CapabilityModelRouter(catalog, tenantAiPolicy);
    }

    @Bean
    JobScheduler fairJobScheduler(
            AiJobRepository jobs,
            AiAttemptRepository attempts,
            TenantAiPolicyPort tenantAiPolicy,
            ModelRouter modelRouter,
            LocalProviderProperties properties,
            ObjectProvider<org.springframework.transaction.PlatformTransactionManager> txManager
    ) {
        org.springframework.transaction.support.TransactionTemplate tx = null;
        org.springframework.transaction.PlatformTransactionManager manager = txManager.getIfAvailable();
        if (manager != null) {
            tx = new org.springframework.transaction.support.TransactionTemplate(manager);
        }
        return createFairJobScheduler(jobs, attempts, tenantAiPolicy, modelRouter, properties, tx);
    }

    /** Test / manual wiring without Spring transaction manager. */
    public JobScheduler fairJobScheduler(
            AiJobRepository jobs,
            AiAttemptRepository attempts,
            TenantAiPolicyPort tenantAiPolicy,
            ModelRouter modelRouter,
            LocalProviderProperties properties
    ) {
        return createFairJobScheduler(jobs, attempts, tenantAiPolicy, modelRouter, properties, null);
    }

    private static JobScheduler createFairJobScheduler(
            AiJobRepository jobs,
            AiAttemptRepository attempts,
            TenantAiPolicyPort tenantAiPolicy,
            ModelRouter modelRouter,
            LocalProviderProperties properties,
            org.springframework.transaction.support.TransactionOperations tx
    ) {
        return new FairJobScheduler(
                jobs,
                attempts,
                tenantAiPolicy,
                modelRouter,
                FairJobScheduler.DEFAULT_AGING_INTERVAL,
                FairJobScheduler.DEFAULT_AGING_BONUS,
                FairJobScheduler.DEFAULT_AVG_JOB_DURATION,
                properties.getMaxAttempts(),
                tx
        );
    }

    @Bean
    AdmissionController defaultAdmissionController(
            AiJobRepository jobs,
            TenantAiPolicyPort tenantAiPolicy,
            ModelRouter modelRouter,
            JobScheduler jobScheduler
    ) {
        return new DefaultAdmissionController(jobs, tenantAiPolicy, modelRouter, jobScheduler);
    }

    @Bean
    AiJobService aiJobService(
            AdmissionController admissionController,
            AiJobRepository jobs,
            AiAttemptRepository attempts,
            JobScheduler jobScheduler,
            JobProgressCache jobProgressCache,
            ObjectProvider<AiJobDeadNotifier> deadNotifier
    ) {
        AiJobDeadNotifier deferred = job -> {
            AiJobDeadNotifier notifier = deadNotifier.getIfAvailable();
            if (notifier != null) {
                notifier.onPermanentlyFailed(job);
            }
        };
        return new AiJobService(
                admissionController,
                jobs,
                attempts,
                jobScheduler,
                jobProgressCache,
                deferred
        );
    }

    /** Test / manual wiring without Spring ObjectProvider. */
    public AiJobService aiJobService(
            AdmissionController admissionController,
            AiJobRepository jobs,
            AiAttemptRepository attempts,
            JobScheduler jobScheduler,
            JobProgressCache jobProgressCache
    ) {
        return new AiJobService(
                admissionController,
                jobs,
                attempts,
                jobScheduler,
                jobProgressCache,
                AiJobDeadNotifier.noop()
        );
    }

    @Bean
    AiProcessingApi aiProcessingApi(AiJobService aiJobService) {
        return new AiProcessingApi.Default(aiJobService);
    }

    @Bean
    TranscriptReadyAiAdmissionHandler transcriptReadyAiAdmissionHandler(
            AiProcessingApi aiProcessingApi,
            TenantApi tenantApi,
            DistributedLock distributedLock,
            PipelineGraphFactory pipelineGraphFactory,
            AiPipelineProperties pipelineProperties
    ) {
        return new TranscriptReadyAiAdmissionHandler(
                aiProcessingApi, tenantApi, distributedLock, pipelineGraphFactory, pipelineProperties);
    }

    @Bean
    @ConditionalOnProperty(name = "actenora.ai.worker.enabled", havingValue = "true", matchIfMissing = true)
    AiJobInferenceWorker aiJobInferenceWorker(
            AiJobInferenceExecutor inferenceExecutor,
            AiProcessingApi aiProcessingApi,
            LocalProviderProperties properties,
            @Value("${actenora.ai.worker.stale-running-after:PT45M}") Duration staleRunningAfter
    ) {
        return new AiJobInferenceWorker(
                inferenceExecutor, aiProcessingApi, staleRunningAfter, properties.getMaxAttempts());
    }

    static final class AiJobInferenceWorker {
        private final AiJobInferenceExecutor inferenceExecutor;
        private final AiProcessingApi aiProcessingApi;
        private final Duration staleRunningAfter;
        private final int maxAttempts;

        AiJobInferenceWorker(
                AiJobInferenceExecutor inferenceExecutor,
                AiProcessingApi aiProcessingApi,
                Duration staleRunningAfter,
                int maxAttempts
        ) {
            this.inferenceExecutor = Objects.requireNonNull(inferenceExecutor, "inferenceExecutor");
            this.aiProcessingApi = Objects.requireNonNull(aiProcessingApi, "aiProcessingApi");
            this.staleRunningAfter = Objects.requireNonNull(staleRunningAfter, "staleRunningAfter");
            if (maxAttempts < 1) {
                throw new IllegalArgumentException("maxAttempts must be >= 1");
            }
            this.maxAttempts = maxAttempts;
        }

        @Scheduled(fixedDelayString = "${actenora.ai.worker.poll-interval:PT15S}")
        void poll() {
            Instant now = Instant.now();
            aiProcessingApi.recoverStaleRunning(now, staleRunningAfter, maxAttempts);
            inferenceExecutor.executeNext(now);
        }
    }

    @Bean
    @ConditionalOnMissingBean(RoutingDecisionStorePort.class)
    RoutingDecisionStorePort inMemoryRoutingDecisionStore() {
        return new InMemoryRoutingDecisionStore();
    }

    @Bean
    @ConditionalOnMissingBean(AttemptHistoryPort.class)
    AttemptHistoryPort inMemoryAttemptHistoryStore() {
        return new InMemoryAttemptHistoryStore();
    }

    @Bean
    @ConditionalOnMissingBean(ShadowExecutionStorePort.class)
    ShadowExecutionStorePort inMemoryShadowExecutionStore() {
        return new InMemoryShadowExecutionStore();
    }

    @Bean
    @ConditionalOnMissingBean(ModelQualityMetricsPort.class)
    ModelQualityMetricsPort inMemoryModelQualityMetricsStore() {
        return new InMemoryModelQualityMetricsStore();
    }

    @Bean
    @ConditionalOnMissingBean(RetryQueuePort.class)
    RetryQueuePort inMemoryRetryQueue() {
        return new InMemoryRetryQueue();
    }

    @Bean
    MultiModelRoutingService multiModelRoutingService(
            LocalDeploymentCatalogPort catalog,
            RoutingDecisionStorePort decisionStore,
            AttemptHistoryPort attemptHistory,
            ShadowExecutionStorePort shadowStore,
            ModelQualityMetricsPort qualityMetrics,
            RetryQueuePort retryQueue,
            Clock clock
    ) {
        return new MultiModelRoutingService(
                new MultiModelRouter(),
                catalog,
                decisionStore,
                attemptHistory,
                shadowStore,
                qualityMetrics,
                retryQueue,
                clock
        );
    }

    @Bean
    MultiModelRoutingApi multiModelRoutingApi(
            MultiModelRoutingService routingService,
            RoutingDecisionStorePort decisionStore,
            ShadowExecutionStorePort shadowStore,
            ModelQualityMetricsPort qualityMetrics
    ) {
        return new AiProcessingFacade(routingService, decisionStore, shadowStore, qualityMetrics);
    }

    /**
     * Projects FAZ 11 registry definitions/deployments into FAZ 12 {@link RoutableCandidate}s.
     * Falls back to bootstrap seed when the registry has no ENABLED models with capabilities.
     */
    public static final class PreferRegistryModelCatalog implements ModelCatalogPort {

        private final ModelDefinitionRepository modelDefinitions;
        private final ModelDeploymentRepository deployments;
        private final DeploymentHealthSettings healthSettings;
        private final InstantClock clock;
        private final InMemoryModelCatalog seed;

        public PreferRegistryModelCatalog(
                ModelDefinitionRepository modelDefinitions,
                ModelDeploymentRepository deployments,
                DeploymentHealthSettings healthSettings,
                InstantClock clock,
                InMemoryModelCatalog seed
        ) {
            this.modelDefinitions = Objects.requireNonNull(modelDefinitions, "modelDefinitions");
            this.deployments = Objects.requireNonNull(deployments, "deployments");
            this.healthSettings = Objects.requireNonNull(healthSettings, "healthSettings");
            this.clock = Objects.requireNonNull(clock, "clock");
            this.seed = Objects.requireNonNull(seed, "seed");
        }

        @Override
        public List<RoutableCandidate> findCandidates(AiCapability capability) {
            List<RoutableCandidate> fromRegistry = projectRegistry();
            if (!fromRegistry.isEmpty()) {
                return fromRegistry.stream()
                        .filter(c -> c.enabledCapabilities().contains(capability))
                        .toList();
            }
            return seed.findCandidates(capability);
        }

        List<RoutableCandidate> projectRegistry() {
            Instant now = clock.now();
            List<RoutableCandidate> candidates = new ArrayList<>();
            for (ModelDefinition definition : modelDefinitions.findAll()) {
                if (definition.status() == ModelStatus.RETIRED) {
                    continue;
                }
                Set<AiCapability> capabilities = enabledCapabilities(definition);
                if (capabilities.isEmpty()) {
                    continue;
                }
                int minContext = minContextRequired(definition);
                for (ModelDeployment deployment : deployments.findByModelDefinitionId(definition.id())) {
                    if (deployment.applyHeartbeatTimeout(now, healthSettings.heartbeatTimeout())) {
                        deployments.save(deployment);
                    }
                    candidates.add(new RoutableCandidate(
                            definition.id(),
                            definition.modelKey(),
                            deployment.id(),
                            deployment.deploymentKey(),
                            capabilities,
                            definition.contextWindow(),
                            minContext,
                            definition.supportedLanguages(),
                            deployment.acceptsNewWork(),
                            definition.acceptsNewWork(),
                            deployment.acceptsNewWork(),
                            deployment.maxConcurrency(),
                            0,
                            0,
                            definition.qualityScore(),
                            definition.speedScore(),
                            definition.priority()
                    ));
                }
            }
            return List.copyOf(candidates);
        }

        static Set<AiCapability> enabledCapabilities(ModelDefinition definition) {
            EnumSet<AiCapability> caps = EnumSet.noneOf(AiCapability.class);
            for (ModelCapability capability : definition.capabilities().values()) {
                if (!capability.enabled()) {
                    continue;
                }
                caps.add(AiCapability.valueOf(capability.capability().name()));
            }
            return caps;
        }

        private static int minContextRequired(ModelDefinition definition) {
            int min = 0;
            for (ModelCapability capability : definition.capabilities().values()) {
                if (capability.enabled()) {
                    min = Math.max(min, capability.minContextRequired());
                }
            }
            return min;
        }
    }
}
