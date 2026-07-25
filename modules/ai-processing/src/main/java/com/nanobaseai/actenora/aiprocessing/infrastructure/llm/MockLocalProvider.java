package com.nanobaseai.actenora.aiprocessing.infrastructure.llm;

import com.nanobaseai.actenora.aiprocessing.application.modelworker.HealthStatus;
import com.nanobaseai.actenora.aiprocessing.application.modelworker.InferenceResult;
import com.nanobaseai.actenora.aiprocessing.application.modelworker.InferenceStreamChunk;
import com.nanobaseai.actenora.aiprocessing.application.modelworker.LocalModelProviderException;
import com.nanobaseai.actenora.aiprocessing.application.modelworker.ProviderCapabilities;
import com.nanobaseai.actenora.aiprocessing.application.modelworker.ProviderFailureCategory;
import com.nanobaseai.actenora.aiprocessing.application.modelworker.ProviderHealth;
import com.nanobaseai.actenora.aiprocessing.application.modelworker.ResolvedInferenceInput;
import com.nanobaseai.actenora.aiprocessing.application.modelworker.SafeInferenceLog;
import com.nanobaseai.actenora.aiprocessing.application.modelworker.TokenEstimate;
import com.nanobaseai.actenora.aiprocessing.application.modelworker.TokenUsage;
import com.nanobaseai.actenora.aiprocessing.application.modelworker.WorkerRequestEnvelope;
import com.nanobaseai.actenora.aiprocessing.application.port.LocalModelProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * Deterministic in-process provider for CI and routing tests without a GPU runtime.
 */
public final class MockLocalProvider implements LocalModelProvider {

    private final int maxConcurrency;
    private final boolean streamingEnabled;
    private final Set<String> servedModelIds;
    private final Semaphore concurrency;
    private final ConcurrentHashMap<UUID, AtomicBoolean> cancellations = new ConcurrentHashMap<>();
    private final AtomicBoolean draining = new AtomicBoolean(false);
    private final AtomicReference<ProviderHealth> health;
    private final AtomicReference<Supplier<String>> responseSupplier;
    private final AtomicReference<ProviderFailureCategory> forcedFailure = new AtomicReference<>();
    private final AtomicReference<String> responseModelOverride = new AtomicReference<>();
    private volatile long artificialDelayMs;
    private volatile long connectDelayMs;

    public MockLocalProvider() {
        this(2, true, Set.of("mock-model"));
    }

    public MockLocalProvider(int maxConcurrency, boolean streamingEnabled, Set<String> servedModelIds) {
        if (maxConcurrency < 1) {
            throw new IllegalArgumentException("maxConcurrency must be >= 1");
        }
        this.maxConcurrency = maxConcurrency;
        this.streamingEnabled = streamingEnabled;
        this.servedModelIds = Set.copyOf(Objects.requireNonNull(servedModelIds, "servedModelIds"));
        this.concurrency = new Semaphore(maxConcurrency, true);
        this.health = new AtomicReference<>(ProviderHealth.up("mock ready", 1));
        this.responseSupplier = new AtomicReference<>(() -> "{\"ok\":true}");
    }

    public void setHealth(ProviderHealth health) {
        this.health.set(Objects.requireNonNull(health, "health"));
    }

    public void setResponse(String content) {
        this.responseSupplier.set(() -> content);
    }

    public void setResponseModelOverride(String modelId) {
        this.responseModelOverride.set(modelId);
    }

    public void forceFailure(ProviderFailureCategory category) {
        this.forcedFailure.set(category);
    }

    public void clearForcedFailure() {
        this.forcedFailure.set(null);
    }

    public void setArtificialDelayMs(long artificialDelayMs) {
        this.artificialDelayMs = Math.max(0, artificialDelayMs);
    }

    public void setConnectDelayMs(long connectDelayMs) {
        this.connectDelayMs = Math.max(0, connectDelayMs);
    }

