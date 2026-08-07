package com.nanobaseai.actenora.security.microsoftconnection;

import com.nanobaseai.actenora.microsoftconnection.infrastructure.auth.MutableMicrosoftTokenProvider;
import com.nanobaseai.actenora.microsoftconnection.infrastructure.config.MicrosoftGraphProperties;
import com.nanobaseai.actenora.microsoftconnection.infrastructure.config.MicrosoftGraphSpringProperties;
import com.nanobaseai.actenora.microsoftconnection.infrastructure.graph.GraphHttpClient;
import com.nanobaseai.actenora.microsoftconnection.infrastructure.graph.MutableGraphEndpoint;
import com.nanobaseai.actenora.sharedkernel.error.ActenoraException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Admin-facing management of the Microsoft Graph / Teams connection.
 *
 * <p>Mirrors {@code NanobaseAiConnectionService}: holds the effective settings, persists edits,
 * hot-applies them to the live {@link MutableMicrosoftTokenProvider} and {@link MutableGraphEndpoint}
 * (no restart), and exposes a real {@link #testConnection()} that acquires a token and probes Graph.
 * The client secret is never returned by {@link #current()} — only whether one is configured.
 */
public final class GraphConnectionService {

    private static final Logger log = LoggerFactory.getLogger(GraphConnectionService.class);
    private static final String PROBE_PATH = "v1.0/users?$select=id&$top=1";

    private final GraphConnectionSettingsStore store;
    private final MutableMicrosoftTokenProvider tokenProvider;
    private final MutableGraphEndpoint graphEndpoint;
    private final GraphHttpClient graphHttp;
    private final MicrosoftGraphSpringProperties springProperties;
    private final AtomicReference<GraphConnectionSettings> effective = new AtomicReference<>();

    public GraphConnectionService(
            GraphConnectionSettingsStore store,
            MutableMicrosoftTokenProvider tokenProvider,
            MutableGraphEndpoint graphEndpoint,
            GraphHttpClient graphHttp,
            MicrosoftGraphSpringProperties springProperties
    ) {
        this.store = Objects.requireNonNull(store, "store");
        this.tokenProvider = Objects.requireNonNull(tokenProvider, "tokenProvider");
        this.graphEndpoint = Objects.requireNonNull(graphEndpoint, "graphEndpoint");
        this.graphHttp = Objects.requireNonNull(graphHttp, "graphHttp");
        this.springProperties = Objects.requireNonNull(springProperties, "springProperties");
        initEffectiveSettings();
    }

    private void initEffectiveSettings() {
        Optional<GraphConnectionSettings> persisted = store.load();
        GraphConnectionSettings loaded = persisted.orElseGet(this::fromSpringProperties);
        effective.set(loaded);
        // Re-apply persisted settings to the live provider/endpoint. On failure keep the boot-time
        // beans (built from properties) so the application still starts.
        if (persisted.isEmpty()) {
            return;
        }
        try {
            MicrosoftGraphProperties props = toProperties(loaded);
            tokenProvider.rebuild(props);
            graphEndpoint.set(props.graphBaseUrl());
            log.info("Applied persisted Microsoft Graph connection settings on startup");
        } catch (RuntimeException ex) {
            log.warn("Persisted Microsoft Graph connection settings could not be applied on startup: {}",
                    sanitize(ex.getMessage()));
        }
    }

    public ConnectionView current() {
        return toView(effective.get());
    }

    public ConnectionView update(UpdateCommand command) {
        Objects.requireNonNull(command, "command");
        GraphConnectionSettings current = effective.get();
        GraphConnectionSettings candidate = merge(current, command);
        validate(candidate);
        MicrosoftGraphProperties props = toProperties(candidate);

        // 1) Validate credential material can be loaded (does not mutate the live provider).
        try {
            tokenProvider.validate(props);
        } catch (RuntimeException ex) {
            throw new ActenoraException("GRAPH_CREDENTIALS_INVALID",
                    "Graph credentials are invalid: " + sanitize(ex.getMessage()));
        }
        // 2) Persist (rejects a plaintext secret when no encryption key is configured).
        try {
            store.save(candidate);
        } catch (RuntimeException ex) {
            throw new ActenoraException("GRAPH_SETTINGS_PERSIST_FAILED",
                    "Could not save Graph settings: " + sanitize(ex.getMessage()));
        }
        // 3) Hot-apply.
        tokenProvider.rebuild(props);
        graphEndpoint.set(props.graphBaseUrl());
        effective.set(candidate);
        log.info("Microsoft Graph connection updated authMode={} tenantId={} baseUrl={}",
                candidate.authMode(), maskId(candidate.tenantId()), candidate.graphBaseUrl());
        return toView(candidate);
    }

    public TestResult testConnection() {
        long start = System.nanoTime();
        try {
            tokenProvider.refreshAccessToken();
        } catch (RuntimeException ex) {
            long ms = elapsedMs(start);
            return new TestResult(false, ms,
                    "Token acquisition failed: " + sanitize(ex.getMessage()));
        }
        long tokenMs = elapsedMs(start);
        // Token acquired — connection credentials are valid. Probe Graph for a fuller signal.
        try {
            var response = graphHttp.send(token -> graphHttp.authorizedGet(PROBE_PATH, token));
            int status = response.statusCode();
            long ms = elapsedMs(start);
            if (status >= 200 && status < 300) {
                return new TestResult(true, ms, "Token acquired and Microsoft Graph reachable (HTTP "
                        + status + ").");
            }
            return new TestResult(true, ms, "Token acquired in " + tokenMs
                    + "ms; Graph probe returned HTTP " + status
                    + " — check API permissions / admin consent.");
        } catch (RuntimeException ex) {
            long ms = elapsedMs(start);
            return new TestResult(true, ms, "Token acquired in " + tokenMs
                    + "ms; Graph probe failed: " + sanitize(ex.getMessage())
                    + " — check API permissions / admin consent.");
        }
    }

    // --- helpers -------------------------------------------------------------------------------

    private GraphConnectionSettings fromSpringProperties() {
        MicrosoftGraphProperties p = springProperties.toProperties();
        return new GraphConnectionSettings(
                springProperties.isEnabled(),
                p.graphBaseUrl().toString(),
                p.authorityHost().toString(),
                p.tenantId() == null ? "" : p.tenantId(),
                p.clientId() == null ? "" : p.clientId(),
                p.scope(),
                p.authMode(),
                p.clientSecret(),
                p.certificatePemPath(),
                p.privateKeyPemPath(),
                Optional.ofNullable(springProperties.getDefaultMailboxUserId())
        );
    }

    private GraphConnectionSettings merge(GraphConnectionSettings base, UpdateCommand cmd) {
        MicrosoftGraphProperties.AuthMode authMode = cmd.authMode() == null || cmd.authMode().isBlank()
                ? base.authMode()
                : parseAuthMode(cmd.authMode());
        // Blank secret input means "keep current"; a non-blank value replaces it.
        Optional<String> secret = (cmd.clientSecret() == null || cmd.clientSecret().isBlank())
                ? base.clientSecret()
                : Optional.of(cmd.clientSecret().trim());
        return new GraphConnectionSettings(
                cmd.enabled() == null ? base.enabled() : cmd.enabled(),
                orElse(cmd.graphBaseUrl(), base.graphBaseUrl()),
                orElse(cmd.authorityHost(), base.authorityHost()),
                orElse(cmd.tenantId(), base.tenantId()),
                orElse(cmd.clientId(), base.clientId()),
                orElse(cmd.scope(), base.scope()),
                authMode,
                secret,
                optional(cmd.certificatePemPath(), base.certificatePemPath()),
                optional(cmd.privateKeyPemPath(), base.privateKeyPemPath()),
                optional(cmd.defaultMailboxUserId(), base.defaultMailboxUserId())
        );
    }

    private void validate(GraphConnectionSettings s) {
        requireField(s.tenantId(), "Directory (tenant) ID");
        requireField(s.clientId(), "Application (client) ID");
        requireUri(s.graphBaseUrl(), "Graph base URL");
        requireUri(s.authorityHost(), "Authority host");
        if (s.scope() == null || s.scope().isBlank()) {
            throw new ActenoraException("GRAPH_SETTINGS_INVALID", "Scope is required.");
        }
        if (s.authMode() == MicrosoftGraphProperties.AuthMode.CLIENT_SECRET && s.clientSecret().isEmpty()) {
            throw new ActenoraException("GRAPH_SETTINGS_INVALID",
                    "A client secret is required for CLIENT_SECRET auth mode.");
        }
        if (s.authMode() == MicrosoftGraphProperties.AuthMode.CERTIFICATE
                && (s.certificatePemPath().isEmpty() || s.privateKeyPemPath().isEmpty())) {
            throw new ActenoraException("GRAPH_SETTINGS_INVALID",
                    "Certificate and private key PEM paths are required for CERTIFICATE auth mode.");
        }
    }

    private MicrosoftGraphProperties toProperties(GraphConnectionSettings s) {
        MicrosoftGraphProperties spring = springProperties.toProperties();
        return new MicrosoftGraphProperties(
                URI.create(s.graphBaseUrl()),
                URI.create(s.authorityHost()),
                s.tenantId(),
                s.clientId(),
                s.scope(),
                s.authMode(),
                s.clientSecret(),
                s.certificatePemPath(),
                s.privateKeyPemPath(),
                spring.subscriptionRenewBefore(),
                spring.subscriptionRenewWindow(),
                spring.workersEnabled()
        );
    }

    private ConnectionView toView(GraphConnectionSettings s) {
        return new ConnectionView(
                s.enabled(),
                s.graphBaseUrl(),
                s.authorityHost(),
                s.tenantId(),
                s.clientId(),
                s.scope(),
                s.authMode().name(),
                s.secretConfigured(),
                s.certificatePemPath().orElse(""),
                s.privateKeyPemPath().orElse(""),
                s.defaultMailboxUserId().orElse("")
        );
    }

    private static MicrosoftGraphProperties.AuthMode parseAuthMode(String value) {
        try {
            return MicrosoftGraphProperties.AuthMode.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new ActenoraException("GRAPH_SETTINGS_INVALID",
                    "Auth mode must be CERTIFICATE or CLIENT_SECRET.");
        }
    }

    private static String orElse(String value, String fallback) {
        return (value == null || value.isBlank()) ? fallback : value.trim();
    }

    private static Optional<String> optional(String value, Optional<String> fallback) {
        if (value == null) {
            return fallback;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? Optional.empty() : Optional.of(trimmed);
    }

    private static void requireField(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new ActenoraException("GRAPH_SETTINGS_INVALID", label + " is required.");
        }
    }

    private static void requireUri(String value, String label) {
        try {
            URI.create(value);
        } catch (RuntimeException ex) {
            throw new ActenoraException("GRAPH_SETTINGS_INVALID", label + " is not a valid URL.");
        }
    }

    private static long elapsedMs(long startNanos) {
        return Math.max(0, (System.nanoTime() - startNanos) / 1_000_000);
    }

    private static String maskId(String id) {
        if (id == null || id.length() < 6) {
            return "***";
        }
        return id.substring(0, 4) + "…" + id.substring(id.length() - 2);
    }

    private static String sanitize(String message) {
        if (message == null || message.isBlank()) {
            return "unknown error";
        }
        return message.length() > 400 ? message.substring(0, 400) + "…" : message;
    }

    public record ConnectionView(
            boolean enabled,
            String graphBaseUrl,
            String authorityHost,
            String tenantId,
            String clientId,
            String scope,
            String authMode,
            boolean secretConfigured,
            String certificatePemPath,
            String privateKeyPemPath,
            String defaultMailboxUserId
    ) {
    }

    public record TestResult(boolean healthy, long latencyMs, String detail) {
    }

    public record UpdateCommand(
            Boolean enabled,
            String graphBaseUrl,
            String authorityHost,
            String tenantId,
            String clientId,
            String scope,
            String authMode,
            String clientSecret,
            String certificatePemPath,
            String privateKeyPemPath,
            String defaultMailboxUserId
    ) {
    }
}
