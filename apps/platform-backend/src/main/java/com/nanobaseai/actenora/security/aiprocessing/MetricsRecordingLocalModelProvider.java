package com.nanobaseai.actenora.security.aiprocessing;

import com.nanobaseai.actenora.aiprocessing.application.modelworker.InferenceResult;
import com.nanobaseai.actenora.aiprocessing.application.modelworker.InferenceStreamChunk;
import com.nanobaseai.actenora.aiprocessing.application.modelworker.LocalModelProviderException;
import com.nanobaseai.actenora.aiprocessing.application.modelworker.ProviderCapabilities;
import com.nanobaseai.actenora.aiprocessing.application.modelworker.ProviderFailureCategory;
import com.nanobaseai.actenora.aiprocessing.application.modelworker.ProviderHealth;
import com.nanobaseai.actenora.aiprocessing.application.modelworker.ResolvedInferenceInput;
import com.nanobaseai.actenora.aiprocessing.application.modelworker.TokenEstimate;
import com.nanobaseai.actenora.aiprocessing.application.modelworker.TokenUsage;
import com.nanobaseai.actenora.aiprocessing.application.modelworker.WorkerRequestEnvelope;
import com.nanobaseai.actenora.aiprocessing.application.port.LocalModelProvider;
import com.nanobaseai.actenora.observability.metrics.ActenoraMetric;
import com.nanobaseai.actenora.observability.metrics.MetricRecorder;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

/**
 * Records LLM duration / tokens / TTFT / timeout without logging prompt or completion text.
 */
public final class MetricsRecordingLocalModelProvider implements LocalModelProvider {

    private final LocalModelProvider delegate;
    private final MetricRecorder metrics;

    public MetricsRecordingLocalModelProvider(LocalModelProvider delegate, MetricRecorder metrics) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.metrics = Objects.requireNonNull(metrics, "metrics");
    }

    @Override
    public InferenceResult submitInference(WorkerRequestEnvelope envelope, ResolvedInferenceInput input) {
        long started = System.nanoTime();
        try {
            InferenceResult result = delegate.submitInference(envelope, input);
            recordSuccess(envelope, result.tokenUsage(), result.latencyMs(), result.timeToFirstTokenMs());
            return result;
        } catch (LocalModelProviderException ex) {
            recordFailure(envelope, ex, elapsedMs(started));
            throw ex;
        } catch (RuntimeException ex) {
            recordFailure(envelope, null, elapsedMs(started));
            throw ex;
        }
    }

    @Override
    public Stream<InferenceStreamChunk> streamInference(
            WorkerRequestEnvelope envelope,
            ResolvedInferenceInput input
    ) {
        long started = System.nanoTime();
        AtomicLong ttftMs = new AtomicLong(-1L);
        AtomicBoolean firstToken = new AtomicBoolean(true);
        try {
            Stream<InferenceStreamChunk> stream = delegate.streamInference(envelope, input);
            return stream.peek(chunk -> {
                        if (firstToken.get() && !chunk.done() && chunk.delta() != null && !chunk.delta().isEmpty()) {
                            if (firstToken.compareAndSet(true, false)) {
                                ttftMs.set(elapsedMs(started));
                            }
                        }
                        if (chunk.done()) {
                            TokenUsage usage = chunk.tokenUsage() == null ? TokenUsage.unknown() : chunk.tokenUsage();
                            recordSuccess(envelope, usage, elapsedMs(started), ttftMs.get());
                        }
                    })
                    .onClose(() -> {
                        // no-op; success recorded on done chunk
                    });
        } catch (LocalModelProviderException ex) {
            recordFailure(envelope, ex, elapsedMs(started));
            throw ex;
        } catch (RuntimeException ex) {
            recordFailure(envelope, null, elapsedMs(started));
            throw ex;
        }
    }

    @Override
    public ProviderHealth health() {
        return delegate.health();
    }

    @Override
    public ProviderCapabilities capabilities() {
        return delegate.capabilities();
    }

    @Override
    public void cancel(UUID attemptId) {
        delegate.cancel(attemptId);
    }

    @Override
    public TokenEstimate estimateTokens(String text) {
        return delegate.estimateTokens(text);
    }

    @Override
    public void beginDrain() {
        delegate.beginDrain();
    }

    @Override
    public boolean isDraining() {
        return delegate.isDraining();
    }

    private void recordSuccess(
            WorkerRequestEnvelope envelope,
            TokenUsage usage,
            long latencyMs,
            long ttftMs
    ) {
        Map<String, String> attrs = attrs(envelope);
        metrics.timing(ActenoraMetric.INFERENCE_DURATION, latencyMs, attrs);
        metrics.count(ActenoraMetric.INFERENCE_PROMPT_TOKENS, usage.inputTokens(), attrs);
        metrics.count(ActenoraMetric.INFERENCE_COMPLETION_TOKENS, usage.outputTokens(), attrs);
        metrics.count(ActenoraMetric.TOKENS, usage.inputTokens() + usage.outputTokens(), attrs);
        if (ttftMs >= 0) {
            metrics.timing(ActenoraMetric.INFERENCE_TTFT, ttftMs, attrs);
        }
    }

    private void recordFailure(WorkerRequestEnvelope envelope, LocalModelProviderException ex, long latencyMs) {
        Map<String, String> attrs = attrs(envelope);
        metrics.timing(ActenoraMetric.INFERENCE_DURATION, latencyMs, attrs);
        if (ex != null && (ex.category() == ProviderFailureCategory.CONNECT_TIMEOUT
                || ex.category() == ProviderFailureCategory.READ_TIMEOUT)) {
            metrics.increment(ActenoraMetric.INFERENCE_TIMEOUT, attrs);
        }
    }

    private static Map<String, String> attrs(WorkerRequestEnvelope envelope) {
        Map<String, String> attrs = new LinkedHashMap<>();
        attrs.put("task_type", envelope.taskType().name());
        attrs.put("served_model_id", envelope.servedModelId());
        attrs.put("prompt_version", envelope.promptVersion());
        attrs.put("job_id", envelope.jobId().toString());
        return attrs;
    }

    private static long elapsedMs(long startedNanos) {
        return Math.max(0L, (System.nanoTime() - startedNanos) / 1_000_000L);
    }
}