    @Override
    public void beginDrain() {
        draining.set(true);
    }

    @Override
    public boolean isDraining() {
        return draining.get();
    }

    @Override
    public InferenceResult submitInference(WorkerRequestEnvelope envelope, ResolvedInferenceInput input) {
        Objects.requireNonNull(envelope, "envelope");
        Objects.requireNonNull(input, "input");
        long started = System.nanoTime();
        SafeInferenceLog.started(envelope, "mock");
        if (!concurrency.tryAcquire()) {
            throw failure(envelope, ProviderFailureCategory.CONCURRENCY_LIMIT,
                    "mock concurrency limit reached", true, started);
        }
        AtomicBoolean cancelled = new AtomicBoolean(false);
        cancellations.put(envelope.attemptId(), cancelled);
        try {
            simulateConnect(envelope, started);
            rejectIfDraining(envelope, started);
            ensureCancelled(cancelled, envelope, started);
            ensureServedModel(envelope, started);
            ensureHealth(envelope, started);
            maybeForceFailure(envelope, started);
            sleep(artificialDelayMs, cancelled, envelope, started);
            ensureCancelled(cancelled, envelope, started);

            String model = responseModelOverride.get();
            if (model == null) {
                model = envelope.servedModelId();
            } else if (!model.equals(envelope.servedModelId())) {
                throw failure(envelope, ProviderFailureCategory.MODEL_MISMATCH,
                        "mock response model mismatch", false, started);
            }

            String content = responseSupplier.get().get();
            TokenUsage usage = TokenUsage.of(
                    estimateTokens(input.systemPrompt() + input.userPrompt()).tokens(),
                    estimateTokens(content).tokens()
            );
            long latencyMs = (System.nanoTime() - started) / 1_000_000L;
            SafeInferenceLog.completed(envelope, usage, latencyMs);
            return new InferenceResult(
                    envelope.jobId(),
                    envelope.attemptId(),
                    model,
                    content,
                    usage,
                    latencyMs
            );
        } finally {
            cancellations.remove(envelope.attemptId());
            concurrency.release();
        }
    }

    @Override
    public Stream<InferenceStreamChunk> streamInference(
            WorkerRequestEnvelope envelope,
            ResolvedInferenceInput input
    ) {
        if (!streamingEnabled) {
            throw LocalModelProviderException.of(
                    ProviderFailureCategory.STREAMING_NOT_SUPPORTED,
                    "mock streaming disabled",
                    false
            );
        }
        InferenceResult result = submitInference(envelope, input);
        List<InferenceStreamChunk> chunks = new ArrayList<>();
        String content = result.content();
        int mid = Math.max(1, content.length() / 2);
        chunks.add(InferenceStreamChunk.delta(content.substring(0, Math.min(mid, content.length()))));
        if (mid < content.length()) {
            chunks.add(InferenceStreamChunk.delta(content.substring(mid)));
        }
        chunks.add(InferenceStreamChunk.done(result.tokenUsage()));
        return chunks.stream();
    }

    @Override
    public ProviderHealth health() {
        ProviderHealth current = health.get();
        SafeInferenceLog.health("mock", current);
        return current;
    }

    @Override
    public ProviderCapabilities capabilities() {
        return new ProviderCapabilities("mock", streamingEnabled, true, maxConcurrency, servedModelIds);
    }

    @Override
    public void cancel(UUID attemptId) {
        AtomicBoolean flag = cancellations.get(attemptId);
        if (flag != null) {
            flag.set(true);
        }
    }

    @Override
    public TokenEstimate estimateTokens(String text) {
        if (text == null || text.isEmpty()) {
            return TokenEstimate.approximate(0);
        }
        return TokenEstimate.approximate(Math.max(1, (int) Math.ceil(text.length() / 4.0)));
    }

