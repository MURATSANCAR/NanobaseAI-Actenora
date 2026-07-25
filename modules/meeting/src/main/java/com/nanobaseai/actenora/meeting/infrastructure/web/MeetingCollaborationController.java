package com.nanobaseai.actenora.meeting.infrastructure.web;

import com.nanobaseai.actenora.meeting.api.collaboration.CollaborationDtos.AgendaResponse;
import com.nanobaseai.actenora.meeting.api.collaboration.CollaborationDtos.CreateMarkerRequest;
import com.nanobaseai.actenora.meeting.api.collaboration.CollaborationDtos.CreateOpenTaskRequest;
import com.nanobaseai.actenora.meeting.api.collaboration.CollaborationDtos.MarkerResponse;
import com.nanobaseai.actenora.meeting.api.collaboration.CollaborationDtos.MeetingWorkspaceResponse;
import com.nanobaseai.actenora.meeting.api.collaboration.CollaborationDtos.OpenTaskResponse;
import com.nanobaseai.actenora.meeting.api.collaboration.CollaborationDtos.PrivateNoteResponse;
import com.nanobaseai.actenora.meeting.api.collaboration.CollaborationDtos.SharedNoteResponse;
import com.nanobaseai.actenora.meeting.api.collaboration.CollaborationDtos.UpdateAgendaRequest;
import com.nanobaseai.actenora.meeting.api.collaboration.CollaborationDtos.UpsertPrivateNoteRequest;
import com.nanobaseai.actenora.meeting.api.collaboration.CollaborationDtos.UpsertSharedNoteRequest;
import com.nanobaseai.actenora.meeting.api.collaboration.MeetingCollaborationApi;
import com.nanobaseai.actenora.meeting.application.collaboration.port.MeetingAppTokenValidator;
import com.nanobaseai.actenora.meeting.application.collaboration.port.MeetingAppTokenValidator.UntrustedTeamsContext;
import com.nanobaseai.actenora.meeting.application.collaboration.port.MeetingAppTokenValidator.ValidatedMeetingPrincipal;
import com.nanobaseai.actenora.meeting.infrastructure.tenancy.FixedTenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * FAZ 22 Teams Meeting App API.
 * Requires backend Authorization bearer; Teams context headers are untrusted hints only.
 */
@RestController
@RequestMapping("/api/v1/meetings/{meetingId}/collaboration")
public class MeetingCollaborationController {

    public static final String TEAMS_MEETING_HEADER = "X-Teams-Meeting-Id";
    public static final String TEAMS_CHAT_HEADER = "X-Teams-Chat-Id";
    public static final String TEAMS_CLAIMED_TENANT_HEADER = "X-Teams-Claimed-Tenant-Id";
    public static final String TEAMS_CLAIMED_USER_HEADER = "X-Teams-Claimed-User-Id";
    public static final String IDEMPOTENCY_HEADER = "Idempotency-Key";

    private final MeetingCollaborationApi collaborationApi;
    private final MeetingAppTokenValidator tokenValidator;
    private final FixedTenantContext tenantContext;

    public MeetingCollaborationController(
            MeetingCollaborationApi collaborationApi,
            MeetingAppTokenValidator tokenValidator,
            FixedTenantContext tenantContext
    ) {
        this.collaborationApi = collaborationApi;
        this.tokenValidator = tokenValidator;
        this.tenantContext = tenantContext;
    }

