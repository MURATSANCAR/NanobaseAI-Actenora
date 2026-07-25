package com.nanobaseai.actenora.security.auth;

import com.nanobaseai.actenora.identity.api.IdentityApi;
import com.nanobaseai.actenora.identity.application.port.IdentityProviderPort;
import com.nanobaseai.actenora.identity.application.port.IdentityProviderRequest;
import com.nanobaseai.actenora.sharedkernel.security.AuthenticatedPrincipal;
import com.nanobaseai.actenora.sharedkernel.security.IdentityClaims;
import com.nanobaseai.actenora.tenant.api.TenantApi;
import com.nanobaseai.actenora.tenant.api.TenantView;
import com.nanobaseai.actenora.tenant.domain.TenantNotActiveException;
import com.nanobaseai.actenora.tenant.domain.TenantStatus;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Binds IdP claims → active tenant → identity principal.
 * Never trusts request body/query tenant IDs.
 */
public final class AuthenticationBindingService {

    private final IdentityProviderPort identityProvider;
    private final TenantApi tenantApi;
    private final IdentityApi identityApi;

    public AuthenticationBindingService(
            IdentityProviderPort identityProvider,
            TenantApi tenantApi,
            IdentityApi identityApi
    ) {
        this.identityProvider = Objects.requireNonNull(identityProvider, "identityProvider");
        this.tenantApi = Objects.requireNonNull(tenantApi, "tenantApi");
        this.identityApi = Objects.requireNonNull(identityApi, "identityApi");
    }

    public Optional<AuthenticatedPrincipal> bind(Map<String, Object> jwtClaims, Map<String, String> headers) {
        IdentityProviderRequest request = new IdentityProviderRequest(jwtClaims, headers);
        Optional<IdentityClaims> claims = identityProvider.extractClaims(request);
        if (claims.isEmpty()) {
            return Optional.empty();
        }
        IdentityClaims identityClaims = claims.get();
        TenantView tenant = tenantApi.findByEntraTenantId(identityClaims.entraTenantId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "No Actenora tenant mapped for Entra tid"));
        if (tenant.status() != TenantStatus.ACTIVE) {
            throw new TenantNotActiveException(tenant.id(), tenant.status());
        }
        AuthenticatedPrincipal principal = identityApi.resolvePrincipal(tenant.id(), identityClaims);
        tenantApi.ensureMembership(tenant.id(), principal.userId());
        return Optional.of(principal);
    }
}
