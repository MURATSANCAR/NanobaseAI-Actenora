package com.nanobaseai.actenora.aiprocessing.infrastructure.json;

import com.nanobaseai.actenora.aiprocessing.domain.pipeline.FailureCategory;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.PipelineException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LimitedJsonRepairTest {

    private final LimitedJsonRepair repair = new LimitedJsonRepair();

    @Test
    void stripsMarkdownFence() {
        String repaired = repair.repairOrThrow("```json\n{\"a\":1}\n```");
        assertEquals("{\"a\":1}", repaired);
    }

    @Test
    void removesTrailingCommas() {
        String repaired = repair.repairOrThrow("{\"a\":1,}");
        assertEquals("{\"a\":1}", repaired);
    }

    @Test
    void unrecoverableThrowsInvalidJson() {
        PipelineException ex = assertThrows(PipelineException.class, () -> repair.repairOrThrow("nope"));
        assertEquals(FailureCategory.INVALID_JSON, ex.category());
    }

    @Test
    void schemaValidatorRejectsInvalidEnum() {
        ExtractionJsonSchemaValidator validator = new ExtractionJsonSchemaValidator();
        PipelineException ex = assertThrows(
                PipelineException.class,
                () -> validator.parseAndValidate("""
                        {
                          "topics": [],
                          "decisions": [],
                          "actionItems": [{
                            "text": "Ship it",
                            "ownerType": "CONTRACTOR",
                            "evidenceSegmentIds": ["seg-1"]
                          }],
                          "risks": [],
                          "openQuestions": [],
                          "commitments": [],
                          "qualityFlags": [],
                          "evidenceSegmentIds": ["seg-1"],
                          "confidence": 0.8
                        }
                        """)
        );
        assertEquals(FailureCategory.SCHEMA_VIOLATION, ex.category());
        assertTrue(ex.getMessage().contains("ownerType"));
    }
}
