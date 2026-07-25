package com.nanobaseai.actenora.identity.infrastructure.web;

import com.nanobaseai.actenora.identity.api.IdentityApi;
import com.nanobaseai.actenora.identity.api.RequiresPermission;
import com.nanobaseai.actenora.identity.api.UserView;
import com.nanobaseai.actenora.identity.domain.Permission;
import com.nanobaseai.actenora.identity.domain.SystemRole;
import com.nanobaseai.actenora.sharedkernel.security.AuthenticatedPrincipal;
import com.nanobaseai.actenora.sharedkernel.security.TenantSecurityContext;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class IdentityController {

    private final IdentityApi identityApi;

    public IdentityController(IdentityApi identityApi) {
        this.identityApi = Objects.requireNonNull(identityApi, "identityApi");
    }

    @GetMapping("/me")
    public UserView me() {
        AuthenticatedPrincipal principal = TenantSecurityContext.require();
        return identityApi.currentUser(principal);
    }

    @GetMapping("/users")
    @RequiresPermission(Permission.USER_READ)
    public List<UserView> listUsers() {
        AuthenticatedPrincipal principal = TenantSecurityContext.require();
        identityApi.requirePermission(principal, Permission.USER_READ);
        return identityApi.listUsers(principal.tenantId());
    }

    @PostMapping("/users/{id}/roles")
    @RequiresPermission(Permission.USER_ADMINISTER)
    public UserView grantRole(
            @PathVariable("id") UUID userId,
            @RequestParam("role") SystemRole role,
            @RequestParam("expectedVersion") long expectedVersion
    ) {
        AuthenticatedPrincipal principal = TenantSecurityContext.require();
        identityApi.requirePermission(principal, Permission.USER_ADMINISTER);
        return identityApi.grantRole(principal.tenantId(), userId, role, expectedVersion, principal.userId());
    }

    @DeleteMapping("/users/{id}/roles/{role}")
    @ResponseStatus(HttpStatus.OK)
    @RequiresPermission(Permission.USER_ADMINISTER)
    public UserView revokeRole(
            @PathVariable("id") UUID userId,
            @PathVariable("role") SystemRole role,
            @RequestParam("expectedVersion") long expectedVersion
    ) {
        AuthenticatedPrincipal principal = TenantSecurityContext.require();
        identityApi.requirePermission(principal, Permission.USER_ADMINISTER);
        return identityApi.revokeRole(principal.tenantId(), userId, role, expectedVersion, principal.userId());
    }
}
