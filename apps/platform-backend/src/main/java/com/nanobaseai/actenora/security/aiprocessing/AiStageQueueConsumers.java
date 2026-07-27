package com.nanobaseai.actenora.security.aiprocessing;

import com.nanobaseai.actenora.aiprocessing.application.pipeline.staged.AiPipelineEvents;
import com.nanobaseai.actenora.aiprocessing.application.pipeline.staged.StagedPipelineRunner;
import com.nanobaseai.actenora.aiprocessing.domain.job.ProcessingStage;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * RabbitMQ stage consumers — wake and claim one job for the matching pipeline stage.
 */
@Component
@ConditionalOnProperty(name = "actenora.messaging.mode", havingValue = "jdbc-rabbit")
@ConditionalOnProperty(name = "actenora.ai.pipeline.mode", havingValue = "staged", matchIfMissing = true)
public class AiStageQueueConsumers {

    private final StagedPipelineRunner runner;

    public AiStageQueueConsumers(StagedPipelineRunner runner) {
        this.runner = runner;
    }

    @RabbitListener(queues = "actenora.ai.normalize", ackMode = "AUTO", concurrency = "1-4")
    public void onNormalize(byte[] ignored) {
        runner.runNext(ProcessingStage.NORMALIZE, Instant.now());
    }

    @RabbitListener(queues = "actenora.ai.triage", ackMode = "AUTO", concurrency = "1-2")
    public void onTriage(byte[] ignored) {
        runner.runNext(ProcessingStage.TRIAGE, Instant.now());
    }

    @RabbitListener(queues = "actenora.ai.chunk", ackMode = "AUTO", concurrency = "1-4")
    public void onChunk(byte[] ignored) {
        runner.runNext(ProcessingStage.CHUNK, Instant.now());
    }

    @RabbitListener(queues = "actenora.ai.extract", ackMode = "AUTO", concurrency = "1-4")
    public void onExtract(byte[] ignored) {
        runner.runNext(ProcessingStage.EXTRACT, Instant.now());
    }

    @RabbitListener(queues = "actenora.ai.merge", ackMode = "AUTO", concurrency = "1")
    public void onMerge(byte[] ignored) {
        runner.runNext(ProcessingStage.MERGE, Instant.now());
    }

    @RabbitListener(queues = "actenora.ai.validate", ackMode = "AUTO", concurrency = "1-4")
    public void onValidate(byte[] ignored) {
        runner.runNext(ProcessingStage.VALIDATE, Instant.now());
    }

    @RabbitListener(queues = "actenora.ai.minutes", ackMode = "AUTO", concurrency = "1")
    public void onMinutes(byte[] ignored) {
        runner.runNext(ProcessingStage.MINUTES, Instant.now());
    }

    @RabbitListener(queues = "actenora.ai.embed", ackMode = "AUTO", concurrency = "1-2")
    public void onEmbed(byte[] ignored) {
        runner.runNext(ProcessingStage.EMBEDDING, Instant.now());
    }

    @SuppressWarnings("unused")
    private static String eventType(ProcessingStage stage) {
        return AiPipelineEvents.eventType(stage);
    }
}
