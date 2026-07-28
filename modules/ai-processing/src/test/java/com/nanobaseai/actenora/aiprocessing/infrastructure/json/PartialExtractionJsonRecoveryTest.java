package com.nanobaseai.actenora.aiprocessing.infrastructure.json;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PartialExtractionJsonRecoveryTest {

    private final PartialExtractionJsonRecovery recovery = new PartialExtractionJsonRecovery();

    @Test
    void recoversClosedObjectsDropsTrailingHalfObject() {
        String truncated = """
                {
                  "topics": [],
                  "decisions": [
                    {"text":"A","evidenceSegmentIds":["seg-1"],"confidence":0.9},
                    {"text":"B","evidenceSegmentIds":["seg-1"],"confidence":0.9},
                    {"text":"C","evidenceSegmentIds":["seg-1"],"confidence":0.9
                  ],
                  "actionItems": [],
                  "risks": [],
                  "openQuestions": [],
                  "commitments": [],
                  "qualityFlags": [],
                  "evidenceSegmentIds": ["seg-1"],
                  "confidence": 0.9
                }
                """;

        List<String> closed = PartialExtractionJsonRecovery.extractClosedObjectsInArray(truncated, "decisions");
        assertEquals(2, closed.size());

        Optional<String> recovered = recovery.recover(truncated);
        assertTrue(recovered.isPresent());
        assertTrue(recovered.get().contains("\"text\":\"A\""));
        assertTrue(recovered.get().contains("\"text\":\"B\""));
        assertTrue(recovered.get().contains("PARTIAL_JSON_RECOVERED"));
        assertTrue(!recovered.get().contains("\"text\":\"C\"")
                || recovered.get().indexOf("\"text\":\"C\"") < 0);
    }

    @Test
    void returnsEmptyWhenNoClosedItems() {
        Optional<String> recovered = recovery.recover("""
                {
                  "decisions": [
                    {"text":"half
                """);
        assertTrue(recovered.isEmpty());
    }
}
