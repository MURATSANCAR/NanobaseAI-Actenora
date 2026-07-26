package com.nanobaseai.actenora.security.policy;

import com.nanobaseai.actenora.audit.api.AuditApi;
import com.nanobaseai.actenora.audit.domain.SettingChangeAuditor;
import com.nanobaseai.actenora.identity.api.IdentityApi;
import com.nanobaseai.actenora.identity.api.RequiresPermission;
import com.nanobaseai.actenora.identity.api.Permission;
import com.nanobaseai.actenora.policy.api.PolicyApi;
import com.nanobaseai.actenora.policy.api.TenantPolicyOverrideRequest;
import com.nanobaseai.actenora.policy.api.TenantPolicyView;
import com.nanobaseai.actenora.policy.domain.TenantPolicy;
import com.nanobaseai.actenora.policy.domain.TenantPolicyOverride;
import com.nanobaseai.actenora.sharedkernel.security.AuthenticatedPrincipal;
import com.nanobaseai.actenora.sharedkernel.security.TenantSecurityContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Auth-bound policy surface. Tenant id comes from {@link TenantSecurityContext} only.
 */
@RestController
@RequestMapping("/api/v1/policies")
public class PolicyController {

    private final PolicyApi policyApi;
    private final IdentityApi identityApi;
    private final AuditApi auditApi;

    public PolicyController(PolicyApi policyApi, IdentityApi identityApi, AuditApi auditApi) {
        this.policyApi = Objects.requireNonNull(policyApi, "policyApi");
        this.identityApi = Objects.requireNonNull(identityApi, "identityApi");
        this.auditApi = Objects.requireNonNull(auditApi, "auditApi");
    }

    @GetMapping("/current")
    public TenantPolicyView current() {
        AuthenticatedPrincipal principal = TenantSecurityContext.require();
        return TenantPolicyView.from(policyApi.evaluate(principal.tenantId()));
    }

    @PutMapping("/current/overrides")
    @RequiresPermission(Permission.POLICY_ADMINISTER)
    public TenantPolicyView saveOverride(@RequestBody TenantPolicyOverrideRequest request) {
        AuthenticatedPrincipal principal = TenantSecurityContext.require();
        identityApi.requirePermission(principal, Permission.POLICY_ADMINISTER);

        TenantPolicy before = policyApi.evaluate(principal.tenantId());
        TenantPolicyOverride.Builder builder = TenantPolicyOverride.builder(principal.tenantId());
        if (request != null) {
            if (request.retention() != null) {
                builder.retention(request.retention());
            }
            if (request.delivery() != null) {
                builder.delivery(request.delivery());
            }
            if (request.modelAccess() != null) {
                builder.modelAccess(request.modelAccess());
            }
            if (request.processingSla() != null) {
                builder.processingSla(request.processingSla());
            }
            if (request.concurrency() != null) {
                builder.concurrency(request.concurrency());
            }
            if (request.externalParticipant() != null) {
                builder.externalParticipant(request.externalParticipant());
            }
            if (request.quotas() != null) {
                builder.quotas(request.quotas());
            }
        }
        policyApi.saveOverride(builder.build());
        TenantPolicy after = policyApi.evaluate(principal.tenantId());

        var entry = SettingChangeAuditor.record(
                principal.tenantId(),
                principal.userId(),
                "TenantPolicyOverride",
                principal.tenantId().value().toString(),
                snapshot(before),
                snapshot(after),
                null,
                null
        );
        auditApi.append(
                entry.tenantId(),
                entry.actorId(),
                entry.action(),
                entry.resourceType(),
                entry.resourceId(),
                entry.metadata(),
                Instant.now()
        );
        return TenantPolicyView.from(after);
    }

    private static Map<String, Object> snapshot(TenantPolicy policy) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("version", policy.version());
        map.put("dailyMeetingLimit", policy.quotas().dailyMeetingLimit());
        map.put("maxConcurrentAiJobs", policy.quotas().maxConcurrentAiJobs());
        map.put("criticalMeetingFallbackAllowed", policy.modelAccess().criticalMeetingFallbackAllowed());
        map.put("allowedModelKeys", policy.modelAccess().allowedModelKeys());
        map.put("requireApproval", policy.delivery().requireApproval());
        map.put("retentionDays", policy.retention().retentionDays());
        return map;
    }
}
