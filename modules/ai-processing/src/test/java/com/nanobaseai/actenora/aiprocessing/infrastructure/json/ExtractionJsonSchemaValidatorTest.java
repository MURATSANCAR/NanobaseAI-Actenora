package com.nanobaseai.actenora.aiprocessing.infrastructure.json;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExtractionJsonSchemaValidatorTest {

    private final ExtractionJsonSchemaValidator validator = new ExtractionJsonSchemaValidator();

    @Test
    void defaultsMissingRootConfidence() {
        String json = """
                {
                  "topics": [],
                  "decisions": [],
                  "actionItems": [],
                  "risks": [],
                  "openQuestions": [],
                  "commitments": [],
                  "qualityFlags": [],
                  "evidenceSegmentIds": []
                }
                """;
        JsonNode node = validator.parseAndValidate(json);
        assertEquals(0.5d, node.get("confidence").asDouble(), 0.0001d);
        assertTrue(node.get("qualityFlags").toString().contains("confidence_defaulted"));
    }

    @Test
    void coercesStringRiskItemsUsingRootEvidence() {
        String json = """
                {
                  "topics": [],
                  "decisions": [],
                  "actionItems": [],
                  "risks": ["Delivery slip"],
                  "openQuestions": [],
                  "commitments": [],
                  "qualityFlags": [],
                  "evidenceSegmentIds": ["seg-1"],
                  "confidence": 0.8
                }
                """;
        JsonNode node = validator.parseAndValidate(json);
        assertEquals(1, node.get("risks").size());
        assertEquals("Delivery slip", node.get("risks").get(0).get("text").asText());
        assertEquals("seg-1", node.get("risks").get(0).get("evidenceSegmentIds").get(0).asText());
        assertTrue(node.get("qualityFlags").toString().contains("risks_items_coerced"));
    }
}
