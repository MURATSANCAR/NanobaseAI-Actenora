package com.nanobaseai.actenora.microsoftconnection.infrastructure.graph;

import java.net.URI;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * FAZ 27 — Graph controlled egress: only Microsoft Graph / login hosts are reachable.
 */
public final class GraphEgressPolicy {

    public static final Set<String> DEFAULT_ALLOWED_HOSTS = Set.of(
            "graph.microsoft.com",
            "login.microsoftonline.com",
            "login.windows.net"
    );

    private final Set<String> allowedHosts;
    private final boolean allowHttpLoopback;

    public GraphEgressPolicy(Set<String> allowedHosts) {
        this(allowedHosts, false);
    }

    public GraphEgressPolicy(Set<String> allowedHosts, boolean allowHttpLoopback) {
        if (allowedHosts == null || allowedHosts.isEmpty()) {
            throw new IllegalArgumentException("allowedHosts required");
        }
        this.allowedHosts = Set.copyOf(allowedHosts);
        this.allowHttpLoopback = allowHttpLoopback;
    }

    public static GraphEgressPolicy defaults() {
        return new GraphEgressPolicy(DEFAULT_ALLOWED_HOSTS);
    }

    /**
     * Loopback HTTP for WireMock / local FAZ scenario tests only.
     */
    public static GraphEgressPolicy localTesting() {
        return new GraphEgressPolicy(Set.of("localhost", "127.0.0.1"), true);
    }

    public void assertAllowed(URI uri) {
        Objects.requireNonNull(uri, "uri");
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw GraphApiException.configuration("Graph egress denied: missing host");
        }
        String normalized = host.toLowerCase(Locale.ROOT);
        boolean loopback = "localhost".equals(normalized) || "127.0.0.1".equals(normalized);
        if (!"https".equals(scheme)) {
            if (!(allowHttpLoopback && "http".equals(scheme) && loopback)) {
                throw GraphApiException.configuration("Graph egress denied: HTTPS required for " + uri);
            }
        }
        boolean allowed = allowedHosts.stream().anyMatch(allowedHost ->
                normalized.equals(allowedHost) || normalized.endsWith("." + allowedHost));
        if (!allowed) {
            throw GraphApiException.configuration("Graph egress denied for host=" + host);
        }
    }

    public Set<String> allowedHosts() {
        return allowedHosts;
    }
}
