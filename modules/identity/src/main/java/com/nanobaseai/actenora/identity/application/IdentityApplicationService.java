package com.nanobaseai.actenora.identity.application;

import com.nanobaseai.actenora.identity.api.IdentityApi;
import com.nanobaseai.actenora.identity.api.UserView;
import com.nanobaseai.actenora.identity.application.port.UserRepositoryPort;
import com.nanobaseai.actenora.identity.domain.AuthorizationDeniedException;
import com.nanobaseai.actenora.identity.domain.DuplicateEntraMappingException;
import com.nanobaseai.actenora.identity.domain.Permission;
import com.nanobaseai.actenora.identity.domain.SystemRole;
import com.nanobaseai.actenora.identity.domain.User;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;
import com.nanobaseai.actenora.sharedkernel.security.AuthenticatedPrincipal;
import com.nanobaseai.actenora.sharedkernel.security.IdentityClaims;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public final class IdentityApplicationService implements IdentityApi {

    private final UserRepositoryPort users;
    private final Clock clock;

    public IdentityApplicationService(UserRepositoryPort users, Clock clock) {
        this.users = Objects.requireNonNull(users, "users");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public AuthenticatedPrincipal resolvePrincipal(TenantId tenantId, IdentityClaims claims) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(claims, "claims");

        User user = users.findByEntraObjectId(claims.entraObjectId())
                .map(existing -> bindExisting(existing, tenantId, claims))
                .orElseGet(() -> provision(tenantId, claims));

        user.assertActive();
        if (!user.tenantId().equals(tenantId) && !claims.globalAdminHint()) {
            throw new AuthorizationDeniedException(user.id(), Permission.TENANT_READ.code());
        }

        Set<String> roleCodes = user.roles().stream().map(SystemRole::code).collect(Collectors.toUnmodifiableSet());
        Set<String> permissionCodes = user.effectivePermissions().stream()
                .map(Permission::code)
                .collect(Collectors.toUnmodifiableSet());

        boolean globalAdmin = claims.globalAdminHint() || user.roles().contains(SystemRole.SUPER_ADMIN);

        return new AuthenticatedPrincipal(
                tenantId,
                user.id(),
                user.entraObjectId(),
                user.email(),
                user.displayName(),
                roleCodes,
                permissionCodes,
                globalAdmin
        );
    }

    @Override
    public UserView currentUser(AuthenticatedPrincipal principal) {
        return findById(principal.tenantId(), principal.userId())
                .orElseThrow(() -> new IllegalStateException("Authenticated user missing from store"));
    }

    @Override
    public List<UserView> listUsers(TenantId tenantId) {
        return users.listByTenant(tenantId).stream().map(IdentityApplicationService::toView).toList();
    }

    @Override
    public UserView grantRole(
            TenantId tenantId,
            UUID userId,
            SystemRole role,
            long expectedVersion,
            UUID actorUserId
    ) {
        User user = requireTenantUser(tenantId, userId);
        user.grantRole(role, expectedVersion, clock.instant());
        users.save(user);
        return toView(user);
    }

    @Override
    public UserView revokeRole(
            TenantId tenantId,
            UUID userId,
            SystemRole role,
            long expectedVersion,
            UUID actorUserId
    ) {
        User user = requireTenantUser(tenantId, userId);
        user.revokeRole(role, expectedVersion, clock.instant());
        users.save(user);
        return toView(user);
    }

    @Override
    public void requirePermission(AuthenticatedPrincipal principal, Permission permission) {
        Objects.requireNonNull(principal, "principal");
        Objects.requireNonNull(permission, "permission");
        if (!principal.hasPermission(permission.code())) {
            throw new AuthorizationDeniedException(principal.userId(), permission.code());
        }
    }

    @Override
    public Optional<UserView> findByEntraObjectId(String entraObjectId) {
        return users.findByEntraObjectId(entraObjectId).map(IdentityApplicationService::toView);
    }

    @Override
    public Optional<UserView> findById(TenantId tenantId, UUID userId) {
        return users.findById(userId)
                .filter(user -> user.tenantId().equals(tenantId))
                .map(IdentityApplicationService::toView);
    }

    private User bindExisting(User existing, TenantId tenantId, IdentityClaims claims) {
        if (!existing.tenantId().equals(tenantId) && !claims.globalAdminHint()) {
            throw new DuplicateEntraMappingException(claims.entraObjectId());
        }
        return existing;
    }

    private User provision(TenantId tenantId, IdentityClaims claims) {
        String email = claims.emailOptional().orElse(claims.entraObjectId() + "@users.noreply");
        Instant now = clock.instant();
        SystemRole initial = claims.globalAdminHint() ? SystemRole.SUPER_ADMIN : SystemRole.PARTICIPANT;
        User user = User.provision(tenantId, claims.entraObjectId(), email, claims.displayName(), initial, now);
        users.save(user);
        return user;
    }

    private User requireTenantUser(TenantId tenantId, UUID userId) {
        User user = users.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown user: " + userId));
        if (!user.tenantId().equals(tenantId)) {
            throw new AuthorizationDeniedException(userId, Permission.USER_READ.code());
        }
        return user;
    }

    static UserView toView(User user) {
        return new UserView(
                user.id(),
                user.tenantId(),
                user.entraObjectId(),
                user.email(),
                user.displayName(),
                user.status(),
                user.roles(),
                user.effectivePermissions().stream().map(Permission::code).collect(Collectors.toUnmodifiableSet()),
                user.createdAt(),
                user.updatedAt(),
                user.version()
        );
    }
}
