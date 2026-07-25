package com.nanobaseai.actenora.meeting.api.collaboration;

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

import java.util.List;
import java.util.UUID;

/**
 * FAZ 22 — Teams Meeting App collaboration façade.
 * Controllers must validate backend tokens before invoking these methods;
 * Teams client context alone is never sufficient.
 */
public interface MeetingCollaborationApi {

    MeetingWorkspaceResponse getWorkspace(UUID meetingOccurrenceId);

    MarkerResponse createMarker(UUID meetingOccurrenceId, CreateMarkerRequest request, String idempotencyKey);

    List<MarkerResponse> listMarkers(UUID meetingOccurrenceId);

    SharedNoteResponse upsertSharedNote(UUID meetingOccurrenceId, UpsertSharedNoteRequest request);

    SharedNoteResponse getSharedNote(UUID meetingOccurrenceId);

    PrivateNoteResponse upsertPrivateNote(UUID meetingOccurrenceId, UpsertPrivateNoteRequest request);

    PrivateNoteResponse getOwnPrivateNote(UUID meetingOccurrenceId);

    /** Owner-only; organizer/admin denied by default. */
    PrivateNoteResponse getPrivateNoteById(UUID noteId);

    PrivateNoteResponse grantPrivateNoteAiUse(UUID noteId);

    /** AI / system consumers — throws if owner has not granted explicit permission. */
    PrivateNoteResponse readPrivateNoteForAi(UUID noteId);

    AgendaResponse updateAgenda(UUID meetingOccurrenceId, UpdateAgendaRequest request, String idempotencyKey);

    AgendaResponse getAgenda(UUID meetingOccurrenceId);

    OpenTaskResponse createOpenTask(UUID meetingOccurrenceId, CreateOpenTaskRequest request);

    List<OpenTaskResponse> listOpenTasks(UUID meetingOccurrenceId);
}
