package com.nanobaseai.actenora.security.auth;

import com.nanobaseai.actenora.audit.api.AuditApi;
import com.nanobaseai.actenora.identity.api.IdentityProblemDetails;
import com.nanobaseai.actenora.identity.domain.AuthorizationDeniedException;
import com.nanobaseai.actenora.identity.domain.DuplicateEntraMappingException;
import com.nanobaseai.actenora.identity.domain.UserNotActiveException;
import com.nanobaseai.actenora.policy.api.QuotaProblemDetails;
import com.nanobaseai.actenora.policy.domain.QuotaExceededException;
import com.nanobaseai.actenora.sharedkernel.security.TenantSecurityContext;
import com.nanobaseai.actenora.tenant.api.TenantProblemDetails;
import com.nanobaseai.actenora.tenant.domain.CrossTenantAccessException;
import com.nanobaseai.actenora.tenant.domain.TenantNotActiveException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class AuthProblemDetailsAdvice {

    private final ObjectProvider<AuditApi> auditApi;

    public AuthProblemDetailsAdvice(ObjectProvider<AuditApi> auditApi) {
        this.auditApi = auditApi;
    }

    @ExceptionHandler(TenantNotActiveException.class)
    public ResponseEntity<String> tenantNotActive(TenantNotActiveException ex, HttpServletRequest request) {
        TenantProblemDetails problem = TenantProblemDetails.from(ex, URI.create(request.getRequestURI()));
        return ResponseEntity.status(problem.status())
                .contentType(MediaType.parseMediaType(TenantProblemDetails.MEDIA_TYPE))
                .body(problem.toJson());
    }

    @ExceptionHandler(CrossTenantAccessException.class)
    public ResponseEntity<String> crossTenant(CrossTenantAccessException ex, HttpServletRequest request) {
        TenantProblemDetails problem = TenantProblemDetails.from(ex, URI.create(request.getRequestURI()));
        return ResponseEntity.status(problem.status())
                .contentType(MediaType.parseMediaType(TenantProblemDetails.MEDIA_TYPE))
                .body(problem.toJson());
    }

    @ExceptionHandler(AuthorizationDeniedException.class)
    public ResponseEntity<String> denied(AuthorizationDeniedException ex, HttpServletRequest request) {
        IdentityProblemDetails problem = IdentityProblemDetails.from(ex, URI.create(request.getRequestURI()));
        return ResponseEntity.status(problem.status())
                .contentType(MediaType.parseMediaType(IdentityProblemDetails.MEDIA_TYPE))
                .body(problem.toJson());
    }

    @ExceptionHandler(UserNotActiveException.class)
    public ResponseEntity<String> userNotActive(UserNotActiveException ex, HttpServletRequest request) {
        IdentityProblemDetails problem = IdentityProblemDetails.from(ex, URI.create(request.getRequestURI()));
        return ResponseEntity.status(problem.status())
                .contentType(MediaType.parseMediaType(IdentityProblemDetails.MEDIA_TYPE))
                .body(problem.toJson());
    }

    @ExceptionHandler(DuplicateEntraMappingException.class)
    public ResponseEntity<String> duplicateEntra(DuplicateEntraMappingException ex, HttpServletRequest request) {
        IdentityProblemDetails problem = IdentityProblemDetails.from(ex, URI.create(request.getRequestURI()));
        return ResponseEntity.status(problem.status())
                .contentType(MediaType.parseMediaType(IdentityProblemDetails.MEDIA_TYPE))
                .body(problem.toJson());
    }

    @ExceptionHandler(QuotaExceededException.class)
    public ResponseEntity<String> quotaExceeded(QuotaExceededException ex, HttpServletRequest request) {
        QuotaProblemDetails problem = QuotaProblemDetails.from(ex, URI.create(request.getRequestURI()));
        auditPolicyDenied(ex, request.getRequestURI());
        return ResponseEntity.status(problem.status())
                .contentType(MediaType.parseMediaType(QuotaProblemDetails.MEDIA_TYPE))
                .body(problem.toJson());
    }

    @ExceptionHandler({
            com.nanobaseai.actenora.identity.domain.OptimisticLockException.class,
            com.nanobaseai.actenora.tenant.domain.OptimisticLockException.class
    })
    public ResponseEntity<String> optimisticLock(RuntimeException ex, HttpServletRequest request) {
        if (ex instanceof com.nanobaseai.actenora.identity.domain.OptimisticLockException identityLock) {
            IdentityProblemDetails problem =
                    IdentityProblemDetails.from(identityLock, URI.create(request.getRequestURI()));
            return ResponseEntity.status(problem.status())
                    .contentType(MediaType.parseMediaType(IdentityProblemDetails.MEDIA_TYPE))
                    .body(problem.toJson());
        }
        com.nanobaseai.actenora.tenant.domain.OptimisticLockException tenantLock =
                (com.nanobaseai.actenora.tenant.domain.OptimisticLockException) ex;
        TenantProblemDetails problem = TenantProblemDetails.from(tenantLock, URI.create(request.getRequestURI()));
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .contentType(MediaType.parseMediaType(TenantProblemDetails.MEDIA_TYPE))
                .body(problem.toJson());
    }

    private void auditPolicyDenied(QuotaExceededException ex, String path) {
        AuditApi audit = auditApi.getIfAvailable();
        if (audit == null) {
            return;
        }
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("quotaDimension", ex.dimension().name());
        metadata.put("limit", ex.limit());
        metadata.put("used", ex.used());
        metadata.put("requested", ex.requested());
        metadata.put("path", path);
        String actor = TenantSecurityContext.current()
                .map(principal -> principal.userId().toString())
                .orElse("anonymous");
        audit.append(
                ex.tenantId().value(),
                actor,
                "POLICY_DENIED",
                "TenantPolicy",
                ex.tenantId().value(),
                metadata,
                Instant.now()
        );
    }
}
