package com.nanobaseai.actenora.security.aiprocessing;

import com.nanobaseai.actenora.aiprocessing.application.AiJobService;
import com.nanobaseai.actenora.aiprocessing.application.pipeline.ModelDescriptor;
import com.nanobaseai.actenora.aiprocessing.application.pipeline.ModelRuntimePort;
import com.nanobaseai.actenora.aiprocessing.application.pipeline.PriorMeetingContextPort;
import com.nanobaseai.actenora.aiprocessing.application.pipeline.PromptRegistryPort;
import com.nanobaseai.actenora.aiprocessing.application.pipeline.staged.DefaultStageExecutors;
import com.nanobaseai.actenora.aiprocessing.application.pipeline.staged.PipelineGraphFactory;
import com.nanobaseai.actenora.aiprocessing.application.pipeline.staged.StageCommandPublisher;
import com.nanobaseai.actenora.aiprocessing.application.pipeline.staged.StageCompletionService;
import com.nanobaseai.actenora.aiprocessing.application.pipeline.staged.StageExecutor;
import com.nanobaseai.actenora.aiprocessing.application.pipeline.staged.StagedPipelineRunner;
import com.nanobaseai.actenora.aiprocessing.application.port.AiJobRepository;
import com.nanobaseai.actenora.aiprocessing.application.port.JobScheduler;
import com.nanobaseai.actenora.aiprocessing.application.port.LocalModelProvider;
import com.nanobaseai.actenora.aiprocessing.application.port.ProcessingArtifactRepository;
import com.nanobaseai.actenora.aiprocessing.application.port.ProcessingJobDependencyRepository;
import com.nanobaseai.actenora.aiprocessing.application.port.TranscriptSegmentSourcePort;
import com.nanobaseai.actenora.aiprocessing.domain.job.ProcessingStage;
import com.nanobaseai.actenora.aiprocessing.domain.routing.TenantRoutingPolicy;
import com.nanobaseai.actenora.aiprocessing.infrastructure.adapter.LocalProviderModelRuntimeAdapter;
import com.nanobaseai.actenora.aiprocessing.infrastructure.adapter.Qwen27BModelAdapter;
import com.nanobaseai.actenora.aiprocessing.infrastructure.adapter.RoleAwareModelRuntimePort;
import com.nanobaseai.actenora.aiprocessing.infrastructure.persistence.InMemoryProcessingArtifactRepository;
import com.nanobaseai.actenora.aiprocessing.infrastructure.persistence.InMemoryProcessingJobDependencyRepository;
import com.nanobaseai.actenora.aiprocessing.infrastructure.routing.DefaultModelRoleBootstrap;
import com.nanobaseai.actenora.observability.metrics.MetricRecorder;
import com.nanobaseai.actenora.sharedkernel.messaging.infrastructure.InMemoryOutboxStore;
import com.nanobaseai.actenora.sharedkernel.messaging.port.OutboxStore;
import com.nanobaseai.actenora.sharedkernel.messaging.support.TenantFairnessTracker;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.Map;

@Configuration
@EnableConfigurationProperties(AiPipelineProperties.class)
public class StagedPipelinePlatformConfiguration {

    @Bean
    @ConditionalOnMissingBean(ProcessingJobDependencyRepository.class)
    ProcessingJobDependencyRepository inMemoryProcessingJobDependencyRepository() {
        return new InMemoryProcessingJobDependencyRepository();
    }

    @Bean
    @ConditionalOnMissingBean(ProcessingArtifactRepository.class)
    ProcessingArtifactRepository inMemoryProcessingArtifactRepository() {
        return new InMemoryProcessingArtifactRepository();
    }

    @Bean
    StageCommandPublisher stageCommandPublisher(ObjectProvider<OutboxStore> outboxStore) {
        OutboxStore store = outboxStore.getIfAvailable();
        if (store == null) {
            store = new InMemoryOutboxStore(new TenantFairnessTracker());
        }
        return new StageCommandPublisher(store);
    }

    @Bean
    PipelineGraphFactory pipelineGraphFactory(
            AiJobRepository jobs,
            ProcessingJobDependencyRepository dependencies,
            StageCommandPublisher commands
    ) {
        return new PipelineGraphFactory(jobs, dependencies, commands);
    }

    @Bean
    StageCompletionService stageCompletionService(
            AiJobService jobService,
            AiJobRepository jobs,
            ProcessingJobDependencyRepository dependencies,
            ProcessingArtifactRepository artifacts,
            StageCommandPublisher commands,
            PipelineGraphFactory graphFactory,
            MetricRecorder metrics
    ) {
        return new StageCompletionService(
                jobService,
                jobs,
                dependencies,
                artifacts,
                commands,
                graphFactory,
                metrics,
                (tenantId, transcriptId) -> transcriptId.toString()
        );
    }