    private void simulateConnect(WorkerRequestEnvelope envelope, long started) {
        if (connectDelayMs <= 0) {
            return;
        }
        try {
            Thread.sleep(connectDelayMs);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw failure(envelope, ProviderFailureCategory.CANCELLED, "connect interrupted", false, started, ex);
        }
        ProviderFailureCategory forced = forcedFailure.get();
        if (forced == ProviderFailureCategory.CONNECT_TIMEOUT) {
            throw failure(envelope, ProviderFailureCategory.CONNECT_TIMEOUT, "mock connect timeout", true, started);
        }
        if (forced == ProviderFailureCategory.CONNECTION_FAILURE) {
            throw failure(envelope, ProviderFailureCategory.CONNECTION_FAILURE, "mock connection failure", true, started);
        }
    }

    private void maybeForceFailure(WorkerRequestEnvelope envelope, long started) {
        ProviderFailureCategory forced = forcedFailure.get();
        if (forced == null
                || forced == ProviderFailureCategory.CONNECT_TIMEOUT
                || forced == ProviderFailureCategory.CONNECTION_FAILURE) {
            return;
        }
        throw failure(envelope, forced, "mock forced failure " + forced, forced != ProviderFailureCategory.CANCELLED, started);
    }

    private void rejectIfDraining(WorkerRequestEnvelope envelope, long started) {
        if (draining.get()) {
            throw failure(envelope, ProviderFailureCategory.DRAINING, "mock draining", true, started);
        }
    }

    private void ensureServedModel(WorkerRequestEnvelope envelope, long started) {
        if (!servedModelIds.contains(envelope.servedModelId())) {
            throw failure(envelope, ProviderFailureCategory.INVALID_SERVED_MODEL,
                    "unknown served model " + envelope.servedModelId(), false, started);
        }
    }

    private void ensureHealth(WorkerRequestEnvelope envelope, long started) {
        HealthStatus status = health.get().status();
        if (status == HealthStatus.DEGRADED || status == HealthStatus.DOWN) {
            throw failure(envelope, ProviderFailureCategory.HEALTH_DEGRADED,
                    "mock health " + status, true, started);
        }
    }

    private void sleep(long delayMs, AtomicBoolean cancelled, WorkerRequestEnvelope envelope, long started) {
        if (delayMs <= 0) {
            return;
        }
        long deadline = System.nanoTime() + delayMs * 1_000_000L;
        while (System.nanoTime() < deadline) {
            ensureCancelled(cancelled, envelope, started);
            try {
                Thread.sleep(10);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw failure(envelope, ProviderFailureCategory.CANCELLED, "delay interrupted", false, started, ex);
            }
        }
        // Endpoint/read timeout simulation when delay exceeds envelope timeout.
        if (delayMs >= envelope.timeoutSeconds() * 1000L) {
            throw failure(envelope, ProviderFailureCategory.READ_TIMEOUT, "mock read timeout", true, started);
        }
    }

    private void ensureCancelled(
            AtomicBoolean cancelled,
            WorkerRequestEnvelope envelope,
            long started
    ) {
        if (cancelled.get()) {
            throw failure(envelope, ProviderFailureCategory.CANCELLED, "mock cancelled", false, started);
        }
    }

    private LocalModelProviderException failure(
            WorkerRequestEnvelope envelope,
            ProviderFailureCategory category,
            String message,
            boolean retryable,
            long started
    ) {
        return failure(envelope, category, message, retryable, started, null);
    }

    private LocalModelProviderException failure(
            WorkerRequestEnvelope envelope,
            ProviderFailureCategory category,
            String message,
            boolean retryable,
            long started,
            Throwable cause
    ) {
        SafeInferenceLog.failed(envelope, category, retryable, (System.nanoTime() - started) / 1_000_000L);
        if (cause == null) {
            return LocalModelProviderException.of(category, message, retryable);
        }
        return LocalModelProviderException.of(category, message, retryable, cause);
    }
}