    @GetMapping
    public MeetingWorkspaceResponse workspace(
            @PathVariable("meetingId") UUID meetingId,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = TEAMS_MEETING_HEADER, required = false) String teamsMeetingId,
            @RequestHeader(value = TEAMS_CHAT_HEADER, required = false) String chatId,
            @RequestHeader(value = TEAMS_CLAIMED_TENANT_HEADER, required = false) String claimedTenant,
            @RequestHeader(value = TEAMS_CLAIMED_USER_HEADER, required = false) String claimedUser
    ) {
        authenticate(authorization, teamsMeetingId, chatId, claimedTenant, claimedUser);
        return collaborationApi.getWorkspace(meetingId);
    }

    @PostMapping("/markers")
    @ResponseStatus(HttpStatus.CREATED)
    public MarkerResponse createMarker(
            @PathVariable("meetingId") UUID meetingId,
            @RequestBody CreateMarkerRequest request,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = IDEMPOTENCY_HEADER, required = false) String idempotencyKey,
            @RequestHeader(value = TEAMS_MEETING_HEADER, required = false) String teamsMeetingId,
            @RequestHeader(value = TEAMS_CHAT_HEADER, required = false) String chatId,
            @RequestHeader(value = TEAMS_CLAIMED_TENANT_HEADER, required = false) String claimedTenant,
            @RequestHeader(value = TEAMS_CLAIMED_USER_HEADER, required = false) String claimedUser
    ) {
        authenticate(authorization, teamsMeetingId, chatId, claimedTenant, claimedUser);
        return collaborationApi.createMarker(meetingId, request, idempotencyKey);
    }

    @GetMapping("/markers")
    public List<MarkerResponse> listMarkers(
            @PathVariable("meetingId") UUID meetingId,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = TEAMS_MEETING_HEADER, required = false) String teamsMeetingId,
            @RequestHeader(value = TEAMS_CHAT_HEADER, required = false) String chatId,
            @RequestHeader(value = TEAMS_CLAIMED_TENANT_HEADER, required = false) String claimedTenant,
            @RequestHeader(value = TEAMS_CLAIMED_USER_HEADER, required = false) String claimedUser
    ) {
        authenticate(authorization, teamsMeetingId, chatId, claimedTenant, claimedUser);
        return collaborationApi.listMarkers(meetingId);
    }

    @PutMapping("/shared-note")
    public SharedNoteResponse upsertSharedNote(
            @PathVariable("meetingId") UUID meetingId,
            @RequestBody UpsertSharedNoteRequest request,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = TEAMS_MEETING_HEADER, required = false) String teamsMeetingId,
            @RequestHeader(value = TEAMS_CHAT_HEADER, required = false) String chatId,
            @RequestHeader(value = TEAMS_CLAIMED_TENANT_HEADER, required = false) String claimedTenant,
            @RequestHeader(value = TEAMS_CLAIMED_USER_HEADER, required = false) String claimedUser
    ) {
        authenticate(authorization, teamsMeetingId, chatId, claimedTenant, claimedUser);
        return collaborationApi.upsertSharedNote(meetingId, request);
    }

    @PutMapping("/private-note")
    public PrivateNoteResponse upsertPrivateNote(
            @PathVariable("meetingId") UUID meetingId,
            @RequestBody UpsertPrivateNoteRequest request,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = TEAMS_MEETING_HEADER, required = false) String teamsMeetingId,
            @RequestHeader(value = TEAMS_CHAT_HEADER, required = false) String chatId,
            @RequestHeader(value = TEAMS_CLAIMED_TENANT_HEADER, required = false) String claimedTenant,
            @RequestHeader(value = TEAMS_CLAIMED_USER_HEADER, required = false) String claimedUser
    ) {
        authenticate(authorization, teamsMeetingId, chatId, claimedTenant, claimedUser);
        return collaborationApi.upsertPrivateNote(meetingId, request);
    }

    @GetMapping("/private-note")
    public PrivateNoteResponse getOwnPrivateNote(
            @PathVariable("meetingId") UUID meetingId,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = TEAMS_MEETING_HEADER, required = false) String teamsMeetingId,
            @RequestHeader(value = TEAMS_CHAT_HEADER, required = false) String chatId,
            @RequestHeader(value = TEAMS_CLAIMED_TENANT_HEADER, required = false) String claimedTenant,
            @RequestHeader(value = TEAMS_CLAIMED_USER_HEADER, required = false) String claimedUser
    ) {
        authenticate(authorization, teamsMeetingId, chatId, claimedTenant, claimedUser);
        return collaborationApi.getOwnPrivateNote(meetingId);
    }

    @GetMapping("/private-notes/{noteId}")
    public PrivateNoteResponse getPrivateNoteById(
            @PathVariable("meetingId") UUID meetingId,
            @PathVariable("noteId") UUID noteId,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = TEAMS_MEETING_HEADER, required = false) String teamsMeetingId,
            @RequestHeader(value = TEAMS_CHAT_HEADER, required = false) String chatId,
            @RequestHeader(value = TEAMS_CLAIMED_TENANT_HEADER, required = false) String claimedTenant,
            @RequestHeader(value = TEAMS_CLAIMED_USER_HEADER, required = false) String claimedUser
    ) {
        authenticate(authorization, teamsMeetingId, chatId, claimedTenant, claimedUser);
        return collaborationApi.getPrivateNoteById(noteId);
    }

    @PostMapping("/private-notes/{noteId}/ai-consent")
    public PrivateNoteResponse grantAiConsent(
            @PathVariable("meetingId") UUID meetingId,
            @PathVariable("noteId") UUID noteId,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = TEAMS_MEETING_HEADER, required = false) String teamsMeetingId,
            @RequestHeader(value = TEAMS_CHAT_HEADER, required = false) String chatId,
            @RequestHeader(value = TEAMS_CLAIMED_TENANT_HEADER, required = false) String claimedTenant,
            @RequestHeader(value = TEAMS_CLAIMED_USER_HEADER, required = false) String claimedUser
    ) {
        authenticate(authorization, teamsMeetingId, chatId, claimedTenant, claimedUser);
        return collaborationApi.grantPrivateNoteAiUse(noteId);
    }

    @PutMapping("/agenda")
    public AgendaResponse updateAgenda(
            @PathVariable("meetingId") UUID meetingId,
            @RequestBody UpdateAgendaRequest request,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = IDEMPOTENCY_HEADER, required = false) String idempotencyKey,
            @RequestHeader(value = TEAMS_MEETING_HEADER, required = false) String teamsMeetingId,
            @RequestHeader(value = TEAMS_CHAT_HEADER, required = false) String chatId,
            @RequestHeader(value = TEAMS_CLAIMED_TENANT_HEADER, required = false) String claimedTenant,
            @RequestHeader(value = TEAMS_CLAIMED_USER_HEADER, required = false) String claimedUser
    ) {
        authenticate(authorization, teamsMeetingId, chatId, claimedTenant, claimedUser);
        return collaborationApi.updateAgenda(meetingId, request, idempotencyKey);
    }

    @GetMapping("/agenda")
    public AgendaResponse getAgenda(
            @PathVariable("meetingId") UUID meetingId,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = TEAMS_MEETING_HEADER, required = false) String teamsMeetingId,
            @RequestHeader(value = TEAMS_CHAT_HEADER, required = false) String chatId,
            @RequestHeader(value = TEAMS_CLAIMED_TENANT_HEADER, required = false) String claimedTenant,
            @RequestHeader(value = TEAMS_CLAIMED_USER_HEADER, required = false) String claimedUser
    ) {
        authenticate(authorization, teamsMeetingId, chatId, claimedTenant, claimedUser);
        return collaborationApi.getAgenda(meetingId);
    }

    @PostMapping("/open-tasks")
    @ResponseStatus(HttpStatus.CREATED)
    public OpenTaskResponse createOpenTask(
            @PathVariable("meetingId") UUID meetingId,
            @RequestBody CreateOpenTaskRequest request,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = TEAMS_MEETING_HEADER, required = false) String teamsMeetingId,
            @RequestHeader(value = TEAMS_CHAT_HEADER, required = false) String chatId,
            @RequestHeader(value = TEAMS_CLAIMED_TENANT_HEADER, required = false) String claimedTenant,
            @RequestHeader(value = TEAMS_CLAIMED_USER_HEADER, required = false) String claimedUser
    ) {
        authenticate(authorization, teamsMeetingId, chatId, claimedTenant, claimedUser);
        return collaborationApi.createOpenTask(meetingId, request);
    }

    @GetMapping("/open-tasks")
    public List<OpenTaskResponse> listOpenTasks(
            @PathVariable("meetingId") UUID meetingId,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = TEAMS_MEETING_HEADER, required = false) String teamsMeetingId,
            @RequestHeader(value = TEAMS_CHAT_HEADER, required = false) String chatId,
            @RequestHeader(value = TEAMS_CLAIMED_TENANT_HEADER, required = false) String claimedTenant,
            @RequestHeader(value = TEAMS_CLAIMED_USER_HEADER, required = false) String claimedUser
    ) {
        authenticate(authorization, teamsMeetingId, chatId, claimedTenant, claimedUser);
        return collaborationApi.listOpenTasks(meetingId);
    }

    private void authenticate(
            String authorization,
            String teamsMeetingId,
            String chatId,
            String claimedTenant,
            String claimedUser
    ) {
        ValidatedMeetingPrincipal principal = tokenValidator.validate(
                authorization,
                new UntrustedTeamsContext(teamsMeetingId, chatId, claimedTenant, claimedUser)
        );
        tenantContext.use(principal.tenantId(), principal.userId());
    }
}
