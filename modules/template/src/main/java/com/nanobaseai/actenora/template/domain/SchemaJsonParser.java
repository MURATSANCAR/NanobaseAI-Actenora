package com.nanobaseai.actenora.template.domain;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Parses Template Studio JSON documents into validated domain schemas.
 */
public final class SchemaJsonParser {

    private final ObjectMapper mapper;

    public SchemaJsonParser(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public DesignSchema parseDesign(String json) {
        try {
            JsonNode root = mapper.readTree(json);
            int version = root.path("schemaVersion").asInt(DesignSchema.CURRENT_SCHEMA_VERSION);
            String pageSize = root.path("page").path("size").asText("A4");
            if (root.has("pageSize")) {
                pageSize = root.path("pageSize").asText(pageSize);
            }
            List<DesignComponent> components = new ArrayList<>();
            JsonNode array = root.path("components");
            if (!array.isArray()) {
                throw new TemplateDomainException("INVALID_DESIGN", "components must be an array");
            }
            for (JsonNode node : array) {
                UUID id = UUID.fromString(node.path("id").asText());
                TemplateComponentType type = TemplateComponentType.fromWire(node.path("type").asText())
                        .orElseThrow(() -> new TemplateDomainException(
                                "INVALID_DESIGN", "Unknown component type: " + node.path("type").asText()));
                int order = node.path("order").asInt();
                Map<String, String> props = new LinkedHashMap<>();
                JsonNode propsNode = node.path("props");
                if (propsNode.isObject()) {
                    Iterator<Map.Entry<String, JsonNode>> fields = propsNode.fields();
                    while (fields.hasNext()) {
                        Map.Entry<String, JsonNode> field = fields.next();
                        props.put(field.getKey(), field.getValue().asText(""));
                    }
                }
                components.add(new DesignComponent(id, type, order, props));
            }
            return new DesignSchema(version, pageSize, components);
        } catch (TemplateDomainException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new TemplateDomainException("INVALID_DESIGN", "Failed to parse design schema: " + ex.getMessage(), ex);
        }
    }

    public ContentSchema parseContent(String json) {
        if (json == null || json.isBlank()) {
            return ContentSchema.empty();
        }
        try {
            JsonNode root = mapper.readTree(json);
            int version = root.path("schemaVersion").asInt(ContentSchema.CURRENT_SCHEMA_VERSION);
            Map<String, String> bindings = new LinkedHashMap<>();
            JsonNode bindingsNode = root.path("bindings");
            if (bindingsNode.isObject()) {
                Iterator<Map.Entry<String, JsonNode>> fields = bindingsNode.fields();
                while (fields.hasNext()) {
                    Map.Entry<String, JsonNode> field = fields.next();
                    JsonNode value = field.getValue();
                    if (value.isObject()) {
                        bindings.put(field.getKey(), value.path("source").asText());
                    } else {
                        bindings.put(field.getKey(), value.asText());
                    }
                }
            }
            return new ContentSchema(version, bindings);
        } catch (TemplateDomainException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new TemplateDomainException(
                    "INVALID_CONTENT_SCHEMA", "Failed to parse content schema: " + ex.getMessage(), ex);
        }
    }
}
