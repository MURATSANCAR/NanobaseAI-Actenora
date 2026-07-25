package com.nanobaseai.actenora.microsoftconnection.application;

import com.nanobaseai.actenora.microsoftconnection.application.model.OnlineMeetingMetadata;
import com.nanobaseai.actenora.microsoftconnection.application.model.ParticipantMetadata;
import com.nanobaseai.actenora.microsoftconnection.application.model.TranscriptAvailability;
import com.nanobaseai.actenora.microsoftconnection.application.model.TranscriptContent;
import com.nanobaseai.actenora.microsoftconnection.application.port.OnlineMeetingGateway;
import com.nanobaseai.actenora.microsoftconnection.application.port.TranscriptGateway;
import com.nanobaseai.actenora.microsoftconnection.infrastructure.graph.GraphApiException;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Meeting metadata + transcript fetch coordination.
 */
public final class MeetingTranscriptService {

    private final OnlineMeetingGateway onlineMeetingGateway;
    private final TranscriptGateway transcriptGateway;

    public MeetingTranscriptService(
            OnlineMeetingGateway onlineMeetingGateway,
            TranscriptGateway transcriptGateway
    ) {
        this.onlineMeetingGateway = Objects.requireNonNull(onlineMeetingGateway, "onlineMeetingGateway");
        this.transcriptGateway = Objects.requireNonNull(transcriptGateway, "transcriptGateway");
    }

    public Optional<OnlineMeetingMetadata> meetingMetadata(UUID tenantId, String userId, String meetingId) {
        return onlineMeetingGateway.getByMeetingId(tenantId, userId, meetingId);
    }

    public List<ParticipantMetadata> participants(UUID tenantId, String userId, String meetingId) {
        return onlineMeetingGateway.listParticipants(tenantId, userId, meetingId);
    }

    public TranscriptAvailability transcriptAvailability(UUID tenantId, String userId, String meetingId) {
        return transcriptGateway.checkAvailability(tenantId, userId, meetingId);
    }

    public Optional<TranscriptContent> downloadTranscript(
            UUID tenantId,
            String userId,
            String meetingId,
            String transcriptId
    ) {
        try {
            return transcriptGateway.download(tenantId, userId, meetingId, transcriptId);
        } catch (GraphApiException ex) {
            if (GraphApiException.CODE_NOT_FOUND.equals(ex.code())) {
                return Optional.empty();
            }
            throw ex;
        }
    }
}
