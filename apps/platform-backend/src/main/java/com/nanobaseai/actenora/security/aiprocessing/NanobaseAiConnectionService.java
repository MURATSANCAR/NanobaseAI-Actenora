package com.nanobaseai.actenora.security.aiprocessing;

import com.nanobaseai.actenora.aiprocessing.application.modelworker.ProviderHealth;
import com.nanobaseai.actenora.aiprocessing.application.port.LocalModelProvider;
import com.nanobaseai.actenora.aiprocessing.infrastructure.llm.LocalProviderConfig;
import com.nanobaseai.actenora.aiprocessing.infrastructure.llm.MockLocalProvider;
import com.nanobaseai.actenora.aiprocessing.infrastructure.llm.OpenAiCompatibleLocalProvider;
import com.nanobaseai.actenora.sharedkernel.error.ActenoraException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Runtime NanobaseAI intelligence endpoint settings + health probe.
 * Operator-facing labels never expose third-party stack names.
 */
public final class NanobaseAiConnectionService {

    private static final Logger log = LoggerFactory.getLogger(NanobaseAiConnectionService.class);

    public static final String PRODUCT_NAME = "NanobaseAI Intelligence";

    private final SwappableLocalModelProvider swappable;
    private final boolean production;
    private final AtomicReference<Settings> settings;
    private final NanobaseAiModelRegistrySync modelRegistrySync;

    public NanobaseAiConnectionService(
            SwappableLocalModelProvider swappable,
            LocalProviderProperties initial,
            boolean production
    ) {
        this(swappable, initial, production, null);
    }

    public NanobaseAiConnectionService(
            SwappableLocalModelProvider swappable,
            LocalProviderProperties initial,
            boolean production,
            NanobaseAiModelRegistrySync modelRegistrySync
    ) {
        this.swappable = Objects.requireNonNull(swappable, "swappable");
        this.production = production;
        LocalProviderProperties props = Objects.requireNonNull(initial, "initial");
        this.settings = new AtomicReference<>(Settings.fromProperties(props));
        this.modelRegistrySync = modelRegistrySync;
    }

    public ConnectionView current() {
        Settings s = settings.get();
        ProviderHealth health = safeHealth();
        return new ConnectionView(
                PRODUCT_NAME,
                NanobaseAiBrandSanitizer.publicMode(s.kind()),
                s.enabled(),
                maskEndpoint(s.baseUrl()),
                s.baseUrl().toString(),
                health.acceptsWork(),
                health.probeLatencyMs(),
                NanobaseAiBrandSanitizer.sanitize(health.detail()),
                s.servedModelIds(),
                Instant.now()
        );
    }

    public ConnectionView update(UpdateConnectionCommand command) {
        Objects.requireNonNull(command, "command");
        Settings current = settings.get();
        URI baseUrl = command.baseUrl() == null ? current.baseUrl() : URI.create(command.baseUrl().trim());
        assertLocalEndpoint(baseUrl);
        boolean enabled = command.enabled() == null ? current.enabled() : command.enabled();
        Set<String> models = command.servedModelIds() == null || command.servedModelIds().isEmpty()
                ? current.servedModelIds()
                : sanitizeModelIds(command.servedModelIds());
        String kind = enabled ? (production ? "nanobaseai" : current.kind().equals("mock") ? "nanobaseai" : current.kind())
                : (production ? "nanobaseai" : "mock");
        if (production && !enabled) {
            throw new ActenoraException(
                    "NANOBASEAI_REQUIRED",
                    "NanobaseAI Intelligence cannot be disabled in production."
            );
        }
        if (production) {
            kind = "nanobaseai";
        } else if (!enabled) {
            kind = "mock";
        } else {
            kind = "nanobaseai";
        }
        Settings next = new Settings(kind, enabled, baseUrl, current.connectTimeout(), current.readTimeout(),
                current.maxConcurrency(), current.streamingEnabled(), models);
        apply(next);
        settings.set(next);
        syncModelRegistry(next);
        return current();
    }

    public ConnectionView testConnection() {
        Settings s = settings.get();
        try {
            ProviderHealth health = swappable.delegate().health();
            if (!health.acceptsWork()) {
                throw new ActenoraException(
                        "NANOBASEAI_UNREACHABLE",
                        NanobaseAiBrandSanitizer.sanitize(
                                "NanobaseAI Intelligence did not respond from " + maskEndpoint(s.baseUrl())
                        )
                );
            }
            syncModelRegistry(s);
            return current();
        } catch (ActenoraException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new ActenoraException(
                    "NANOBASEAI_UNREACHABLE",
                    NanobaseAiBrandSanitizer.sanitize(
                            "NanobaseAI Intelligence is unreachable at " + maskEndpoint(s.baseUrl())
                    )
            );
        }
    }

