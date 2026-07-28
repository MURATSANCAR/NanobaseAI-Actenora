package com.nanobaseai.actenora.aiprocessing.infrastructure.json;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Narrow, deterministic recovery for truncated extraction JSON:
 * keep closed array objects, drop the trailing half-object, validate survivors one-by-one.
 */
public final class PartialExtractionJsonRecovery {

    private static final Set<String> RECORD_ARRAYS = Set.of(
            "topics", "decisions", "actionItems", "risks", "openQuestions", "commitments",
            "issues", "proposals", "importantFacts"
    );

    private final ObjectMapper objectMapper;
    private final ExtractionJsonSchemaValidator schemaValidator;

    public PartialExtractionJsonRecovery() {
        this(new ObjectMapper(), new ExtractionJsonSchemaValidator());
    }

    public PartialExtractionJsonRecovery(ObjectMapper objectMapper, ExtractionJsonSchemaValidator schemaValidator) {
        this.objectMapper = objectMapper;
        this.schemaValidator = schemaValidator;
    }

    /**
     * @return recovered JSON object text when at least one valid record was salvaged
     */
    public Optional<String> recover(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String text = stripToObject(raw.trim());
        if (text.isEmpty()) {
            return Optional.empty();
        }

        ObjectNode root = objectMapper.createObjectNode();
        boolean anyItem = false;
        for (String field : RECORD_ARRAYS) {
            ArrayNode kept = objectMapper.createArrayNode();
            for (String objectJson : extractClosedObjectsInArray(text, field)) {
                try {
                    JsonNode item = objectMapper.readTree(objectJson);
                    if (item != null && item.isObject() && itemHasRequiredText(item)) {
                        kept.add(item);
                        anyItem = true;
                    }
                } catch (Exception ignored) {
                    // drop invalid closed fragment
                }
            }
            root.set(field, kept);
        }
        if (!anyItem) {
            return Optional.empty();
        }

        root.putArray("qualityFlags").add("PARTIAL_JSON_RECOVERED");
        root.putArray("evidenceSegmentIds");
        root.put("confidence", 0.5d);

        // Collect root evidence from recovered items.
        LinkedEvidence evidence = new LinkedEvidence();
        for (String field : RECORD_ARRAYS) {
            JsonNode arr = root.get(field);
            if (arr == null || !arr.isArray()) {
                continue;
            }
            for (JsonNode item : arr) {
                JsonNode ev = item.get("evidenceSegmentIds");
                if (ev != null && ev.isArray()) {
                    for (JsonNode id : ev) {
                        if (id != null && id.isTextual() && !id.asText().isBlank()) {
                            evidence.add(id.asText());
                        }
                    }
                }
            }
        }
        ArrayNode rootEv = root.putArray("evidenceSegmentIds");
        for (String id : evidence.ids()) {
            rootEv.add(id);
        }

        try {
            JsonNode validated = schemaValidator.parseAndValidate(root.toString());
            return Optional.of(validated.toString());
        } catch (RuntimeException ex) {
            // Filter items that individually fail by rebuilding with only schema-valid singles.
            return filterPerItem(root);
        }
    }

    private Optional<String> filterPerItem(ObjectNode root) {
        ObjectNode rebuilt = objectMapper.createObjectNode();
        boolean any = false;
        for (String field : RECORD_ARRAYS) {
            ArrayNode kept = objectMapper.createArrayNode();
            JsonNode arr = root.get(field);
            if (arr != null && arr.isArray()) {
                for (JsonNode item : arr) {
                    ObjectNode probe = skeleton();
                    ArrayNode single = probe.putArray(field);
                    single.add(item.deepCopy());
                    probe.putArray("qualityFlags").add("PARTIAL_JSON_RECOVERED");
                    probe.putArray("evidenceSegmentIds");
                    probe.put("confidence", 0.5d);
                    try {
                        schemaValidator.parseAndValidate(probe.toString());
                        kept.add(item);
                        any = true;
                    } catch (RuntimeException ignored) {
                        // drop
                    }
                }
            }
            rebuilt.set(field, kept);
        }
        if (!any) {
            return Optional.empty();
        }
        rebuilt.putArray("qualityFlags").add("PARTIAL_JSON_RECOVERED");
        rebuilt.putArray("evidenceSegmentIds");
        rebuilt.put("confidence", 0.5d);
        try {
            return Optional.of(schemaValidator.parseAndValidate(rebuilt.toString()).toString());
        } catch (RuntimeException ex) {
            return Optional.empty();
        }
    }

