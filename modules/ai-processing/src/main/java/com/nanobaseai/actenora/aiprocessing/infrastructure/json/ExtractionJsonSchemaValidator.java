package com.nanobaseai.actenora.aiprocessing.infrastructure.json;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
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

    /** Optional extended arrays — coerced when present, never required. */
    private static final Set<String> OPTIONAL_RECORD_ARRAYS = Set.of(
            "issues",
            "proposals",
            "importantFacts"
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
        if (node instanceof ObjectNode objectNode) {
            normalizeTolerantDefaults(objectNode);
        }
        validate(node);
        return node;
    }

    /**
     * Local models often omit root {@code confidence} or emit plain strings in record arrays.
     * Normalize those shapes so otherwise-useful extractions are not fail-closed.
     */
    private void normalizeTolerantDefaults(ObjectNode node) {
        for (String arrayField : RECORD_ARRAYS) {
            if (!node.has(arrayField) || node.get(arrayField).isNull()) {
                node.putArray(arrayField);
            }
        }
        for (String arrayField : OPTIONAL_RECORD_ARRAYS) {
            if (node.has(arrayField) && node.get(arrayField).isNull()) {
                node.putArray(arrayField);
            }
        }
        if (!node.has("qualityFlags") || node.get("qualityFlags").isNull()) {
            node.putArray("qualityFlags");
        }
        if (!node.has("evidenceSegmentIds") || node.get("evidenceSegmentIds").isNull()) {
            node.putArray("evidenceSegmentIds");
        }
        if (!node.has("normalizationIssues") || node.get("normalizationIssues").isNull()) {
            node.putArray("normalizationIssues");
        }
        ArrayNode qualityFlags = (ArrayNode) node.get("qualityFlags");
        ArrayNode rootEvidence = (ArrayNode) node.get("evidenceSegmentIds");

        for (String arrayField : RECORD_ARRAYS) {
            normalizeRecordArray(node, arrayField, rootEvidence, qualityFlags);
        }
        for (String arrayField : OPTIONAL_RECORD_ARRAYS) {
            if (node.has(arrayField)) {
                normalizeRecordArray(node, arrayField, rootEvidence, qualityFlags);
            }
        }

        if (!node.has("confidence") || node.get("confidence").isNull()) {
            node.put("confidence", 0.5d);
            qualityFlags.add("confidence_defaulted");
        }
    }

    private void normalizeRecordArray(
            ObjectNode node,
            String arrayField,
            ArrayNode rootEvidence,
            ArrayNode qualityFlags
    ) {
        JsonNode arrayNode = node.get(arrayField);
        if (!(arrayNode instanceof ArrayNode array)) {
            return;
        }
        ArrayNode normalized = objectMapper.createArrayNode();
        boolean coerced = false;
        boolean dropped = false;
        for (JsonNode item : array) {
            ObjectNode record = coerceRecordItem(item, rootEvidence);
            if (record == null) {
                dropped = true;
                continue;
            }
            if (item.isTextual() || !item.isObject()) {
                coerced = true;
            }
            normalized.add(record);
        }
        node.set(arrayField, normalized);
        if (coerced) {
            qualityFlags.add(arrayField + "_items_coerced");
        }
        if (dropped) {
            qualityFlags.add(arrayField + "_items_dropped");
        }
    }

    private ObjectNode coerceRecordItem(JsonNode item, ArrayNode rootEvidence) {
        if (item == null || item.isNull()) {
            return null;
        }
        ObjectNode record;
        if (item.isTextual()) {
            String text = item.asText();
            if (text == null || text.isBlank()) {
                return null;
            }
            record = objectMapper.createObjectNode();
            record.put("text", text.trim());
        } else if (item.isObject()) {
            record = (ObjectNode) item.deepCopy();
            if (!record.hasNonNull("text") || !record.get("text").isTextual()
                    || record.get("text").asText().isBlank()) {
                return null;
            }
        } else {
            return null;
        }

        if (!record.has("evidenceSegmentIds") || !record.get("evidenceSegmentIds").isArray()
                || record.get("evidenceSegmentIds").isEmpty()) {
            ArrayNode evidence = objectMapper.createArrayNode();
            if (rootEvidence != null) {
                for (JsonNode id : rootEvidence) {
                    if (id != null && id.isTextual() && !id.asText().isBlank()) {
                        evidence.add(id.asText());
                    }
                }
            }
            if (!evidence.isEmpty()) {
                record.set("evidenceSegmentIds", evidence);
            } else if (!record.has("evidenceSegmentIds") || !record.get("evidenceSegmentIds").isArray()) {
                // Keep empty array so deterministic validation can fail closed.
                record.putArray("evidenceSegmentIds");
            }
        }
        if (!record.has("confidence") || record.get("confidence").isNull()) {
            record.put("confidence", 0.7d);
        }
        return record;
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
        for (String arrayField : OPTIONAL_RECORD_ARRAYS) {
            if (!node.has(arrayField)) {
                continue;
            }
            JsonNode array = node.get(arrayField);
            if (!array.isArray()) {
                throw schemaViolation(arrayField + " must be an array");
            }
            for (JsonNode item : array) {
                validateRecord(arrayField, item);
            }
        }
        if (node.has("normalizationIssues") && !node.get("normalizationIssues").isArray()) {
            throw schemaViolation("normalizationIssues must be an array");
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
        assertNullOrText(item, "candidateId");
        if ("topics".equals(field)) {
            assertNullOrText(item, "summary");
        }
        if ("decisions".equals(field)) {
            assertNullOrText(item, "rationale");
            assertNullOrText(item, "status");
        }
        if ("actionItems".equals(field)) {
            assertNullOrText(item, "owner");
            assertNullOrText(item, "ownerType");
            assertNullOrText(item, "dueDate");
            assertNullOrText(item, "relativeDate");
            assertNullOrText(item, "priority");
        }
        if ("risks".equals(field)) {
            assertNullOrText(item, "likelihood");
            assertNullOrText(item, "mitigation");
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
