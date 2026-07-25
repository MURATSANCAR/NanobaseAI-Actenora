package com.nanobaseai.actenora.transcript.api.web;

import com.nanobaseai.actenora.sharedkernel.domain.TenantId;
import com.nanobaseai.actenora.sharedkernel.security.TenantSecurityContext;
import com.nanobaseai.actenora.transcript.api.TranscriptDeploymentMode;
import com.nanobaseai.actenora.transcript.api.dto.AddDictionaryEntryRequest;
import com.nanobaseai.actenora.transcript.api.dto.CreateTenantDictionaryRequest;
import com.nanobaseai.actenora.transcript.api.dto.TenantDictionaryResponse;
import com.nanobaseai.actenora.transcript.application.TenantDictionaryApplicationService;
import com.nanobaseai.actenora.transcript.domain.TranscriptDomainException;
import com.nanobaseai.actenora.transcript.domain.dictionary.TenantDictionary;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * FAZ 9 — tenant dictionary CRUD. Tenant from auth context; header is fallback only.
 */
@RestController
@RequestMapping("/api/v1/transcript-dictionaries")
@ConditionalOnProperty(
        name = TranscriptDeploymentMode.PROPERTY,
        havingValue = TranscriptDeploymentMode.EMBEDDED,
        matchIfMissing = true)
public class TenantDictionaryController {

    public static final String TENANT_HEADER = TranscriptController.TENANT_HEADER;

    private final TenantDictionaryApplicationService dictionaryService;

    public TenantDictionaryController(TenantDictionaryApplicationService dictionaryService) {
        this.dictionaryService = dictionaryService;
    }

    @PostMapping
    public ResponseEntity<TenantDictionaryResponse> create(
            @RequestHeader(value = TENANT_HEADER, required = false) UUID tenantHeader,
            @RequestBody CreateTenantDictionaryRequest request) {
        TenantDictionary created = dictionaryService.create(resolveTenant(tenantHeader), request.name());
        return ResponseEntity.status(HttpStatus.CREATED).body(TenantDictionaryResponse.from(created));
    }

    @GetMapping
    public List<TenantDictionaryResponse> list(
            @RequestHeader(value = TENANT_HEADER, required = false) UUID tenantHeader) {
        return dictionaryService.list(resolveTenant(tenantHeader)).stream()
                .map(TenantDictionaryResponse::from)
                .toList();
    }

    @GetMapping("/{dictionaryId}")
    public TenantDictionaryResponse get(
            @RequestHeader(value = TENANT_HEADER, required = false) UUID tenantHeader,
            @PathVariable UUID dictionaryId) {
        return TenantDictionaryResponse.from(dictionaryService.get(resolveTenant(tenantHeader), dictionaryId));
    }

    @PostMapping("/{dictionaryId}/entries")
    public TenantDictionaryResponse addEntry(
            @RequestHeader(value = TENANT_HEADER, required = false) UUID tenantHeader,
            @PathVariable UUID dictionaryId,
            @RequestBody AddDictionaryEntryRequest request) {
        TenantDictionary updated = dictionaryService.addEntry(
                resolveTenant(tenantHeader),
                dictionaryId,
                request.kind(),
                request.canonical(),
                request.aliases(),
                request.externalRef());
        return TenantDictionaryResponse.from(updated);
    }

    private static TenantId resolveTenant(UUID tenantHeader) {
        return TenantSecurityContext.current()
                .map(principal -> {
                    if (tenantHeader != null && !principal.tenantId().value().equals(tenantHeader)) {
                        throw new TranscriptDomainException(
                                "TENANT_HEADER_MISMATCH",
                                "X-Actenora-Tenant-Id does not match authenticated tenant");
                    }
                    return principal.tenantId();
                })
                .orElseGet(() -> {
                    if (tenantHeader == null) {
                        throw new TranscriptDomainException(
                                "TENANT_REQUIRED",
                                "Authenticated tenant or X-Actenora-Tenant-Id header required");
                    }
                    return TenantId.of(tenantHeader);
                });
    }

    @ExceptionHandler(TranscriptDomainException.class)
    public ResponseEntity<ProblemDetail> handleDomain(TranscriptDomainException ex) {
        HttpStatus status = switch (ex.code()) {
            case "TENANT_REQUIRED", "TENANT_HEADER_MISMATCH", "INVALID_DICTIONARY_NAME" -> HttpStatus.BAD_REQUEST;
            case "DICTIONARY_NOT_FOUND" -> HttpStatus.NOT_FOUND;
            case "DICTIONARY_NAME_EXISTS" -> HttpStatus.CONFLICT;
            default -> HttpStatus.UNPROCESSABLE_ENTITY;
        };
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, ex.getMessage());
        problem.setTitle(ex.code());
        problem.setProperty("code", ex.code());
        return ResponseEntity.status(status).body(problem);
    }
}
