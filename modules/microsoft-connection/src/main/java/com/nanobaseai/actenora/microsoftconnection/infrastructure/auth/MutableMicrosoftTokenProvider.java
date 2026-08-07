package com.nanobaseai.actenora.microsoftconnection.infrastructure.auth;

import com.nanobaseai.actenora.microsoftconnection.application.model.AccessToken;
import com.nanobaseai.actenora.microsoftconnection.application.port.MicrosoftTokenProvider;
import com.nanobaseai.actenora.microsoftconnection.infrastructure.config.MicrosoftGraphProperties;
import com.nanobaseai.actenora.sharedkernel.time.InstantClock;

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

    private final InstantClock clock;
    private volatile MicrosoftTokenProvider delegate;

    public MutableMicrosoftTokenProvider(MicrosoftGraphProperties initial, InstantClock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.delegate = build(Objects.requireNonNull(initial, "initial"), clock);
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
