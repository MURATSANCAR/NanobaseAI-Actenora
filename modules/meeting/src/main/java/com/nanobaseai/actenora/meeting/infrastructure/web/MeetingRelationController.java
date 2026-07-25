package com.nanobaseai.actenora.meeting.infrastructure.web;

import com.nanobaseai.actenora.meeting.api.relation.ContinuityProjectionResponse;
import com.nanobaseai.actenora.meeting.api.relation.CreateManualRelationRequest;
import com.nanobaseai.actenora.meeting.api.relation.MeetingRelationApi;
import com.nanobaseai.actenora.meeting.api.relation.MeetingRelationResponse;
import com.nanobaseai.actenora.meeting.application.port.TenantContextPort;
import com.nanobaseai.actenora.meeting.domain.relation.SuggestionNotFoundException;
import com.nanobaseai.actenora.sharedkernel.security.TenantSecurityContext;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * FAZ 7 relation/continuity HTTP surface. Tenant id never taken from request body.
 */
@RestController
@RequestMapping("/api/v1")
public class MeetingRelationController {

    private final MeetingRelationApi relationApi;
    private final TenantContextPort tenantContext;

    public MeetingRelationController(MeetingRelationApi relationApi, TenantContextPort tenantContext) {
        this.relationApi = Objects.requireNonNull(relationApi, "relationApi");
        this.tenantContext = Objects.requireNonNull(tenantContext, "tenantContext");
    }

    @PostMapping("/meeting-relations")
    @ResponseStatus(HttpStatus.CREATED)
    public MeetingRelationResponse createManual(@RequestBody CreateManualRelationRequest request) {
        return relationApi.createManual(tenantId(), actor(), request);
    }

    @GetMapping("/meeting-occurrences/{id}/relations")
    public List<MeetingRelationResponse> listRelations(@PathVariable("id") UUID occurrenceId) {
        return relationApi.listForOccurrence(tenantId(), occurrenceId);
    }

    @PostMapping("/meeting-relation-suggestions/{id}/approve")
    public MeetingRelationResponse approveSuggestion(@PathVariable("id") UUID suggestionId) {
        return relationApi.approveSuggestion(tenantId(), suggestionId, actor())
                .orElseThrow(() -> new SuggestionNotFoundException(suggestionId));
    }

    @PostMapping("/meeting-relation-suggestions/{id}/reject")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void rejectSuggestion(@PathVariable("id") UUID suggestionId) {
        relationApi.rejectSuggestion(tenantId(), suggestionId, actor());
    }

    @GetMapping("/meeting-occurrences/{id}/continuity")
    public ContinuityProjectionResponse continuity(@PathVariable("id") UUID occurrenceId) {
        return relationApi.continuity(tenantId(), occurrenceId);
    }

    private UUID tenantId() {
        return TenantSecurityContext.current()
                .map(principal -> principal.tenantId().value())
                .orElseGet(() -> tenantContext.requireTenantId().value());
    }

    private String actor() {
        return TenantSecurityContext.current()
                .map(principal -> principal.userId().toString())
                .orElseGet(() -> tenantContext.requireActorUserId().toString());
    }
}
