package com.nanobaseai.actenora.transcript.domain.parsing;

import com.nanobaseai.actenora.sharedkernel.domain.TenantId;
import com.nanobaseai.actenora.transcript.api.TranscriptId;
import com.nanobaseai.actenora.transcript.domain.TranscriptDomainException;
import com.nanobaseai.actenora.transcript.domain.TranscriptSegment;
import com.nanobaseai.actenora.transcript.domain.normalization.NormalizationIssue;
import com.nanobaseai.actenora.transcript.domain.normalization.NormalizationIssueType;
import com.nanobaseai.actenora.transcript.domain.normalization.WhitespaceNormalizer;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic VTT parser: timestamps, speakers, sort, overlap/bad-time checks,
 * duplicate cleanup, and whitespace normalization.
 */
public final class VttParser {

    private static final Pattern TIMESTAMP_FULL = Pattern.compile(
            "^(\\d{2}):(\\d{2}):(\\d{2})\\.(\\d{3})\\s+-->\\s+(\\d{2}):(\\d{2}):(\\d{2})\\.(\\d{3})(?:\\s+.*)?$");
    private static final Pattern TIMESTAMP_SHORT = Pattern.compile(
            "^(\\d{2}):(\\d{2})\\.(\\d{3})\\s+-->\\s+(\\d{2}):(\\d{2})\\.(\\d{3})(?:\\s+.*)?$");
    private static final Pattern TIMESTAMP_LOOSE = Pattern.compile("-->");
    private static final Pattern SPEAKER_V = Pattern.compile("^<v(?:\\s+([^>]+))?>(.*)$");
    private static final Pattern SPEAKER_COLON = Pattern.compile("^([^:]{1,80}):\\s+(.*)$");

    private VttParser() {
    }

    public static VttParseResult parse(TenantId tenantId, TranscriptId transcriptId, byte[] rawBytes) {
        String text = new String(rawBytes, StandardCharsets.UTF_8);
        if (text.startsWith("\uFEFF")) {
            text = text.substring(1);
        }
        String trimmedStart = text.stripLeading();
        if (!trimmedStart.toUpperCase(Locale.ROOT).startsWith("WEBVTT")) {
            throw new TranscriptDomainException("MALFORMED_VTT", "VTT must start with WEBVTT");
        }

        String normalizedNewlines = text.replace("\r\n", "\n").replace('\r', '\n');
        String[] blocks = normalizedNewlines.split("\n\n+");
        List<NormalizationIssue> issues = new ArrayList<>();
        List<RawCue> cues = new ArrayList<>();

        for (String block : blocks) {
            String[] lines = block.split("\n");
            int tsLine = -1;
            for (int i = 0; i < lines.length; i++) {
                String trimmed = lines[i].trim();
                if (TIMESTAMP_FULL.matcher(trimmed).matches()
                        || TIMESTAMP_SHORT.matcher(trimmed).matches()
                        || TIMESTAMP_LOOSE.matcher(trimmed).find()) {
                    tsLine = i;
                    break;
                }
            }
            if (tsLine < 0) {
                continue;
            }

            String ts = lines[tsLine].trim();
            Optional<long[]> timing = parseTimestamp(ts);
            if (timing.isEmpty()) {
                issues.add(NormalizationIssue.of(
                        NormalizationIssueType.MALFORMED_TIMESTAMP,
                        "Malformed timestamp: " + redactTiming(ts),
                        null,
                        null,
                        false));
                continue;
            }
            long start = timing.get()[0];
            long end = timing.get()[1];
            if (end < start) {
                issues.add(NormalizationIssue.of(
                        NormalizationIssueType.INVALID_TIME_RANGE,
                        "endOffsetMs < startOffsetMs",
                        null,
                        null,
                        false));
                continue;
            }

            String speaker = null;
            StringBuilder content = new StringBuilder();
            for (int i = tsLine + 1; i < lines.length; i++) {
                String line = lines[i];
                if (line.isBlank()) {
                    continue;
                }
                String working = line.trim();
                Matcher v = SPEAKER_V.matcher(working);
                if (v.matches()) {
                    String name = v.group(1);
                    if (name != null && !name.isBlank()) {
                        speaker = WhitespaceNormalizer.normalize(name);
                    }
                    working = v.group(2) == null ? "" : v.group(2).trim();
                } else if (speaker == null) {
                    Matcher colon = SPEAKER_COLON.matcher(working);
                    if (colon.matches() && looksLikeSpeaker(colon.group(1))) {
                        speaker = WhitespaceNormalizer.normalize(colon.group(1));
                        working = colon.group(2).trim();
                    }
                }
                if (working.isEmpty()) {
                    continue;
                }
                if (!content.isEmpty()) {
                    content.append('\n');
                }
                content.append(working);
            }

            String rawContent = content.toString();
            String normalizedContent = WhitespaceNormalizer.normalize(rawContent);
            if (normalizedContent.isEmpty()) {
                issues.add(NormalizationIssue.of(
                        NormalizationIssueType.EMPTY_CONTENT,
                        "Cue has empty content after whitespace normalization",
                        null,
                        null,
                        false));
                continue;
            }

            cues.add(new RawCue(speaker, start, end, normalizedContent, !rawContent.equals(normalizedContent)));
        }

        if (cues.isEmpty()) {
            throw new TranscriptDomainException("MALFORMED_VTT", "VTT contains no cues");
        }

        cues.sort(Comparator
                .comparingLong((RawCue c) -> c.startOffsetMs)
                .thenComparingLong(c -> c.endOffsetMs)
                .thenComparing(c -> c.content));

        List<RawCue> deduped = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        int duplicatesRemoved = 0;
        int whitespaceNormalizedCount = 0;
        for (RawCue cue : cues) {
            if (cue.whitespaceChanged) {
                whitespaceNormalizedCount++;
            }
            String key = cue.startOffsetMs + "|" + cue.endOffsetMs + "|"
                    + (cue.speaker == null ? "" : cue.speaker) + "|" + cue.content;
            if (!seen.add(key)) {
                duplicatesRemoved++;
                issues.add(NormalizationIssue.of(
                        NormalizationIssueType.DUPLICATE_SEGMENT,
                        "Duplicate segment removed",
                        null,
                        null,
                        false));
                continue;
            }
            deduped.add(cue);
        }

        for (int i = 1; i < deduped.size(); i++) {
            RawCue prev = deduped.get(i - 1);
            RawCue curr = deduped.get(i);
            if (curr.startOffsetMs < prev.endOffsetMs) {
                issues.add(NormalizationIssue.of(
                        NormalizationIssueType.OVERLAPPING_SEGMENTS,
                        "Segment overlaps previous cue",
                        i,
                        null,
                        false));
            }
        }

        List<TranscriptSegment> segments = new ArrayList<>();
        for (int i = 0; i < deduped.size(); i++) {
            RawCue cue = deduped.get(i);
            UUID segmentId = UUID.randomUUID();
            segments.add(new TranscriptSegment(
                    segmentId,
                    tenantId,
                    transcriptId,
                    i,
                    null,
                    cue.speaker,
                    cue.startOffsetMs,
                    cue.endOffsetMs,
                    cue.content));
        }

        return new VttParseResult(segments, issues, whitespaceNormalizedCount, duplicatesRemoved);
    }

