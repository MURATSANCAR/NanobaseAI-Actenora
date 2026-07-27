package com.nanobaseai.actenora.aiprocessing.domain.pipeline;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Light pipeline-entry normalization (FAZ 9 owns deep dictionary work).
 * Marks marker-adjacent segments for chunk prioritization.
 */
public final class SegmentNormalizer {

    private static final Pattern MARKER = Pattern.compile(
            "(?i)\\b(decision|karar|action|aksiyon|todo|risk|commitment|taahhüt|open\\s*question|"
                    + "açık\\s*soru|we\\s+(decided|agree|will)|kararlaştırıldı)\\b"
    );
    private static final Pattern CUE_MARKUP = Pattern.compile("</?v(?:\\s+[^>]*)?>|</?c(?:\\.[^>]*)?>|</?b>|</?i>|</?u>");

    public List<SegmentInput> normalize(List<SegmentInput> raw) {
        Objects.requireNonNull(raw, "raw");
        List<SegmentInput> out = new ArrayList<>(raw.size());
        for (SegmentInput segment : raw) {
            String cleaned = collapseWhitespace(stripCueMarkup(segment.content()));
            if (MeetingNoisePatterns.isLowSignalSegment(cleaned)) {
                // Drop ops/UI filler before chunking so it cannot saturate ctx or invent decisions.
                continue;
            }
            boolean marker = segment.markerNear() || MARKER.matcher(cleaned).find();
            out.add(segment.withContent(cleaned).withMarker(marker));
        }
        // Never empty the transcript entirely (pathological all-noise input).
        if (out.isEmpty() && !raw.isEmpty()) {
            for (SegmentInput segment : raw) {
                String cleaned = collapseWhitespace(stripCueMarkup(segment.content()));
                if (!cleaned.isBlank()) {
                    out.add(segment.withContent(cleaned).withMarker(true));
                }
            }
        }
        return List.copyOf(out);
    }

    private static String stripCueMarkup(String content) {
        if (content == null || content.isEmpty()) {
            return "";
        }
        return CUE_MARKUP.matcher(content).replaceAll("");
    }

    private static String collapseWhitespace(String content) {
        return content.replace('\u00A0', ' ').replaceAll("[ \\t\\x0B\\f\\r]+", " ").strip();
    }
}
