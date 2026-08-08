package com.nanobaseai.actenora.security.meetingintelligence;

import com.nanobaseai.actenora.meetingintelligence.infrastructure.embedding.OpenAiCompatibleEmbeddingPort;
import com.nanobaseai.actenora.security.aiprocessing.LocalProviderFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Read-only view + live probe for the OpenAI-compatible embedding endpoint used by
 * meeting knowledge search. Deliberately does NOT hot-swap the runtime embedding port:
 * the embedding dimension is fixed at the pgvector column, so changing endpoint/model/
 * dimensions must go through env + restart (and a reindex if dimensions change). This
 * service only lets an operator (a) see the current configuration and (b) live-test a
 * candidate endpoint before committing it to the environment file.
 */
public final class EmbeddingConnectionService {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingConnectionService.class);

    /** Short timeout for the interactive probe so the UI never hangs. */
    private static final Duration PROBE_TIMEOUT = Duration.ofSeconds(12);

    private final String mode;
    private final URI baseUrl;
    private final String modelId;
    private final int dimensions;
    private final boolean apiKeyConfigured;
    private final String apiKey;

    public EmbeddingConnectionService(
            String mode,
            String baseUrl,
            String modelId,
            int dimensions,
            String apiKey
    ) {
        this.mode = mode == null || mode.isBlank() ? "hash" : mode.trim();
        this.baseUrl = baseUrl == null || baseUrl.isBlank() ? null : URI.create(baseUrl.trim());
        this.modelId = modelId == null || modelId.isBlank() ? null : modelId.trim();
        this.dimensions = dimensions;
        this.apiKey = apiKey == null || apiKey.isBlank() ? null : apiKey;
        this.apiKeyConfigured = this.apiKey != null;
    }

    public ConnectionView current() {
        return new ConnectionView(
                mode,
                "openai-compatible".equalsIgnoreCase(mode) && baseUrl != null,
                maskEndpoint(baseUrl),
                baseUrl == null ? "" : baseUrl.toString(),
                modelId == null ? "" : modelId,
                dimensions,
                apiKeyConfigured,
                Instant.now()
        );
    }

    /**
     * Live-probe the embedding endpoint. Candidate fields fall back to the configured
     * values when null/blank, so the UI can either test the current config or a typed
     * candidate before the operator writes it to the environment file.
     */
    public TestResult test(TestCommand command) {
        TestCommand cmd = command == null ? new TestCommand(null, null, null, null) : command;
        URI candidateBaseUrl = cmd.baseUrl() == null || cmd.baseUrl().isBlank()
                ? baseUrl
                : URI.create(cmd.baseUrl().trim());
        String candidateModelId = firstNonBlank(cmd.modelId(), modelId, "embedding");
        String candidateApiKey = cmd.apiKey() == null || cmd.apiKey().isBlank() ? apiKey : cmd.apiKey().trim();
        int candidateDimensions = cmd.dimensions() != null && cmd.dimensions() >= 8 ? cmd.dimensions() : dimensions;

        if (candidateBaseUrl == null) {
            return new TestResult(false, 0,
                    "No embedding endpoint configured. Set actenora.knowledge.embedding.base-url "
                            + "(mode=openai-compatible) or provide a URL to test.",
                    candidateDimensions);
        }
        try {
            // Reuse the AI provider egress guard so an operator cannot probe public hosts (SSRF).
            LocalProviderFactory.assertLocalEndpoint(candidateBaseUrl);
        } catch (IllegalStateException ex) {
            return new TestResult(false, 0, "Endpoint rejected: " + ex.getMessage(), candidateDimensions);
        }

        OpenAiCompatibleEmbeddingPort port = new OpenAiCompatibleEmbeddingPort(
                candidateBaseUrl,
                candidateModelId,
                candidateDimensions,
                PROBE_TIMEOUT,
                candidateApiKey
        );
        long start = System.nanoTime();
        try {
            float[] vector = port.embed("connection test");
            long latencyMs = (System.nanoTime() - start) / 1_000_000L;
            return new TestResult(
                    true,
                    latencyMs,
                    "OK — returned a " + vector.length + "-dimensional embedding.",
                    vector.length);
        } catch (RuntimeException ex) {
            long latencyMs = (System.nanoTime() - start) / 1_000_000L;
            String detail = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
            log.info("Embedding connection test failed host={} reason={}",
                    maskEndpoint(candidateBaseUrl), detail);
            return new TestResult(false, latencyMs, "Unreachable or invalid: " + detail, candidateDimensions);
        }
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v.trim();
            }
        }
        return "embedding";
    }

    private static String maskEndpoint(URI uri) {
        if (uri == null) {
            return "";
        }
        String host = uri.getHost() == null ? "localhost" : uri.getHost();
        int port = uri.getPort();
        return port > 0 ? host + ":" + port : host;
    }

    public record TestCommand(
            String baseUrl,
            String modelId,
            String apiKey,
            Integer dimensions
    ) {
    }

    public record TestResult(
            boolean healthy,
            long latencyMs,
            String detail,
            int dimensions
    ) {
    }

    public record ConnectionView(
            String mode,
            boolean configured,
            String endpointHost,
            String baseUrl,
            String modelId,
            int dimensions,
            boolean apiKeyConfigured,
            Instant checkedAt
    ) {
    }
}
