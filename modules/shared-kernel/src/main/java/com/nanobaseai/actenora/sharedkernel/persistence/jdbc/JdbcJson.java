package com.nanobaseai.actenora.sharedkernel.persistence.jdbc;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;

/** JSON helpers for JDBC column binding. */
public final class JdbcJson {

    private static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules();

    private JdbcJson() {
    }

    public static String write(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("JSON serialization failed", ex);
        }
    }

    public static <T> T read(String json, Class<T> type) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readValue(json, type);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("JSON deserialization failed for " + type.getSimpleName(), ex);
        }
    }

    public static Map<String, Object> readMap(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return MAPPER.readValue(json, new TypeReference<>() {});
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("JSON map deserialization failed", ex);
        }
    }
}
