package com.nanobaseai.actenora.security.meetingintelligence;

import com.nanobaseai.actenora.identity.api.IdentityApi;
import com.nanobaseai.actenora.identity.api.Permission;
import com.nanobaseai.actenora.identity.api.RequiresPermission;
import com.nanobaseai.actenora.sharedkernel.error.ActenoraException;
import com.nanobaseai.actenora.sharedkernel.security.AuthenticatedPrincipal;
import com.nanobaseai.actenora.sharedkernel.security.TenantSecurityContext;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;

/**
 * Operator surface for the meeting-knowledge embedding endpoint: view the current
 * configuration and live-test a candidate before committing it to the environment.
 * Admin-gated ({@link Permission#MODEL_CONTROL}), mirroring the LLM/Graph connection
 * endpoints in the portal BFF. Standalone controller to avoid touching the large
 * PortalApiController constructor.
 */
@RestController
@RequestMapping("/api/v1/portal/intelligence/embedding")
public class EmbeddingConnectionController {

    private final ObjectProvider<EmbeddingConnectionService> embeddingConnectionService;
    private final IdentityApi identityApi;

    public EmbeddingConnectionController(
            ObjectProvider<EmbeddingConnectionService> embeddingConnectionService,
            IdentityApi identityApi
    ) {
        this.embeddingConnectionService = embeddingConnectionService;
        this.identityApi = Objects.requireNonNull(identityApi, "identityApi");
    }

    @GetMapping("/connection")
    @RequiresPermission(Permission.MODEL_CONTROL)
    public EmbeddingConnectionService.ConnectionView connection() {
        require(Permission.MODEL_CONTROL);
        return service().current();
    }

    @PostMapping("/connection/test")
    @RequiresPermission(Permission.MODEL_CONTROL)
    public EmbeddingConnectionService.TestResult testConnection(
            @RequestBody(required = false) EmbeddingConnectionService.TestCommand body
    ) {
        require(Permission.MODEL_CONTROL);
        return service().test(body);
    }

    private EmbeddingConnectionService service() {
        EmbeddingConnectionService service = embeddingConnectionService.getIfAvailable();
        if (service == null) {
            throw new ActenoraException(
                    "EMBEDDING_CONNECTION_UNAVAILABLE",
                    "Embedding connection settings are not available on this runtime");
        }
        return service;
    }

    private void require(Permission permission) {
        AuthenticatedPrincipal principal = TenantSecurityContext.require();
        identityApi.requirePermission(principal, permission);
    }
}
