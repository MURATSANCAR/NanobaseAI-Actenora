package com.nanobaseai.actenora.aiprocessing.domain.pipeline.action;

import com.nanobaseai.actenora.aiprocessing.domain.pipeline.CommitmentCandidate;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.SegmentInput;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Binds commitment owner from evidence speaker only for explicit first-person future commitments.
 */
public final class CommitmentOwnerBinder {

    private static final Pattern FIRST_PERSON_COMMITMENT = Pattern.compile(
            "(?iu).*\\b([\\p{L}]+(?:ece[gğ]im|aca[gğ][ıi]m|yece[gğ]im|yaca[gğ][ıi]m))\\b.*"
    );

    public record Result(List<CommitmentCandidate> commitments, int bound) {
    }

    public Result bind(List<CommitmentCandidate> commitments, List<SegmentInput> segments) {
        Objects.requireNonNull(commitments, "commitments");
        Map<String, String> speakerById = indexSpeakers(segments);
        List<CommitmentCandidate> out = new ArrayList<>();
        int bound = 0;
        for (CommitmentCandidate c : commitments) {
            if (c.owner() != null && !c.owner().isBlank()) {
                out.add(c);
                continue;
            }
            if (!FIRST_PERSON_COMMITMENT.matcher(c.text()).matches()) {
                out.add(c);
                continue;
            }
            String speaker = resolveSpeaker(c.evidenceSegmentIds(), speakerById, segments, c.text());
            if (speaker == null || speaker.isBlank()) {
                out.add(c);
                continue;
            }
            out.add(new CommitmentCandidate(c.text(), speaker, c.evidenceSegmentIds(), c.confidence()));
            bound++;
        }
        return new Result(List.copyOf(out), bound);
    }

    private static Map<String, String> indexSpeakers(List<SegmentInput> segments) {
        Map<String, String> map = new LinkedHashMap<>();
        if (segments == null) {
            return map;
        }
        for (SegmentInput s : segments) {
            if (s.segmentId() != null && s.speakerDisplayName() != null && !s.speakerDisplayName().isBlank()) {
                map.put(s.segmentId(), s.speakerDisplayName().strip());
            }
        }
        return map;
    }

    private static String resolveSpeaker(
            List<String> evidenceIds,
            Map<String, String> speakerById,
            List<SegmentInput> segments,
            String text
    ) {
        for (String id : evidenceIds) {
            String speaker = speakerById.get(id);
            if (speaker != null) {
                return speaker;
            }
        }
        // Fallback: find segment whose content contains the commitment text.
        if (segments == null || text == null) {
            return null;
        }
        String needle = text.toLowerCase(Locale.ROOT).strip();
        for (SegmentInput s : segments) {
            if (s.content() != null && s.content().toLowerCase(Locale.ROOT).contains(needle)
                    && s.speakerDisplayName() != null && !s.speakerDisplayName().isBlank()) {
                return s.speakerDisplayName().strip();
            }
        }
        return null;
    }
}
