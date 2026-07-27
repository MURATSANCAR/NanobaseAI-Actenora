package com.nanobaseai.actenora.meetingintelligence.infrastructure.embedding;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nanobaseai.actenora.meetingintelligence.application.port.EmbeddingPort;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Objects;

/**
 * OpenAI-compatible {@code POST /v1/embeddings} client for local/prod embedding runtimes.
 */
public final class OpenAiCompatibleEmbeddingPort implements EmbeddingPort {

    private final URI embeddingsUri;
    private final String modelId;
    private final int dimensions;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final Duration timeout;

    public OpenAiCompatibleEmbeddingPort(
            URI baseUrl,
            String modelId,
            int dimensions,
            Duration timeout
    ) {
        this(
                Objects.requireNonNull(baseUrl, "baseUrl").resolve("/v1/embeddings"),
                modelId,
                dimensions,
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build(),
                new ObjectMapper(),
                timeout == null ? Duration.ofSeconds(30) : timeout
        );
    }

    OpenAiCompatibleEmbeddingPort(
            URI embeddingsUri,
            String modelId,
            int dimensions,
            HttpClient httpClient,
            ObjectMapper objectMapper,
            Duration timeout
    ) {
        this.embeddingsUri = Objects.requireNonNull(embeddingsUri, "embeddingsUri");
        this.modelId = Objects.requireNonNull(modelId, "modelId");
        if (dimensions < 8) {
            throw new IllegalArgumentException("dimensions must be >= 8");
        }
        this.dimensions = dimensions;
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.timeout = Objects.requireNonNull(timeout, "timeout");
    }

    @Override
    public int dimensions() {
        return dimensions;
    }

    @Override
    public float[] embed(String text) {
        Objects.requireNonNull(text, "text");
        try {
            String body = objectMapper.createObjectNode()
                    .put("model", modelId)
                    .put("input", text)
                    .toString();
            HttpRequest request = HttpRequest.newBuilder(embeddingsUri)
                    .timeout(timeout)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("embedding HTTP " + response.statusCode());
            }
            JsonNode data = objectMapper.readTree(response.body()).path("data");
            if (!data.isArray() || data.isEmpty()) {
                throw new IllegalStateException("embedding response missing data");
            }
            JsonNode embedding = data.get(0).path("embedding");
            if (!embedding.isArray() || embedding.size() != dimensions) {
                throw new IllegalStateException(
                        "embedding dimension mismatch: expected " + dimensions + " got " + embedding.size());
            }
            float[] vector = new float[dimensions];
            for (int i = 0; i < dimensions; i++) {
                vector[i] = (float) embedding.get(i).asDouble();
            }
            return vector;
        } catch (IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new IllegalStateException("embedding request failed", ex);
        }
    }
}
