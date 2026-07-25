package com.nanobaseai.actenora.transcript.domain.parsing;

import com.nanobaseai.actenora.transcript.domain.TranscriptSegment;
import com.nanobaseai.actenora.transcript.domain.normalization.NormalizationIssue;

import java.util.List;

/**
 * Result of deterministic VTT parsing prior to dictionary normalization.
 */
public record VttParseResult(
        List<TranscriptSegment> segments,
        List<NormalizationIssue> issues,
        int whitespaceNormalizedCount,
        int duplicatesRemoved
) {
    public VttParseResult {
        segments = List.copyOf(segments);
        issues = List.copyOf(issues);
    }

    public VttParseResult(List<TranscriptSegment> segments, List<NormalizationIssue> issues) {
        this(segments, issues, 0, 0);
    }
}