    private ObjectNode skeleton() {
        ObjectNode node = objectMapper.createObjectNode();
        for (String field : RECORD_ARRAYS) {
            node.putArray(field);
        }
        node.putArray("qualityFlags");
        node.putArray("evidenceSegmentIds");
        node.put("confidence", 0.5d);
        return node;
    }

    private static boolean itemHasRequiredText(JsonNode item) {
        JsonNode text = item.get("text");
        return text != null && text.isTextual() && !text.asText().isBlank();
    }

    static List<String> extractClosedObjectsInArray(String text, String fieldName) {
        String needle = "\"" + fieldName + "\"";
        int fieldAt = indexOfField(text, needle);
        if (fieldAt < 0) {
            return List.of();
        }
        int colon = text.indexOf(':', fieldAt + needle.length());
        if (colon < 0) {
            return List.of();
        }
        int i = colon + 1;
        while (i < text.length() && Character.isWhitespace(text.charAt(i))) {
            i++;
        }
        if (i >= text.length() || text.charAt(i) != '[') {
            return List.of();
        }
        i++; // after [

        List<String> objects = new ArrayList<>();
        while (i < text.length()) {
            while (i < text.length() && Character.isWhitespace(text.charAt(i))) {
                i++;
            }
            if (i >= text.length()) {
                break;
            }
            char c = text.charAt(i);
            if (c == ']') {
                break;
            }
            if (c == ',') {
                i++;
                continue;
            }
            if (c != '{') {
                // truncated / garbage — stop
                break;
            }
            int end = findMatchingBrace(text, i);
            if (end < 0) {
                // trailing half-object — drop it
                break;
            }
            objects.add(text.substring(i, end + 1));
            i = end + 1;
        }
        return objects;
    }

    private static int indexOfField(String text, String needle) {
        boolean inString = false;
        boolean escape = false;
        for (int i = 0; i <= text.length() - needle.length(); i++) {
            char c = text.charAt(i);
            if (inString) {
                if (escape) {
                    escape = false;
                } else if (c == '\\') {
                    escape = true;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }
            if (c == '"') {
                if (text.startsWith(needle, i)) {
                    return i;
                }
                inString = true;
            }
        }
        return -1;
    }

    private static int findMatchingBrace(String text, int openAt) {
        int depth = 0;
        int nestedArray = 0;
        boolean inString = false;
        boolean escape = false;
        for (int i = openAt; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inString) {
                if (escape) {
                    escape = false;
                } else if (c == '\\') {
                    escape = true;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }
            if (c == '"') {
                inString = true;
            } else if (c == '[') {
                nestedArray++;
            } else if (c == ']') {
                if (nestedArray > 0) {
                    nestedArray--;
                } else if (depth > 0) {
                    // Parent array closed while this object is still open → truncated item.
                    return -1;
                }
            } else if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    private static String stripToObject(String raw) {
        String text = raw;
        if (text.startsWith("```")) {
            text = text.replaceFirst("^```(?:json)?\\s*", "");
            int close = text.lastIndexOf("```");
            if (close >= 0) {
                text = text.substring(0, close).trim();
            }
        }
        int start = text.indexOf('{');
        return start >= 0 ? text.substring(start) : "";
    }

    private static final class LinkedEvidence {
        private final List<String> ids = new ArrayList<>();

        void add(String id) {
            if (!ids.contains(id)) {
                ids.add(id);
            }
        }

        List<String> ids() {
            return ids;
        }
    }
}