    @Bean
    Map<ProcessingStage, StageExecutor> stageExecutors(
            PromptRegistryPort prompts,
            ModelRuntimePort modelRuntime,
            TranscriptSegmentSourcePort segments,
            ProcessingArtifactRepository artifacts,
            PriorMeetingContextPort priorMeetingContext
    ) {
        return DefaultStageExecutors.createAll(prompts, modelRuntime, segments, artifacts, priorMeetingContext);
    }

    @Bean
    StagedPipelineRunner stagedPipelineRunner(
            JobScheduler scheduler,
            Map<ProcessingStage, StageExecutor> stageExecutors,
            StageCompletionService stageCompletionService
    ) {
        return new StagedPipelineRunner(scheduler, stageExecutors, stageCompletionService);
    }

    /**
     * Dual-model runtime: FAST extract/triage vs FINAL merge/minutes.
     */
    @Bean
    @Primary
    ModelRuntimePort roleAwareModelRuntimePort(
            SwappableLocalModelProvider primaryProvider,
            LocalProviderProperties properties
    ) {
        int timeoutSeconds = (int) Math.max(1, properties.getReadTimeout().toSeconds());
        String fastServed = properties.hasFastExtractionServedModelId()
                ? properties.getFastExtractionServedModelId()
                : "fast-extraction-local";
        String finalServed = properties.hasFinalServedModelId()
                ? properties.getFinalServedModelId()
                : Qwen27BModelAdapter.SERVED_MODEL_ID;

        LocalModelProvider fastProvider = primaryProvider;
        LocalModelProvider finalProvider = primaryProvider;
        if (properties.hasDistinctFastBaseUrl() || properties.hasDistinctFinalBaseUrl()) {
            LocalProviderProperties fastProps = copyWithBase(properties, properties.getFastBaseUrl());
            LocalProviderProperties finalProps = copyWithBase(properties, properties.getFinalBaseUrl());
            try {
                fastProvider = LocalProviderFactory.create(fastProps, false);
                finalProvider = LocalProviderFactory.create(finalProps, false);
            } catch (RuntimeException ignored) {
                fastProvider = primaryProvider;
                finalProvider = primaryProvider;
            }
        }

        ModelRuntimePort fast = new LocalProviderModelRuntimeAdapter(
                fastProvider,
                new ModelDescriptor(
                        "fast-extraction",
                        fastServed,
                        fastServed + "@local-v1",
                        8_192,
                        4_096
                ),
                DefaultModelRoleBootstrap.FAST_EXTRACTION_MODEL_ID,
                timeoutSeconds
        );
        ModelRuntimePort fin = new LocalProviderModelRuntimeAdapter(
                finalProvider,
                new ModelDescriptor(
                        Qwen27BModelAdapter.CATALOG_ID,
                        finalServed,
                        finalServed + "@local-v1",
                        Qwen27BModelAdapter.CONTEXT_WINDOW,
                        Math.max(Qwen27BModelAdapter.MAX_OUTPUT, 6000)
                ),
                DefaultModelRoleBootstrap.QWEN27_FINAL_MODEL_ID,
                timeoutSeconds
        );
        return new RoleAwareModelRuntimePort(fast, fin, TenantRoutingPolicy::defaults);
    }

    private static LocalProviderProperties copyWithBase(LocalProviderProperties source, java.net.URI baseUrl) {
        LocalProviderProperties copy = new LocalProviderProperties();
        copy.setKind(source.getKind());
        copy.setBaseUrl(baseUrl != null ? baseUrl : source.getBaseUrl());
        copy.setConnectTimeout(source.getConnectTimeout());
        copy.setReadTimeout(source.getReadTimeout());
        copy.setMaxConcurrency(source.getMaxConcurrency());
        copy.setMaxConcurrencyExtraction(source.getMaxConcurrencyExtraction());
        copy.setMaxConcurrencyFinal(Math.max(1, source.getMaxConcurrencyFinal()));
        copy.setFastExtractionServedModelId(source.getFastExtractionServedModelId());
        copy.setFinalServedModelId(source.getFinalServedModelId());
        copy.setStreamingEnabled(source.isStreamingEnabled());
        copy.setDegradedProbeThresholdMs(source.getDegradedProbeThresholdMs());
        copy.setServedModelIds(source.getServedModelIds());
        copy.setMaxAttempts(source.getMaxAttempts());
        return copy;
    }
}
