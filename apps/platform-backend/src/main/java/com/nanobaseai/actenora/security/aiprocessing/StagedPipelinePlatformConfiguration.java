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
import com.nanobaseai.actenora.aiprocessing.application.pipeline.staged.StageMetricsPort;
import com.nanobaseai.actenora.aiprocessing.application.port.AiJobRepository;
import com.nanobaseai.actenora.aiprocessing.application.port.ApprovedKnowledgeIndexPort;
import com.nanobaseai.actenora.aiprocessing.application.port.JobScheduler;
import com.nanobaseai.actenora.aiprocessing.application.port.LocalModelProvider;
import com.nanobaseai.actenora.aiprocessing.application.port.MeetingNoteHandoffPort;
import com.nanobaseai.actenora.aiprocessing.application.port.ProcessingArtifactRepository;
import com.nanobaseai.actenora.aiprocessing.application.port.ProcessingJobDependencyRepository;
import com.nanobaseai.actenora.aiprocessing.application.port.TranscriptSegmentSourcePort;
import com.nanobaseai.actenora.aiprocessing.domain.job.ProcessingStage;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.MeetingLlmBudgets;
import com.nanobaseai.actenora.aiprocessing.domain.routing.TenantRoutingPolicy;
import com.nanobaseai.actenora.aiprocessing.infrastructure.adapter.LocalProviderModelRuntimeAdapter;
import com.nanobaseai.actenora.aiprocessing.infrastructure.adapter.Qwen27BModelAdapter;
import com.nanobaseai.actenora.aiprocessing.infrastructure.adapter.RoleAwareModelRuntimePort;
import com.nanobaseai.actenora.aiprocessing.infrastructure.persistence.InMemoryProcessingArtifactRepository;
import com.nanobaseai.actenora.aiprocessing.infrastructure.persistence.InMemoryProcessingJobDependencyRepository;
import com.nanobaseai.actenora.aiprocessing.infrastructure.routing.DefaultModelRoleBootstrap;
import com.nanobaseai.actenora.meetingintelligence.application.port.ApprovedKnowledgeIndexerPort;
import com.nanobaseai.actenora.observability.metrics.ActenoraMetric;
import com.nanobaseai.actenora.observability.metrics.MetricRecorder;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;
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
    @org.springframework.context.annotation.Lazy
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
    StageMetricsPort stageMetricsPort(MetricRecorder metricRecorder) {
        return new StageMetricsPort() {
            @Override
            public void recordDuration(ProcessingStage stage, long durationMs, boolean success) {
                metricRecorder.timing(
                        ActenoraMetric.MEETING_JOB_DURATION,
                        durationMs,
                        java.util.Map.of("stage", stage.name(), "success", Boolean.toString(success))
                );
            }

            @Override
            public void recordQueueWait(ProcessingStage stage, long waitMs) {
                metricRecorder.timing(
                        ActenoraMetric.MEETING_JOB_QUEUE_WAIT,
                        waitMs,
                        java.util.Map.of("stage", stage.name())
                );
            }

            @Override
            public void recordDlq(ProcessingStage stage) {
                metricRecorder.increment(
                        ActenoraMetric.MEETING_JOB_DLQ,
                        java.util.Map.of("stage", stage.name())
                );
            }

            @Override
            public void recordEarlyExit() {
                metricRecorder.increment(ActenoraMetric.MEETING_TRIAGE_EARLY_EXIT);
            }
        };
    }

    @Bean
    @ConditionalOnMissingBean(ApprovedKnowledgeIndexPort.class)
    ApprovedKnowledgeIndexPort approvedKnowledgeIndexPort(
            ObjectProvider<ApprovedKnowledgeIndexerPort> indexer
    ) {
        ApprovedKnowledgeIndexerPort delegate = indexer.getIfAvailable();
        if (delegate == null) {
            return ApprovedKnowledgeIndexPort.noop();
        }
        return (tenantId, meetingOccurrenceId, noteId, noteVersionId) ->
                delegate.indexApprovedNote(TenantId.of(tenantId), meetingOccurrenceId, noteId, noteVersionId);
    }

    @Bean
    StageCompletionService stageCompletionService(
            AiJobService jobService,
            AiJobRepository jobs,
            ProcessingJobDependencyRepository dependencies,
            ProcessingArtifactRepository artifacts,
            StageCommandPublisher commands,
            PipelineGraphFactory graphFactory,
            StageMetricsPort stageMetricsPort
    ) {
        return new StageCompletionService(
                jobService,
                jobs,
                dependencies,
                artifacts,
                commands,
                graphFactory,
                (tenantId, transcriptId) -> transcriptId.toString(),
                stageMetricsPort
        );
    }

    @Bean
    Map<ProcessingStage, StageExecutor> stageExecutors(
            PromptRegistryPort prompts,
            ModelRuntimePort modelRuntime,
            TranscriptSegmentSourcePort segments,
            ProcessingArtifactRepository artifacts,
            PriorMeetingContextPort priorMeetingContext,
            ObjectProvider<MeetingNoteHandoffPort> noteHandoff,
            ApprovedKnowledgeIndexPort knowledgeIndex
    ) {
        return DefaultStageExecutors.createAll(
                prompts,
                modelRuntime,
                segments,
                artifacts,
                priorMeetingContext,
                noteHandoff.getIfAvailable() == null
                        ? MeetingNoteHandoffPort.noop()
                        : noteHandoff.getIfAvailable(),
                knowledgeIndex
        );
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
                : "nanobase-meeting-8b";
        String finalServed = properties.hasFinalServedModelId()
                ? properties.getFinalServedModelId()
                : "nanobase-meeting-8b";

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
                        MeetingLlmBudgets.OPERATIONAL_CTX_SIZE,
                        MeetingLlmBudgets.DEFAULT_MAX_TOKENS
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
                        MeetingLlmBudgets.OPERATIONAL_CTX_SIZE,
                        MeetingLlmBudgets.DEFAULT_MAX_TOKENS
                ),
                DefaultModelRoleBootstrap.QWEN27_FINAL_MODEL_ID,
                timeoutSeconds
        );
        return new RoleAwareModelRuntimePort(
                fast,
                fin,
                () -> TenantRoutingPolicy.defaults(java.util.UUID.fromString("00000000-0000-4000-8000-000000000099"))
        );
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
