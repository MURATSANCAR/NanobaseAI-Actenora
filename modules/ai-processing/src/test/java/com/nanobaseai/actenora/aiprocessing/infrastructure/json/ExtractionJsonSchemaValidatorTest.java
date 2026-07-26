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
}
