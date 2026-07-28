package com.nanobaseai.actenora.aiprocessing.domain.pipeline;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Builds token-bounded chunks without splitting segments; prefers marker-aware boundaries.
 */
public final class TranscriptChunker {

    private final TokenEstimator tokenEstimator;

    public TranscriptChunker(TokenEstimator tokenEstimator) {
        this.tokenEstimator = Objects.requireNonNull(tokenEstimator, "tokenEstimator");
    }

    public TranscriptChunker() {
        this(TokenEstimator.approximate());
    }

    public List<TranscriptChunk> chunk(List<SegmentInput> segments, ChunkingConfig config) {
        Objects.requireNonNull(segments, "segments");
        Objects.requireNonNull(config, "config");
        if (segments.isEmpty()) {
            return List.of();
        }

        int target = config.effectiveTargetTokens();
        int overlap = config.effectiveOverlapTokens();

        List<Integer> tokenWeights = new ArrayList<>(segments.size());
        for (SegmentInput segment : segments) {
            tokenWeights.add(tokenEstimator.estimate(segment.content()));
        }

        List<TranscriptChunk> chunks = new ArrayList<>();
        int start = 0;
        int chunkIndex = 0;
        while (start < segments.size()) {
            int end = selectEnd(segments, tokenWeights, start, target);
            List<SegmentInput> window = segments.subList(start, end);
            int tokens = sum(tokenWeights, start, end);
            chunks.add(new TranscriptChunk(chunkIndex++, List.copyOf(window), tokens));
            if (end >= segments.size()) {
                break;
            }
            int next = nextStart(segments, tokenWeights, start, end, overlap);
            // Overlap must never rewind to the same start — that allocates forever.
            if (next <= start) {
                next = end;
            }
            start = next;
        }
        return List.copyOf(chunks);
    }

    private int selectEnd(List<SegmentInput> segments, List<Integer> weights, int start, int target) {
        int end = start;
        int used = 0;
        while (end < segments.size()) {
            int next = weights.get(end);
            if (end > start && used + next > target) {
                break;
            }
            used += next;
            end++;
            if (used >= target) {
                break;
            }
        }
        if (end == start) {
            end = start + 1;
        }
        return preferSignalAwareBoundary(segments, start, preferMarkerBoundary(segments, start, end));
    }

    /**
     * If the natural cut is just after a marker segment, keep the marker with the chunk;
     * if cut is mid-run of non-markers and a nearby marker sits at the edge, snap to it.
     */
    private int preferMarkerBoundary(List<SegmentInput> segments, int start, int end) {
        if (end <= start + 1 || end >= segments.size()) {
            return end;
        }
        // Prefer not to orphan a marker at the start of the next chunk when it fits.
        if (segments.get(end).markerNear() && end - 1 > start) {
            return end; // marker begins next chunk — good for prioritization
        }
        // If last segment is weak and previous is a marker, keep as-is.
        if (segments.get(end - 1).markerNear()) {
            return end;
        }
        // Look slightly back for a marker to end on (within 3 segments).
        int lookback = Math.max(start + 1, end - 3);
        for (int i = end - 1; i >= lookback; i--) {
            if (segments.get(i).markerNear()) {
                return i + 1;
            }
        }
        return end;
    }

    /**
     * Semantic-ish cut: after a signal (marker) stretch, drop trailing non-marker filler
     * into the next chunk so low-signal noise does not dilute EXTRACT windows.
     */
    private int preferSignalAwareBoundary(List<SegmentInput> segments, int start, int end) {
        if (end - start < 4) {
            return end;
        }
        int lastMarker = -1;
        for (int i = start; i < end; i++) {
            if (segments.get(i).markerNear()) {
                lastMarker = i;
            }
        }
        if (lastMarker < 0) {
            return end;
        }
        int trailingNoise = 0;
        for (int i = lastMarker + 1; i < end; i++) {
            if (!segments.get(i).markerNear()) {
                trailingNoise++;
            }
        }
        // Enough filler after the last signal → cut right after last marker,
        // but only when the retained window is still substantial (avoids 10× tiny chunks).
        if (trailingNoise >= 2 && lastMarker + 1 > start && (lastMarker + 1 - start) >= 12) {
            return lastMarker + 1;
        }
        return end;
    }

    private int nextStart(
            List<SegmentInput> segments,
            List<Integer> weights,
            int previousStart,
            int previousEnd,
            int overlapTokens
    ) {
        if (overlapTokens <= 0) {
            return previousEnd;
        }
        int acc = 0;
        int idx = previousEnd;
        while (idx > previousStart && acc < overlapTokens) {
            idx--;
            acc += weights.get(idx);
        }
        // Prefer overlapping onto a marker in the overlap window, but never return
        // previousStart: marker-at-chunk-head would otherwise infinite-loop the outer loop.
        int candidate = idx;
        for (int i = Math.max(idx, previousStart + 1); i < previousEnd; i++) {
            if (segments.get(i).markerNear()) {
                candidate = i;
                break;
            }
        }
        return candidate <= previousStart ? previousEnd : candidate;
    }

    private static int sum(List<Integer> weights, int start, int end) {
        int total = 0;
        for (int i = start; i < end; i++) {
            total += weights.get(i);
        }
        return total;
    }
}
