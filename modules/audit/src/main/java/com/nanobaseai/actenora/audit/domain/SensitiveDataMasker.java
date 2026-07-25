package com.nanobaseai.actenora.audit.domain;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Masks sensitive values in audit metadata (secrets, tokens, credentials). */
public final class SensitiveDataMasker {

    public static final String MASK = "***";

    private static final Set<String> SENSITIVE_KEYS = Set.of(
            "password", "secret", "token", "accesstoken", "refreshtoken",
            "apikey", "api_key", "authorization", "clientsecret", "client_secret",
            "privatekey", "private_key"
    );

    private SensitiveDataMasker() {}

    public static Map<String, Object> mask(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : metadata.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (isSensitive(key)) {
                result.put(key, MASK);
            } else if (value instanceof Map<?, ?> nested) {
                @SuppressWarnings("unchecked")
                Map<String, Object> nestedMap = (Map<String, Object>) nested;
                result.put(key, mask(nestedMap));
            } else {
                result.put(key, value);
            }
        }
        return result;
    }

    public static boolean isSensitive(String key) {
        if (key == null) {
            return false;
        }
        String normalized = key.toLowerCase(Locale.ROOT).replace("-", "").replace("_", "");
        if (SENSITIVE_KEYS.contains(key.toLowerCase(Locale.ROOT)) || SENSITIVE_KEYS.contains(normalized)) {
            return true;
        }
        return normalized.contains("password")
                || normalized.contains("secret")
                || normalized.contains("apikey")
                || normalized.endsWith("token");
    }
}