    static Optional<long[]> parseTimestamp(String line) {
        Matcher full = TIMESTAMP_FULL.matcher(line);
        if (full.matches()) {
            long start = toMillis(full.group(1), full.group(2), full.group(3), full.group(4));
            long end = toMillis(full.group(5), full.group(6), full.group(7), full.group(8));
            return Optional.of(new long[]{start, end});
        }
        Matcher shortTs = TIMESTAMP_SHORT.matcher(line);
        if (shortTs.matches()) {
            long start = toMillis("00", shortTs.group(1), shortTs.group(2), shortTs.group(3));
            long end = toMillis("00", shortTs.group(4), shortTs.group(5), shortTs.group(6));
            return Optional.of(new long[]{start, end});
        }
        return Optional.empty();
    }

    private static long toMillis(String hh, String mm, String ss, String mss) {
        return Long.parseLong(hh) * 3_600_000L
                + Long.parseLong(mm) * 60_000L
                + Long.parseLong(ss) * 1_000L
                + Long.parseLong(mss);
    }

    private static boolean looksLikeSpeaker(String candidate) {
        String trimmed = candidate.trim();
        if (trimmed.length() < 2 || trimmed.length() > 64) {
            return false;
        }
        if (trimmed.contains("<") || trimmed.contains(">")) {
            return false;
        }
        // Avoid treating ordinary sentences as speakers.
        return !trimmed.contains(" ") || trimmed.split("\\s+").length <= 4;
    }

    private static String redactTiming(String ts) {
        // Timing lines are not transcript content; safe to keep short form for diagnostics.
        return ts.length() > 64 ? ts.substring(0, 64) : ts;
    }

    private record RawCue(
            String speaker,
            long startOffsetMs,
            long endOffsetMs,
            String content,
            boolean whitespaceChanged
    ) {
    }
}
