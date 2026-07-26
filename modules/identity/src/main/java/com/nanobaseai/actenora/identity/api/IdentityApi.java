package com.nanobaseai.actenora.identity.api;

import com.nanobaseai.actenora.identity.domain.SystemRole;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;
import com.nanobaseai.actenora.sharedkernel.security.AuthenticatedPrincipal;
import com.nanobaseai.actenora.sharedkernel.security.IdentityClaims;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Public façade for the Identity bounded context.
 * Cross-module callers use types in this package only.
 */
public interface IdentityApi {

    /**
     * Resolves or provisions a user within an already-validated active tenant.
     * Tenant binding must be performed by the caller (platform auth filter) via TenantApi.
     */
    AuthenticatedPrincipal resolvePrincipal(TenantId tenantId, IdentityClaims claims);

    UserView currentUser(AuthenticatedPrincipal principal);

    List<UserView> listUsers(TenantId tenantId);

    UserView grantRole(TenantId tenantId, UUID userId, SystemRole role, long expectedVersion, UUID actorUserId);

    UserView revokeRole(TenantId tenantId, UUID userId, SystemRole role, long expectedVersion, UUID actorUserId);

    void requirePermission(AuthenticatedPrincipal principal, Permission permission);

    Optional<UserView> findByEntraObjectId(String entraObjectId);

    Optional<UserView> findById(TenantId tenantId, UUID userId);
}
