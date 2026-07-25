package com.nanobaseai.actenora.microsoftconnection.application.port;

import com.nanobaseai.actenora.microsoftconnection.application.model.TranscriptAvailability;
import com.nanobaseai.actenora.microsoftconnection.application.model.TranscriptContent;

import java.util.Optional;
import java.util.UUID;

/**
 * Microsoft Graph Teams meeting transcript availability and download.
 */
public interface TranscriptGateway {

    TranscriptAvailability checkAvailability(UUID tenantId, String userId, String meetingId);

    Optional<TranscriptContent> download(UUID tenantId, String userId, String meetingId, String transcriptId);
}
