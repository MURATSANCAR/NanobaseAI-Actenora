package com.nanobaseai.actenora.transcript.api;

import com.nanobaseai.actenora.sharedkernel.domain.TenantId;
import com.nanobaseai.actenora.sharedkernel.domain.PersonIdentityNormalizer;
import com.nanobaseai.actenora.sharedkernel.port.storage.AuthorizedUrl;
import com.nanobaseai.actenora.transcript.api.dto.TranscriptCommandResponse;
import com.nanobaseai.actenora.transcript.api.dto.TranscriptDownloadAuthorizationResponse;
import com.nanobaseai.actenora.transcript.api.dto.TranscriptNormalizeResponse;
import com.nanobaseai.actenora.transcript.api.dto.TranscriptSegmentView;
import com.nanobaseai.actenora.transcript.api.dto.TranscriptUploadResponse;
import com.nanobaseai.actenora.transcript.application.TranscriptIngestionService;
import com.nanobaseai.actenora.transcript.application.TranscriptNormalizationService;
import com.nanobaseai.actenora.transcript.application.port.in.AuthorizeTranscriptDownloadQuery;
import com.nanobaseai.actenora.transcript.application.port.in.IngestGraphVttCommand;
import com.nanobaseai.actenora.transcript.application.port.in.NormalizeTranscriptCommand;
import com.nanobaseai.actenora.transcript.application.port.in.ReparseTranscriptCommand;
import com.nanobaseai.actenora.transcript.application.port.in.UploadManualVttCommand;
import com.nanobaseai.actenora.transcript.application.port.in.UploadManualVttResult;
import com.nanobaseai.actenora.transcript.application.port.out.TranscriptRepository;
import com.nanobaseai.actenora.transcript.application.port.out.TranscriptSegmentRepository;
import com.nanobaseai.actenora.transcript.domain.Transcript;
import com.nanobaseai.actenora.transcript.domain.TranscriptDomainException;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Public façade for the Transcript bounded context.
 * Cross-module callers use types in this package only.
 */
public class TranscriptApi {

    private final TranscriptIngestionService ingestionService;
    private final TranscriptNormalizationService normalizationService;
    private final TranscriptRepository transcriptRepository;
    private final TranscriptSegmentRepository segmentRepository;

    public TranscriptApi(
            TranscriptIngestionService ingestionService,
            TranscriptNormalizationService normalizationService,
            TranscriptRepository transcriptRepository,
            TranscriptSegmentRepository segmentRepository) {
        this.ingestionService = ingestionService;
        this.normalizationService = normalizationService;
        this.transcriptRepository = transcriptRepository;
        this.segmentRepository = segmentRepository;
    }

    public TranscriptApi(
            TranscriptIngestionService ingestionService,
            TranscriptNormalizationService normalizationService) {
        this(ingestionService, normalizationService, null, null);
    }

    public TranscriptUploadResponse uploadManualVtt(
            TenantId tenantId,
            UUID meetingOccurrenceId,
            String originalFilename,
            String declaredMimeType,
            byte[] content,
            String language,
            Integer retentionPolicyDays) {
        UploadManualVttResult result = ingestionService.uploadManualVtt(new UploadManualVttCommand(
                tenantId,
                meetingOccurrenceId,
                originalFilename,
                declaredMimeType,
                content,
                language,
                retentionPolicyDays));
        return new TranscriptUploadResponse(
                result.transcriptId().value(),
                result.contentHash().sha256Hex(),
                result.status(),
                result.rawStorageKey(),
                result.duplicate());
    }

    public TranscriptUploadResponse ingestFromGraphVtt(
            TenantId tenantId,
            UUID meetingOccurrenceId,
            String externalTranscriptId,
            byte[] content,
            String language) {
        UploadManualVttResult result = ingestionService.ingestFromGraphVtt(new IngestGraphVttCommand(
                tenantId,
                meetingOccurrenceId,
                externalTranscriptId,
                content,
                language));
        return new TranscriptUploadResponse(
                result.transcriptId().value(),
                result.contentHash().sha256Hex(),
                result.status(),
                result.rawStorageKey(),
                result.duplicate());
    }

    public List<TranscriptSegmentView> listSegmentsForMeeting(TenantId tenantId, UUID meetingOccurrenceId) {
        requireRepositories();
        Transcript transcript = transcriptRepository.findLatestProcessableByMeetingOccurrenceId(
                        tenantId, meetingOccurrenceId)
                .orElseThrow(() -> new TranscriptDomainException(
                        "TRANSCRIPT_NOT_FOUND",
                        "No transcript found for meeting occurrence"));
        return segmentRepository.findByTranscript(tenantId, transcript.id()).stream()
                .sorted(Comparator.comparingInt(s -> s.sequence()))
                .map(TranscriptSegmentView::from)
                .toList();
    }

