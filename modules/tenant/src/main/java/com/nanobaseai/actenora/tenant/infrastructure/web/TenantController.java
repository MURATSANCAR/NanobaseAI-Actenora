package com.nanobaseai.actenora.tenant.infrastructure.web;

import com.nanobaseai.actenora.sharedkernel.security.AuthenticatedPrincipal;
import com.nanobaseai.actenora.sharedkernel.security.TenantSecurityContext;
import com.nanobaseai.actenora.tenant.api.TenantApi;
import com.nanobaseai.actenora.tenant.api.TenantView;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;

@RestController
@RequestMapping("/api/v1/tenants")
public class TenantController {

    private final TenantApi tenantApi;

    public TenantController(TenantApi tenantApi) {
        this.tenantApi = Objects.requireNonNull(tenantApi, "tenantApi");
    }

    @GetMapping("/current")
    public TenantView current() {
        AuthenticatedPrincipal principal = TenantSecurityContext.require();
        return tenantApi.requireActive(principal.tenantId());
    }
}
