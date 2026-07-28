package com.nanobaseai.actenora.aiprocessing.domain.pipeline;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EvidenceReferenceScrubberTest {

    private static final String REAL_A =
            "a99ddd05-a8be-4fb2-a0d3-8db4878e25a0";
    private static final String REAL_B =
            "8526110e-47c4-8b9c-9bbe-c2c3b8ce45d2";
    private static final String REAL_PREFIX_TARGET =
            "bf3e07b9-a755-d12bcb3e5b46";

    @Test
    void unknownEvidenceIdDropsItemKeepsBundle() {
        ExtractionBundle bundle = decisions(
                decision("Keep me", List.of(REAL_A)),
                decision("Drop me", List.of(UUID.randomUUID().toString()))
        );
        EvidenceReferenceScrubber.Outcome outcome = new EvidenceReferenceScrubber()
                .scrub(bundle, Set.of(REAL_A, REAL_B));

        assertEquals(1, outcome.bundle().decisions().size());
        assertEquals("Keep me", outcome.bundle().decisions().get(0).text());
        assertEquals(1, outcome.droppedItems());
        assertTrue(outcome.droppedRefs() >= 1);
        assertTrue(outcome.bundle().qualityFlags().contains("EVIDENCE_ITEM_SOFT_DROPPED"));
    }

    @Test
    void oneBadItemTwoGoodItemsKeepsSurvivors() {
        ExtractionBundle bundle = decisions(
                decision("A", List.of(REAL_A)),
                decision("B", List.of("totally-unknown")),
                decision("C", List.of(REAL_B))
        );
        EvidenceReferenceScrubber.Outcome outcome = new EvidenceReferenceScrubber()
                .scrub(bundle, Set.of(REAL_A, REAL_B));

        assertEquals(2, outcome.bundle().decisions().size());
        assertEquals(List.of("A", "C"), outcome.bundle().decisions().stream()
                .map(DecisionCandidate::text).toList());
    }

    @Test
    void ambiguousPrefixDoesNotCorrectSoftDrops() {
        String shared = "aaaaaaaa-bbbb-cccc";
        String one = shared + "-111111111111";
        String two = shared + "-222222222222";
        ExtractionBundle bundle = decisions(decision("Ambiguous", List.of(shared)));
        EvidenceReferenceScrubber.Outcome outcome = new EvidenceReferenceScrubber()
                .scrub(bundle, Set.of(one, two));

        assertTrue(outcome.bundle().decisions().isEmpty());
        assertEquals(0, outcome.correctedRefs());
        assertTrue(outcome.droppedRefs() >= 1);
    }

    @Test
    void uniqueTruncatedPrefixCompletesToRealId() {
        String truncated = REAL_PREFIX_TARGET.substring(0, REAL_PREFIX_TARGET.length() - 2);
        assertTrue(truncated.length() >= 12);
        ExtractionBundle bundle = decisions(decision("Prefix", List.of(truncated)));
        EvidenceReferenceScrubber.Outcome outcome = new EvidenceReferenceScrubber()
                .scrub(bundle, Set.of(REAL_PREFIX_TARGET, REAL_A));

        assertEquals(1, outcome.bundle().decisions().size());
        assertEquals(List.of(REAL_PREFIX_TARGET), outcome.bundle().decisions().get(0).evidenceSegmentIds());
        assertEquals(1, outcome.correctedRefs());
        assertTrue(outcome.bundle().qualityFlags().contains("EVIDENCE_REF_CORRECTED"));
    }

    @Test
    void levenshteinTwoWithFuzzyDisabledSoftDrops() {
        // 8b9c vs 9b9e — two hex diffs (1h EVAL case)
        String mutated = REAL_B.replace("8b9c", "9b9e");
        assertEquals(2, EvidenceReferenceScrubber.levenshtein(mutated, REAL_B));
        ExtractionBundle bundle = decisions(decision("Near miss", List.of(mutated)));
        EvidenceReferenceScrubber.Outcome outcome = new EvidenceReferenceScrubber(
                EvidenceNearMissConfig.disabled()
        ).scrub(bundle, Set.of(REAL_B));

        assertTrue(outcome.bundle().decisions().isEmpty());
        assertEquals(0, outcome.correctedRefs());
    }

    @Test
    void levenshteinTwoWithFuzzyEnabledCorrectsUniqueCandidate() {
        String mutated = REAL_B.replace("8b9c", "9b9e");
        EvidenceNearMissConfig cfg = new EvidenceNearMissConfig(true, 2, 12, true, true);
        ExtractionBundle bundle = decisions(decision("Near miss", List.of(mutated)));
        EvidenceReferenceScrubber.Outcome outcome = new EvidenceReferenceScrubber(cfg)
                .scrub(bundle, Set.of(REAL_B, REAL_A));

        assertEquals(1, outcome.bundle().decisions().size());
        assertEquals(List.of(REAL_B), outcome.bundle().decisions().get(0).evidenceSegmentIds());
        assertEquals(1, outcome.correctedRefs());
    }

    private static DecisionCandidate decision(String text, List<String> evidence) {
        return new DecisionCandidate(text, evidence, 0.9d);
    }

    private static ExtractionBundle decisions(DecisionCandidate... decisions) {
        return new ExtractionBundle(
                List.of(),
                List.of(decisions),
                List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(),
                List.of(),
                List.of(),
                0.9d
        );
    }
}