    public List<TranscriptSegmentView> searchSegmentsForMeeting(
            TenantId tenantId,
            UUID meetingOccurrenceId,
            String query,
            int limit
    ) {
        requireRepositories();
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("query must not be blank");
        }
        if (limit < 1 || limit > 100) {
            throw new IllegalArgumentException("limit must be between 1 and 100");
        }
        Transcript transcript = transcriptRepository.findLatestProcessableByMeetingOccurrenceId(
                        tenantId, meetingOccurrenceId)
                .orElseThrow(() -> new TranscriptDomainException(
                        "TRANSCRIPT_NOT_FOUND",
                        "No transcript found for meeting occurrence"));
        return segmentRepository.searchByTranscript(tenantId, transcript.id(), query.trim(), limit).stream()
                .map(TranscriptSegmentView::from)
                .toList();
    }

    public boolean hasTranscriptForMeeting(TenantId tenantId, UUID meetingOccurrenceId) {
        requireRepositories();
        return transcriptRepository.findLatestProcessableByMeetingOccurrenceId(
                tenantId, meetingOccurrenceId).isPresent();
    }

    public boolean transcriptBelongsToMeeting(
            TenantId tenantId,
            UUID meetingOccurrenceId,
            UUID transcriptId
    ) {
        requireRepositories();
        if (tenantId == null || meetingOccurrenceId == null || transcriptId == null) {
            return false;
        }
        return transcriptRepository.findById(tenantId, TranscriptId.of(transcriptId))
                .map(Transcript::meetingOccurrenceId)
                .filter(meetingOccurrenceId::equals)
                .isPresent();
    }

    public Optional<UUID> latestProcessableTranscriptIdForMeeting(
            TenantId tenantId,
            UUID meetingOccurrenceId
    ) {
        requireRepositories();
        return transcriptRepository.findLatestProcessableByMeetingOccurrenceId(tenantId, meetingOccurrenceId)
                .map(transcript -> transcript.id().value());
    }

    public List<String> listSpeakersForMeeting(TenantId tenantId, UUID meetingOccurrenceId) {
        return listSegmentsForMeeting(tenantId, meetingOccurrenceId).stream()
                .map(TranscriptSegmentView::speaker)
                .distinct()
                .toList();
    }

    /**
     * True only when the latest processable transcript contains at least one real
     * speaker label. Generic ASR placeholders must not stop attribution re-polling.
     */
    public boolean hasSpeakerAttributionForMeeting(TenantId tenantId, UUID meetingOccurrenceId) {
        try {
            return listSegmentsForMeeting(tenantId, meetingOccurrenceId).stream()
                    .map(TranscriptSegmentView::speaker)
                    .anyMatch(speaker -> !PersonIdentityNormalizer.isGenericSpeakerLabel(speaker));
        } catch (TranscriptDomainException ex) {
            if ("TRANSCRIPT_NOT_FOUND".equals(ex.code())) {
                return false;
            }
            throw ex;
        }
    }

    public TranscriptDownloadAuthorizationResponse authorizeDownload(
            TenantId tenantId,
            TranscriptId transcriptId,
            Duration ttl) {
        AuthorizedUrl url = ingestionService.authorizeDownload(
                new AuthorizeTranscriptDownloadQuery(tenantId, transcriptId, ttl));
        return new TranscriptDownloadAuthorizationResponse(url.url(), url.expiresAt());
    }

    public TranscriptCommandResponse reparse(TenantId tenantId, TranscriptId transcriptId) {
        Transcript transcript = ingestionService.reparse(
                new ReparseTranscriptCommand(tenantId, transcriptId));
        return new TranscriptCommandResponse(transcript.id().value(), transcript.status());
    }

    public TranscriptNormalizeResponse normalize(
            TenantId tenantId, TranscriptId transcriptId, UUID dictionaryId) {
        return TranscriptNormalizeResponse.from(normalizationService.normalize(
                new NormalizeTranscriptCommand(tenantId, transcriptId, dictionaryId)));
    }

    public TranscriptNormalizeResponse renormalize(
            TenantId tenantId, TranscriptId transcriptId, UUID dictionaryId) {
        return normalize(tenantId, transcriptId, dictionaryId);
    }

    private void requireRepositories() {
        if (transcriptRepository == null || segmentRepository == null) {
            throw new TranscriptDomainException(
                    "TRANSCRIPT_QUERY_UNAVAILABLE",
                    "Transcript query repositories are not configured");
        }
    }
}
