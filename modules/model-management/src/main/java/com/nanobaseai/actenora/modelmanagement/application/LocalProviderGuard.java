package com.nanobaseai.actenora.modelmanagement.application;

import com.nanobaseai.actenora.modelmanagement.domain.ModelRegistryException;

import java.util.Locale;
import java.util.Set;

/**
 * FAZ 11 / ADR-005 — reject obvious cloud LLM providers; local-only catalog.
 */
public final class LocalProviderGuard {

    private static final Set<String> BLOCKED_PROVIDER_FRAGMENTS = Set.of(
            "openai",
            "anthropic",
            "azure-openai",
            "bedrock",
            "gemini",
            "vertex",
            "cohere",
            "mistral-cloud"
    );

    private static final Set<String> BLOCKED_HOST_FRAGMENTS = Set.of(
            "openai.com",
            "anthropic.com",
            "googleapis.com",
            "azure.com",
            "amazonaws.com",
            "cohere.ai"
    );

    private LocalProviderGuard() {
    }

    public static void assertLocalProvider(String providerType) {
        String value = normalize(providerType);
        for (String fragment : BLOCKED_PROVIDER_FRAGMENTS) {
            if (value.contains(fragment)) {
                throw ModelRegistryException.cloudProviderRejected(providerType);
            }
        }
    }

    public static void assertLocalEndpoint(String endpoint) {
        String value = normalize(endpoint);
        for (String fragment : BLOCKED_HOST_FRAGMENTS) {
            if (value.contains(fragment)) {
                throw ModelRegistryException.cloudProviderRejected(endpoint);
            }
        }
    }

    private static String normalize(String raw) {
        return raw == null ? "" : raw.toLowerCase(Locale.ROOT).trim();
    }
}
