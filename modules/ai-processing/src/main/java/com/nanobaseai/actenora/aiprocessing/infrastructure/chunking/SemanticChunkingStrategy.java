package com.nanobaseai.actenora.aiprocessing.infrastructure.chunking;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.ChunkingConfig;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.ChunkingStrategy;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.SegmentInput;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.TranscriptChunk;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Embedding-based semantic chunking delegated to the on-prem ai-orchestrator ({@code /semantic-chunk}).
 *
 * <p>Groups topically-coherent segments together instead of cutting on a fixed token count. This is
 * a decorator: it calls the orchestrator and, on ANY failure — disabled flag, blank URL, network
 * error, malformed response, or incomplete segment coverage — it falls back to the injected
 * {@code fallback} strategy so extraction never breaks. The segment-atomic / evidence-id contract of
 * {@link ChunkingStrategy} is preserved: every input segment appears in exactly one chunk, in order.
 */
public final class SemanticChunkingStrategy implements ChunkingStrategy {

    private static final Logger log = LoggerFactory.getLogger(SemanticChunkingStrategy.class);

    private final ChunkingStrategy fallback;
    private final String baseUrl;
    private final boolean enabled;
    private final double breakpointPercentile;
    private final Duration requestTimeout;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public SemanticChunkingStrategy(
            ChunkingStrategy fallback,
            String baseUrl,
            boolean enabled,
            double breakpointPercentile,
            Duration requestTimeout) {
        this(
                fallback,
                baseUrl,
                enabled,
                breakpointPercentile,
                requestTimeout,
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build(),
                new ObjectMapper());
    }

    SemanticChunkingStrategy(
            ChunkingStrategy fallback,
            String baseUrl,
            boolean enabled,
            double breakpointPercentile,
            Duration requestTimeout,
            HttpClient httpClient,
            ObjectMapper objectMapper) {
        this.fallback = Objects.requireNonNull(fallback, "fallback");
        this.baseUrl = baseUrl == null ? "" : baseUrl.trim();
        this.enabled = enabled;
        this.breakpointPercentile = breakpointPercentile;
        this.requestTimeout = Objects.requireNonNull(requestTimeout, "requestTimeout");
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    @Override
    public List<TranscriptChunk> chunk(List<SegmentInput> segments, ChunkingConfig config) {
        if (!enabled || baseUrl.isEmpty() || segments == null || segments.size() < 2) {
            return fallback.chunk(segments, config);
        }
        try {
            JsonNode response = callOrchestrator(segments, config);
            List<TranscriptChunk> chunks = mapResponse(segments, response);
            log.info(
                    "event=semantic_chunk_ok segments={} chunks={} fallback=token-window",
                    segments.size(),
                    chunks.size());
            return chunks;
        } catch (SemanticChunkingException exc) {
            log.warn("event=semantic_chunk_fallback reason={} -> token-window", exc.getMessage());
            return fallback.chunk(segments, config);
        } catch (Exception exc) { // never let chunking break extraction
            log.warn("event=semantic_chunk_error reason={} -> token-window", exc.toString());
            return fallback.chunk(segments, config);
        }
    }

    private JsonNode callOrchestrator(List<SegmentInput> segments, ChunkingConfig config) {
        ObjectNode body = objectMapper.createObjectNode();
        ArrayNode segArray = body.putArray("segments");
        for (SegmentInput segment : segments) {
            ObjectNode node = segArray.addObject();
            node.put("id", segment.segmentId());
            node.put("text", segment.content());
        }
        body.put("max_tokens", Math.max(256, config.effectiveTargetTokens()));
        body.put("min_tokens", Math.max(0, config.effectiveTargetTokens() / 4));
        body.put("breakpoint_percentile", breakpointPercentile);

        byte[] payload;
        try {
            payload = objectMapper.writeValueAsBytes(body);
        } catch (Exception exc) {
            throw new SemanticChunkingException("request-serialize:" + exc.getMessage());
        }

        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/semantic-chunk"))
                .timeout(requestTimeout)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(payload))
                .build();

        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (Exception exc) {
            throw new SemanticChunkingException("transport:" + exc.getClass().getSimpleName());
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new SemanticChunkingException("http-" + response.statusCode());
        }
        try {
            return objectMapper.readTree(response.body());
        } catch (Exception exc) {
            throw new SemanticChunkingException("response-parse:" + exc.getMessage());
        }
    }

    /**
     * Map the orchestrator response back to segment-atomic chunks. Pure and deterministic. Throws
     * {@link SemanticChunkingException} if coverage is not exact (every input segment used once, in
     * order) so the caller falls back safely.
     */
    static List<TranscriptChunk> mapResponse(List<SegmentInput> segments, JsonNode response) {
        Map<String, SegmentInput> byId = new LinkedHashMap<>();
        for (SegmentInput segment : segments) {
            byId.put(segment.segmentId(), segment);
        }
        JsonNode chunksNode = response == null ? null : response.get("chunks");
        if (chunksNode == null || !chunksNode.isArray() || chunksNode.isEmpty()) {
            throw new SemanticChunkingException("no-chunks");
        }

        List<TranscriptChunk> chunks = new ArrayList<>();
        List<String> flattenedOrder = new ArrayList<>();
        int index = 0;
        for (JsonNode chunkNode : chunksNode) {
            JsonNode ids = chunkNode.get("segment_ids");
            if (ids == null || !ids.isArray() || ids.isEmpty()) {
                throw new SemanticChunkingException("empty-chunk");
            }
            List<SegmentInput> chunkSegments = new ArrayList<>();
            int estimatedTokens = 0;
            for (JsonNode idNode : ids) {
                String id = idNode.asText();
                SegmentInput segment = byId.get(id);
                if (segment == null) {
                    throw new SemanticChunkingException("unknown-segment:" + id);
                }
                chunkSegments.add(segment);
                flattenedOrder.add(id);
                estimatedTokens += Math.max(1, (segment.content().length() + 3) / 4);
            }
            chunks.add(new TranscriptChunk(index++, chunkSegments, estimatedTokens));
        }

        // Exact-coverage guard: same ids, same count, same order as input (segment-atomic contract).
        if (flattenedOrder.size() != segments.size()) {
            throw new SemanticChunkingException(
                    "coverage-count " + flattenedOrder.size() + "!=" + segments.size());
        }
        for (int i = 0; i < segments.size(); i++) {
            if (!segments.get(i).segmentId().equals(flattenedOrder.get(i))) {
                throw new SemanticChunkingException("coverage-order@" + i);
            }
        }
        return chunks;
    }

    static final class SemanticChunkingException extends RuntimeException {
        SemanticChunkingException(String message) {
            super(message);
        }
    }
}