    private void apply(Settings next) {
        LocalModelProvider provider = buildProvider(next);
        swappable.replace(provider);
        log.info("NanobaseAI Intelligence endpoint updated host={} enabled={}",
                next.baseUrl().getHost(), next.enabled());
        syncModelRegistry(next);
    }

    private void syncModelRegistry(Settings settings) {
        if (modelRegistrySync == null || settings == null) {
            return;
        }
        try {
            modelRegistrySync.ensureRegistered(settings.baseUrl(), settings.servedModelIds(), settings.enabled());
        } catch (RuntimeException ex) {
            log.warn("NanobaseAI model registry sync skipped reason={}", ex.getMessage());
        }
    }

    private LocalModelProvider buildProvider(Settings s) {
        if (!s.enabled() || "mock".equalsIgnoreCase(s.kind())) {
            if (production) {
                throw new ActenoraException(
                        "NANOBASEAI_REQUIRED",
                        "NanobaseAI Intelligence requires a live local endpoint in production."
                );
            }
            return new MockLocalProvider(s.maxConcurrency(), s.streamingEnabled(), s.servedModelIds());
        }
        LocalProviderConfig config = LocalProviderConfig.builder("nanobaseai", s.baseUrl())
                .connectTimeout(s.connectTimeout())
                .readTimeout(s.readTimeout())
                .maxConcurrency(s.maxConcurrency())
                .streamingEnabled(s.streamingEnabled())
                .knownServedModelIds(s.servedModelIds())
                .build();
        LocalProviderFactory.assertLocalEndpoint(s.baseUrl());
        return new OpenAiCompatibleLocalProvider(config);
    }

    private ProviderHealth safeHealth() {
        try {
            return swappable.delegate().health();
        } catch (RuntimeException ex) {
            return ProviderHealth.down(NanobaseAiBrandSanitizer.sanitize(ex.getMessage()), 0);
        }
    }

    private static void assertLocalEndpoint(URI baseUrl) {
        try {
            LocalProviderFactory.assertLocalEndpoint(baseUrl);
        } catch (IllegalStateException ex) {
            throw new ActenoraException(
                    "NANOBASEAI_ENDPOINT_DENIED",
                    NanobaseAiBrandSanitizer.sanitize(ex.getMessage())
            );
        }
    }

    private static Set<String> sanitizeModelIds(Set<String> ids) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (String id : ids) {
            if (id == null || id.isBlank()) {
                continue;
            }
            // Served model ids are opaque gateway identifiers (e.g. nanobase-qwen36-35b-a3b-mtp).
            // Do not brand-sanitize them — that rewrites vendor substrings and breaks provider routing.
            String cleaned = id.trim().replaceAll("\\s+", "-");
            if (cleaned.isBlank()) {
                continue;
            }
            out.add(cleaned);
        }
        if (out.isEmpty()) {
            out.add("nanobaseai-primary");
        }
        return out;
    }

    private static String maskEndpoint(URI uri) {
        if (uri == null) {
            return "";
        }
        String host = uri.getHost() == null ? "localhost" : uri.getHost();
        int port = uri.getPort();
        return port > 0 ? host + ":" + port : host;
    }

    public record Settings(
            String kind,
            boolean enabled,
            URI baseUrl,
            Duration connectTimeout,
            Duration readTimeout,
            int maxConcurrency,
            boolean streamingEnabled,
            Set<String> servedModelIds
    ) {
        static Settings fromProperties(LocalProviderProperties props) {
            String kind = props.resolvedKind() == LocalProviderProperties.Kind.MOCK ? "mock" : "nanobaseai";
            Set<String> models = props.getServedModelIds() == null || props.getServedModelIds().isEmpty()
                    ? Set.of("nanobaseai-primary", "nanobaseai-local")
                    : sanitizeModelIds(props.getServedModelIds());
            boolean enabled = props.resolvedKind() != LocalProviderProperties.Kind.MOCK;
            return new Settings(
                    kind,
                    enabled,
                    props.getBaseUrl(),
                    props.getConnectTimeout(),
                    props.getReadTimeout(),
                    props.getMaxConcurrency(),
                    props.isStreamingEnabled(),
                    models
            );
        }
    }

    public record UpdateConnectionCommand(
            String baseUrl,
            Boolean enabled,
            Set<String> servedModelIds
    ) {
    }

    public record ConnectionView(
            String productName,
            String mode,
            boolean enabled,
            String endpointHost,
            String baseUrl,
            boolean healthy,
            long latencyMs,
            String statusDetail,
            Set<String> servedModelIds,
            Instant checkedAt
    ) {
    }
}
