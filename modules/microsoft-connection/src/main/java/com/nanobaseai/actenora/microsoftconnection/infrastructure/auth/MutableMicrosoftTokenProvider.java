package com.nanobaseai.actenora.microsoftconnection.infrastructure.auth;

import com.nanobaseai.actenora.microsoftconnection.application.model.AccessToken;
import com.nanobaseai.actenora.microsoftconnection.application.port.MicrosoftTokenProvider;
import com.nanobaseai.actenora.microsoftconnection.infrastructure.config.MicrosoftGraphProperties;
import com.nanobaseai.actenora.sharedkernel.time.InstantClock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * A {@link MicrosoftTokenProvider} whose underlying credential provider (certificate or
 * client-secret) can be swapped at runtime. The admin-editable Graph connection screen calls
 * {@link #rebuild(MicrosoftGraphProperties)} after persisting new credentials so subsequent
 * Graph calls pick them up without an application restart.
 *
 * <p>The build logic mirrors the {@code microsoftTokenProvider} bean factory in
 * {@code MicrosoftConnectionModuleConfiguration}.
 */
public final class MutableMicrosoftTokenProvider implements MicrosoftTokenProvider {

    private static final Logger log = LoggerFactory.getLogger(MutableMicrosoftTokenProvider.class);

    /** Delegate used when credentials are incomplete at startup — fails only when actually used. */
    private static final MicrosoftTokenProvider UNCONFIGURED = new MicrosoftTokenProvider() {
        @Override
        public AccessToken getAccessToken() {
            throw new IllegalStateException(
                    "Microsoft Graph connection is not configured. Set the tenant/client/credentials "
                            + "on the Teams settings screen.");
        }

        @Override
        public AccessToken refreshAccessToken() {
            return getAccessToken();
        }
    };

    private final InstantClock clock;
    private volatile MicrosoftTokenProvider delegate;

    public MutableMicrosoftTokenProvider(MicrosoftGraphProperties initial, InstantClock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.delegate = buildOrDeferred(Objects.requireNonNull(initial, "initial"));
    }

    /**
     * Builds the delegate but tolerates incomplete boot-time credentials: the module can be enabled
     * with blank/partial config so an admin can supply credentials at runtime via the portal without
     * the application failing to start. Runtime {@link #rebuild} still throws on invalid input.
     */
    private MicrosoftTokenProvider buildOrDeferred(MicrosoftGraphProperties props) {
        try {
            return build(props, clock);
        } catch (RuntimeException ex) {
            log.warn("Microsoft Graph credentials incomplete at startup ({}). Connection is inactive "
                    + "until configured via the Teams settings screen.", ex.getMessage());
            return UNCONFIGURED;
        }
    }

    @Override
    public AccessToken getAccessToken() {
        return delegate.getAccessToken();
    }

    @Override
    public AccessToken refreshAccessToken() {
        return delegate.refreshAccessToken();
    }

    /**
     * Rebuilds the delegate from the supplied properties. Throws if the credential material is
     * incomplete (e.g. CERTIFICATE mode without PEM paths, CLIENT_SECRET without a secret) so the
     * caller can surface the failure to the admin before the change is considered applied.
     */
    public void rebuild(MicrosoftGraphProperties props) {
        this.delegate = build(Objects.requireNonNull(props, "props"), clock);
    }

    /**
     * Validates that a provider can be constructed from the given properties (e.g. PEM material
     * loads, required credential fields present) without swapping the live delegate. Throws the
     * same exceptions {@link #rebuild(MicrosoftGraphProperties)} would.
     */
    public void validate(MicrosoftGraphProperties props) {
        build(Objects.requireNonNull(props, "props"), clock);
    }

    static MicrosoftTokenProvider build(MicrosoftGraphProperties props, InstantClock clock) {
        if (props.authMode() == MicrosoftGraphProperties.AuthMode.CERTIFICATE) {
            var material = PemCredentialsLoader.load(
                    props.certificatePemPath().orElseThrow(() -> new IllegalStateException(
                            "Certificate PEM path required when auth-mode=CERTIFICATE")),
                    props.privateKeyPemPath().orElseThrow(() -> new IllegalStateException(
                            "Private key PEM path required when auth-mode=CERTIFICATE"))
            );
            CertificateCredential credential = new CertificateCredential(
                    props.tenantId(),
                    props.clientId(),
                    props.authorityHost().toString(),
                    material.certificate(),
                    material.privateKey(),
                    props.scope()
            );
            return new CertificateMicrosoftTokenProvider(credential, clock);
        }
        ClientSecretCredential credential = new ClientSecretCredential(
                props.tenantId(),
                props.clientId(),
                props.clientSecret().orElseThrow(() -> new IllegalStateException(
                        "Client secret required when auth-mode=CLIENT_SECRET")),
                props.authorityHost().toString(),
                props.scope()
        );
        return new ClientSecretMicrosoftTokenProvider(credential, clock);
    }
}
