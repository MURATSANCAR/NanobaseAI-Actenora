package com.nanobaseai.actenora.security.aiprocessing;

import com.nanobaseai.actenora.aiprocessing.application.port.LocalModelProvider;
import com.nanobaseai.actenora.aiprocessing.infrastructure.llm.LlamaCppProvider;
import com.nanobaseai.actenora.aiprocessing.infrastructure.llm.LocalProviderConfig;
import com.nanobaseai.actenora.aiprocessing.infrastructure.llm.MockLocalProvider;
import com.nanobaseai.actenora.aiprocessing.infrastructure.llm.OpenAiCompatibleLocalProvider;
import com.nanobaseai.actenora.aiprocessing.infrastructure.llm.VllmProvider;

import java.net.URI;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Builds the NanobaseAI local intelligence runtime and refuses cloud endpoints or offline mode on production.
 */
public final class LocalProviderFactory {

    private static final Set<String> LOCAL_HOSTS = Set.of("localhost", "127.0.0.1", "::1", "0.0.0.0", "host.docker.internal");

    private LocalProviderFactory() {
    }

    public static LocalModelProvider create(LocalProviderProperties properties, boolean production) {
        Objects.requireNonNull(properties, "properties");
        LocalProviderProperties.Kind kind = properties.resolvedKind();
        if (kind == LocalProviderProperties.Kind.MOCK) {
            if (production) {
                throw new IllegalStateException(
                        "Refusing to start: NanobaseAI Intelligence cannot run offline on production profiles");
            }
            return new MockLocalProvider(
                    properties.getMaxConcurrency(),
                    properties.isStreamingEnabled(),
                    properties.getServedModelIds());
        }

        LocalProviderConfig config = config(properties, kind);
        assertLocalEndpoint(config.baseUrl());
        return switch (kind) {
            case VLLM -> new VllmProvider(config);
            case LLAMACPP -> new LlamaCppProvider(config);
            case OPENAI, NANOBASEAI -> new OpenAiCompatibleLocalProvider(config);
            case MOCK -> throw new IllegalStateException("unreachable");
        };
    }

    private static LocalProviderConfig config(LocalProviderProperties properties, LocalProviderProperties.Kind kind) {
        return LocalProviderConfig.builder("nanobaseai", properties.getBaseUrl())
                .connectTimeout(properties.getConnectTimeout())
                .readTimeout(properties.getReadTimeout())
                .maxConcurrency(properties.getMaxConcurrency())
                .maxConcurrencyExtraction(properties.getMaxConcurrencyExtraction())
                .maxConcurrencyFinal(properties.getMaxConcurrencyFinal())
                .streamingEnabled(properties.isStreamingEnabled())
                .degradedProbeThresholdMs(properties.getDegradedProbeThresholdMs())
                .knownServedModelIds(properties.getServedModelIds())
                .build();
    }

    public static void assertLocalEndpoint(URI baseUrl) {
        String host = baseUrl.getHost();
        if (host == null || host.isBlank()) {
            throw new IllegalStateException("NanobaseAI Intelligence endpoint must include a host");
        }
        String normalized = host.toLowerCase(Locale.ROOT);
        boolean local = LOCAL_HOSTS.contains(normalized)
                || normalized.endsWith(".local")
                || normalized.endsWith(".internal")
                || isPrivateIpv4(normalized)
                || isDockerServiceHost(normalized);
        if (!local) {
            throw new IllegalStateException(
                    "NanobaseAI Intelligence only accepts local or private network endpoints");
        }
    }

    private static boolean isDockerServiceHost(String host) {
        // Single-label names are Docker/Kubernetes service names on private networks.
        return !host.contains(".") && !host.contains(":");
    }

    private static boolean isPrivateIpv4(String host) {
        String[] parts = host.split("\\.");
        if (parts.length != 4) {
            return false;
        }
        int first;
        int second;
        try {
            first = Integer.parseInt(parts[0]);
            second = Integer.parseInt(parts[1]);
            Integer.parseInt(parts[2]);
            Integer.parseInt(parts[3]);
        } catch (NumberFormatException ex) {
            return false;
        }
        return first == 10
                || (first == 192 && second == 168)
                || (first == 172 && second >= 16 && second <= 31);
    }
}
