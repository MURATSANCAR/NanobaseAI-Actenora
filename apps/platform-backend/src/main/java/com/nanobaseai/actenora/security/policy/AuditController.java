package com.nanobaseai.actenora.security.policy;

import com.nanobaseai.actenora.audit.api.AuditApi;
import com.nanobaseai.actenora.identity.api.IdentityApi;
import com.nanobaseai.actenora.identity.api.RequiresPermission;
import com.nanobaseai.actenora.identity.api.Permission;
import com.nanobaseai.actenora.sharedkernel.security.AuthenticatedPrincipal;
import com.nanobaseai.actenora.sharedkernel.security.TenantSecurityContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/audit")
public class AuditController {

    private final AuditApi auditApi;
    private final IdentityApi identityApi;

    public AuditController(AuditApi auditApi, IdentityApi identityApi) {
        this.auditApi = Objects.requireNonNull(auditApi, "auditApi");
        this.identityApi = Objects.requireNonNull(identityApi, "identityApi");
    }

    @GetMapping("/resources/{resourceId}")
    @RequiresPermission(Permission.AUDIT_READ)
    public List<AuditApi.AuditTimelineEntry> timeline(@PathVariable("resourceId") UUID resourceId) {
        AuthenticatedPrincipal principal = TenantSecurityContext.require();
        identityApi.requirePermission(principal, Permission.AUDIT_READ);
        return auditApi.timeline(principal.tenantId().value(), resourceId);
    }
}
