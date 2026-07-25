package com.nanobaseai.actenora.aiprocessing.infrastructure.json;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.FailureCategory;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.PipelineException;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.PipelineStage;

import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.Objects;
import java.util.Set;

/**
 * Fail-closed validation against the published extraction JSON Schema resource.
 */
public final class ExtractionJsonSchemaValidator {

    private static final Set<String> REQUIRED_ROOT = Set.of(
            "topics",
            "decisions",
            "actionItems",
            "risks",
            "openQuestions",
            "commitments",
            "qualityFlags",
            "evidenceSegmentIds",
            "confidence"
    );

    private static final Set<String> RECORD_ARRAYS = Set.of(
            "topics",
            "decisions",
            "actionItems",
            "risks",
            "openQuestions",
            "commitments"
    );

    private final ObjectMapper objectMapper;
    private final JsonNode schemaRoot;

    public ExtractionJsonSchemaValidator(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.schemaRoot = loadSchema();
    }

    public ExtractionJsonSchemaValidator() {
        this(new ObjectMapper());
    }

    public JsonNode schemaRoot() {
        return schemaRoot;
    }

    public JsonNode parseAndValidate(String json) {
        JsonNode node;
        try {
            node = objectMapper.readTree(json);
        } catch (IOException ex) {
            throw new PipelineException(
                    FailureCategory.INVALID_JSON,
                    PipelineStage.EXTRACT,
                    "Invalid JSON: " + ex.getMessage()
            );
        }
        validate(node);
        return node;
    }

    public void validate(JsonNode node) {
        if (node == null || !node.isObject()) {
            throw schemaViolation("Root must be a JSON object");
        }
        for (String required : REQUIRED_ROOT) {
            if (!node.has(required)) {
                throw schemaViolation("Missing required property: " + required);
            }
        }
        for (String arrayField : RECORD_ARRAYS) {
            JsonNode array = node.get(arrayField);
            if (!array.isArray()) {
                throw schemaViolation(arrayField + " must be an array");
            }
            for (JsonNode item : array) {
                validateRecord(arrayField, item);
            }
        }
        if (!node.get("qualityFlags").isArray()) {
            throw schemaViolation("qualityFlags must be an array");
        }
        if (!node.get("evidenceSegmentIds").isArray()) {
            throw schemaViolation("evidenceSegmentIds must be an array");
        }
        JsonNode confidence = node.get("confidence");
        if (!confidence.isNumber()) {
            throw schemaViolation("confidence must be a number");
        }
        double value = confidence.asDouble();
        if (value < 0.0d || value > 1.0d) {
            throw schemaViolation("confidence must be between 0 and 1");
        }
    }

    private void validateRecord(String field, JsonNode item) {
        if (!item.isObject()) {
            throw schemaViolation(field + " items must be objects");
        }
        if (!item.hasNonNull("text") || !item.get("text").isTextual()) {
            throw schemaViolation(field + " item requires textual text");
        }
        if (!item.has("evidenceSegmentIds") || !item.get("evidenceSegmentIds").isArray()) {
            throw schemaViolation(field + " item requires evidenceSegmentIds array");
        }
        if (item.has("confidence") && !item.get("confidence").isNumber()) {
            throw schemaViolation(field + " item confidence must be a number");
        }
        if ("actionItems".equals(field)) {
            assertNullOrText(item, "owner");
            assertNullOrText(item, "dueDate");
        }
        if ("commitments".equals(field)) {
            assertNullOrText(item, "owner");
        }
    }

    private void assertNullOrText(JsonNode item, String field) {
        if (!item.has(field)) {
            return;
        }
        JsonNode value = item.get(field);
        if (!(value.isNull() || value.isTextual())) {
            throw schemaViolation(field + " must be string or null");
        }
    }

    private static PipelineException schemaViolation(String message) {
        return new PipelineException(
                FailureCategory.SCHEMA_VIOLATION,
                PipelineStage.EXTRACT,
                message
        );
    }

    private JsonNode loadSchema() {
        try (InputStream in = ExtractionJsonSchemaValidator.class.getResourceAsStream(
                "/aiprocessing/schemas/extraction-output.schema.json")) {
            if (in == null) {
                throw new IllegalStateException("Missing extraction-output.schema.json");
            }
            JsonNode schema = objectMapper.readTree(in);
            // Ensure schema declares required keys we enforce (self-check).
            JsonNode required = schema.get("required");
            if (required == null || !required.isArray()) {
                throw new IllegalStateException("Schema missing required array");
            }
            Iterator<JsonNode> it = required.elements();
            while (it.hasNext()) {
                REQUIRED_ROOT.contains(it.next().asText());
            }
            return schema;
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to load extraction schema", ex);
        }
    }
}
