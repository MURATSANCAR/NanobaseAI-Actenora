package com.nanobaseai.actenora.aiprocessing.application.pipeline.staged;

import com.nanobaseai.actenora.aiprocessing.application.port.JobScheduler;
import com.nanobaseai.actenora.aiprocessing.domain.job.AiJob;
import com.nanobaseai.actenora.aiprocessing.domain.job.ProcessingStage;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Claims and runs a single stage (poller or Rabbit consumer).
 */
public final class StagedPipelineRunner {

    private final JobScheduler scheduler;
    private final Map<ProcessingStage, StageExecutor> executors;
    private final StageCompletionService completion;

    public StagedPipelineRunner(
            JobScheduler scheduler,
            Map<ProcessingStage, StageExecutor> executors,
            StageCompletionService completion
    ) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.executors = Objects.requireNonNull(executors, "executors");
        this.completion = Objects.requireNonNull(completion, "completion");
    }

    public Optional<StageExecutionResult> runNext(ProcessingStage stage, Instant now) {
        Objects.requireNonNull(stage, "stage");
        Objects.requireNonNull(now, "now");
        Optional<JobScheduler.ClaimedJob> claimed = scheduler.claimNextForStage(now, stage);
        if (claimed.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(runClaimed(claimed.get(), now));
    }

    public StageExecutionResult runClaimed(JobScheduler.ClaimedJob claimed, Instant now) {
        AiJob job = claimed.job();
        StageExecutor executor = executors.get(job.stage());
        if (executor == null) {
            StageExecutionResult failed = StageExecutionResult.failure(
                    job, false, "NO_EXECUTOR", "No executor for stage " + job.stage(), 0L, now);
            completion.complete(failed);
            return failed;
        }
        StageExecutionResult result = executor.execute(job, now);
        completion.complete(result);
        return result;
    }
}
