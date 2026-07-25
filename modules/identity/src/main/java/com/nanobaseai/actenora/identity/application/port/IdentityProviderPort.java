package com.nanobaseai.actenora.identity.application.port;

import com.nanobaseai.actenora.sharedkernel.security.IdentityClaims;

import java.util.Optional;

/**
 * Port for extracting identity claims from an upstream identity provider.
 */
public interface IdentityProviderPort {

    Optional<IdentityClaims> extractClaims(IdentityProviderRequest request);
}
