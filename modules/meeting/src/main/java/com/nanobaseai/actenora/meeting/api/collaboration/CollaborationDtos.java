package com.nanobaseai.actenora.meeting.api.collaboration;

import com.nanobaseai.actenora.meeting.domain.collaboration.MarkerType;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class CollaborationDtos {

    private CollaborationDtos() {
    }

    public record CreateMarkerRequest(MarkerType type, String body) {
    }

    public record MarkerResponse(
            UUID id,
            UUID meetingOccurrenceId,
            MarkerType type,
            String body,
            long offsetMs,
            UUID createdByUserId,
            Instant createdAt
    ) {
    }

    public record UpsertSharedNoteRequest(String body, Long expectedVersion) {
    }

    public record SharedNoteResponse(
            UUID id,
            UUID meetingOccurrenceId,
            String body,
            UUID createdByUserId,
            UUID updatedByUserId,
            Instant updatedAt,
            long version
    ) {
    }

    public record UpsertPrivateNoteRequest(String body, Long expectedVersion) {
    }

    public record PrivateNoteResponse(
            UUID id,
            UUID meetingOccurrenceId,
            UUID ownerUserId,
            String body,
            boolean aiUseAllowed,
            Instant updatedAt,
            long version
    ) {
    }

    public record UpdateAgendaRequest(List<String> items, Long expectedVersion) {
    }

    public record AgendaResponse(
            UUID id,
            UUID meetingOccurrenceId,
            List<String> items,
            UUID updatedByUserId,
            Instant updatedAt,
            long version
    ) {
    }

    public record CreateOpenTaskRequest(String title, UUID assigneeUserId, UUID sourceMeetingOccurrenceId) {
    }

    public record OpenTaskResponse(
            UUID id,
            UUID meetingOccurrenceId,
            String title,
            UUID assigneeUserId,
            boolean open,
            UUID sourceMeetingOccurrenceId,
            Instant createdAt
    ) {
    }

    public record MeetingWorkspaceResponse(
            UUID meetingOccurrenceId,
            AgendaResponse agenda,
            List<OpenTaskResponse> openTasks,
            List<MarkerResponse> markers,
            SharedNoteResponse sharedNote,
            PrivateNoteResponse privateNote
    ) {
    }
}
