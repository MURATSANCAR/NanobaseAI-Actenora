package com.nanobaseai.actenora.transcript.domain.normalization;

import com.nanobaseai.actenora.transcript.domain.ContentHash;
import com.nanobaseai.actenora.transcript.domain.TranscriptSegment;
import com.nanobaseai.actenora.transcript.domain.dictionary.DictionaryMatcher;
import com.nanobaseai.actenora.transcript.domain.dictionary.TenantDictionary;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Deterministic transcript normalizer: whitespace, dictionary rewrites, speaker resolution.
 * Preserves original ↔ normalized segment linkage.
 */
public final class TranscriptNormalizer {

    private TranscriptNormalizer() {
    }

    public static NormalizationOutcome normalize(
            List<TranscriptSegment> originalSegments,
            List<NormalizationIssue> parseIssues,
            TenantDictionary dictionary) {
        List<NormalizationIssue> issues = new ArrayList<>(parseIssues);
        List<NormalizedSegment> normalized = new ArrayList<>();
        List<SpeakerResolution> resolutions = new ArrayList<>();

        int whitespaceNormalizedCount = 0;
        int dictionaryRewrites = 0;
        int speakersResolved = 0;
        int speakersAmbiguous = 0;
        int speakersUnresolved = 0;
        int speakersMissing = 0;
        int overlapWarnings = (int) parseIssues.stream()
                .filter(i -> i.type() == NormalizationIssueType.OVERLAPPING_SEGMENTS)
                .count();
        int malformedTimestampCount = (int) parseIssues.stream()
                .filter(i -> i.type() == NormalizationIssueType.MALFORMED_TIMESTAMP)
                .count();
        int duplicatesRemoved = (int) parseIssues.stream()
                .filter(i -> i.type() == NormalizationIssueType.DUPLICATE_SEGMENT)
                .count();

        int sequence = 0;
        for (TranscriptSegment original : originalSegments) {
            String originalContent = original.content();
            String whitespaceNormalized = WhitespaceNormalizer.normalize(originalContent);
            if (!whitespaceNormalized.equals(originalContent)) {
                whitespaceNormalizedCount++;
            }

            DictionaryMatcher.RewriteResult rewrite =
                    DictionaryMatcher.rewrite(whitespaceNormalized, dictionary);
            dictionaryRewrites += rewrite.rewriteCount();

            SpeakerResolution resolution = SpeakerResolver.resolve(
                    original.id(),
                    original.speakerDisplayName().orElse(null),
                    dictionary);
            resolutions.add(resolution);

            String speakerDisplay = original.speakerDisplayName().orElse(null);
            String speakerId = original.speakerId().orElse(null);
            switch (resolution.status()) {
                case RESOLVED_EXACT, RESOLVED_ALIAS -> {
                    speakersResolved++;
                    speakerDisplay = resolution.resolvedCanonical().orElse(speakerDisplay);
                    speakerId = resolution.resolvedEntryId().map(UUID::toString).orElse(speakerId);
                }
                case AMBIGUOUS -> {
                    speakersAmbiguous++;
                    issues.add(NormalizationIssue.of(
                            NormalizationIssueType.AMBIGUOUS_SPEAKER,
                            "Ambiguous speaker match was not auto-finalized",
                            original.sequence(),
                            original.id(),
                            false));
                }
                case UNRESOLVED -> {
                    speakersUnresolved++;
                    issues.add(NormalizationIssue.of(
                            NormalizationIssueType.UNKNOWN_SPEAKER,
                            "Speaker not found in tenant dictionary",
                            original.sequence(),
                            original.id(),
                            false));
                }
                case MISSING_SPEAKER -> {
                    speakersMissing++;
                    issues.add(NormalizationIssue.of(
                            NormalizationIssueType.MISSING_SPEAKER,
                            "Segment has no speaker label",
                            original.sequence(),
                            original.id(),
                            false));
                }
            }

            normalized.add(new NormalizedSegment(
                    UUID.randomUUID(),
                    original.id(),
                    sequence++,
                    speakerId,
                    speakerDisplay,
                    original.startOffsetMs(),
                    original.endOffsetMs(),
                    originalContent,
                    rewrite.text(),
                    resolution));
        }

        ContentHash hash = hashNormalized(normalized);
        NormalizationMetrics metrics = new NormalizationMetrics(
                originalSegments.size(),
                normalized.size(),
                duplicatesRemoved,
                whitespaceNormalizedCount,
                dictionaryRewrites,
                speakersResolved,
                speakersAmbiguous,
                speakersUnresolved,
                speakersMissing,
                overlapWarnings,
                malformedTimestampCount,
                issues.size());

        return new NormalizationOutcome(normalized, resolutions, issues, metrics, hash);
    }

    public static ContentHash hashNormalized(List<NormalizedSegment> segments) {
        StringBuilder canonical = new StringBuilder();
        for (NormalizedSegment segment : segments) {
            if (!canonical.isEmpty()) {
                canonical.append('\n');
            }
            canonical.append(segment.canonicalLine());
        }
        return ContentHash.ofBytes(canonical.toString().getBytes(StandardCharsets.UTF_8));
    }

    public record NormalizationOutcome(
            List<NormalizedSegment> segments,
            List<SpeakerResolution> speakerResolutions,
            List<NormalizationIssue> issues,
            NormalizationMetrics metrics,
            ContentHash normalizedTranscriptHash
    ) {
        public NormalizationOutcome {
            segments = List.copyOf(segments);
            speakerResolutions = List.copyOf(speakerResolutions);
            issues = List.copyOf(issues);
        }
    }
}
