package com.nanobaseai.actenora.microsoftconnection.application.port;

import com.nanobaseai.actenora.microsoftconnection.application.model.OnlineMeetingMetadata;
import com.nanobaseai.actenora.microsoftconnection.application.model.ParticipantMetadata;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Microsoft Graph online meeting metadata.
 */
public interface OnlineMeetingGateway {

    Optional<OnlineMeetingMetadata> getByJoinWebUrl(UUID tenantId, String userId, String joinWebUrl);

    Optional<OnlineMeetingMetadata> getByMeetingId(UUID tenantId, String userId, String meetingId);

    List<ParticipantMetadata> listParticipants(UUID tenantId, String userId, String meetingId);

    /**
     * PATCH onlineMeeting to turn on native Teams transcription (idempotent).
     */
    void enableTranscription(UUID tenantId, String userId, String meetingId);
}
