package com.nanobaseai.actenora.transcript.application;

import com.nanobaseai.actenora.sharedkernel.domain.TenantId;
import com.nanobaseai.actenora.sharedkernel.port.storage.AuthorizedUrl;
import com.nanobaseai.actenora.sharedkernel.port.storage.ObjectPutRequest;
import com.nanobaseai.actenora.sharedkernel.port.storage.ObjectStorage;
import com.nanobaseai.actenora.sharedkernel.time.InstantClock;
import com.nanobaseai.actenora.transcript.api.TranscriptId;
import com.nanobaseai.actenora.transcript.application.port.in.AuthorizeTranscriptDownloadQuery;
import com.nanobaseai.actenora.transcript.application.port.in.IngestGraphVttCommand;
import com.nanobaseai.actenora.transcript.application.port.in.ReparseTranscriptCommand;
import com.nanobaseai.actenora.transcript.application.port.in.RenormalizeTranscriptCommand;
import com.nanobaseai.actenora.transcript.application.port.in.UploadManualVttCommand;
import com.nanobaseai.actenora.transcript.application.port.in.UploadManualVttResult;
import com.nanobaseai.actenora.transcript.application.port.out.KnownMeetingOccurrenceStore;
import com.nanobaseai.actenora.transcript.application.port.out.TranscriptEventPublisher;
import com.nanobaseai.actenora.transcript.application.port.out.TranscriptRepository;
import com.nanobaseai.actenora.transcript.application.port.out.TranscriptSegmentRepository;
import com.nanobaseai.actenora.transcript.domain.ContentHash;
import com.nanobaseai.actenora.transcript.domain.StructuralVttParser;
import com.nanobaseai.actenora.transcript.domain.TenantObjectKeys;
import com.nanobaseai.actenora.transcript.domain.Transcript;
import com.nanobaseai.actenora.transcript.domain.TranscriptDomainException;
import com.nanobaseai.actenora.transcript.domain.TranscriptSegment;
import com.nanobaseai.actenora.transcript.domain.TranscriptStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Transcript ingest / download / reparse / renormalize use cases.
 * Intentionally never logs transcript content or raw bytes.
 */
public class TranscriptIngestionService {

    private static final Logger log = LoggerFactory.getLogger(TranscriptIngestionService.class);

    private final TranscriptRepository transcriptRepository;
    private final TranscriptSegmentRepository segmentRepository;
    private final ObjectStorage objectStorage;
    private final VttUploadValidator validator;
    private final InstantClock clock;
    private final TranscriptEventPublisher eventPublisher;
    private final KnownMeetingOccurrenceStore knownMeetings;

    public TranscriptIngestionService(
            TranscriptRepository transcriptRepository,
            TranscriptSegmentRepository segmentRepository,
            ObjectStorage objectStorage,
            VttUploadValidator validator,
            InstantClock clock) {
        this(transcriptRepository, segmentRepository, objectStorage, validator, clock,
                TranscriptEventPublisher.noop(), KnownMeetingOccurrenceStore.allowAll());
    }

    public TranscriptIngestionService(
            TranscriptRepository transcriptRepository,
            TranscriptSegmentRepository segmentRepository,
            ObjectStorage objectStorage,
            VttUploadValidator validator,
            InstantClock clock,
            TranscriptEventPublisher eventPublisher) {
        this(transcriptRepository, segmentRepository, objectStorage, validator, clock,
                eventPublisher, KnownMeetingOccurrenceStore.allowAll());
    }

    public TranscriptIngestionService(
            TranscriptRepository transcriptRepository,
            TranscriptSegmentRepository segmentRepository,
            ObjectStorage objectStorage,
            VttUploadValidator validator,
            InstantClock clock,
            TranscriptEventPublisher eventPublisher,
            KnownMeetingOccurrenceStore knownMeetings) {
        this.transcriptRepository = transcriptRepository;
        this.segmentRepository = segmentRepository;
        this.objectStorage = objectStorage;
        this.validator = validator;
        this.clock = clock;
        this.eventPublisher = eventPublisher == null ? TranscriptEventPublisher.noop() : eventPublisher;
        this.knownMeetings = Objects.requireNonNullElseGet(knownMeetings, KnownMeetingOccurrenceStore::allowAll);
    }

    public UploadManualVttResult uploadManualVtt(UploadManualVttCommand command) {
        validator.validate(command.originalFilename(), command.declaredMimeType(), command.content());

        if (!knownMeetings.isKnown(command.tenantId(), command.meetingOccurrenceId())) {
            throw new TranscriptDomainException(
                    "UNKNOWN_MEETING_OCCURRENCE",
                    "Meeting occurrence is not known for this tenant");
        }

        ContentHash hash = ContentHash.ofBytes(command.content());
        Optional<Transcript> existing = transcriptRepository.findByTenantAndContentHash(
                command.tenantId(), hash);
        if (existing.isPresent()) {
            Transcript duplicate = existing.get();
            log.info(
                    "Duplicate transcript upload rejected transcriptId={} tenantId={} contentHash={} sizeBytes={}",
                    duplicate.id().value(),
                    command.tenantId().value(),
                    hash.sha256Hex(),
                    command.content().length);
            return new UploadManualVttResult(
                    duplicate.id(),
                    hash,
                    TranscriptStatus.DUPLICATE,
                    duplicate.rawStorageKey(),
                    true);
        }

        Instant now = clock.now();
        Transcript transcript = Transcript.createManualUpload(
                command.tenantId(),
                command.meetingOccurrenceId(),
                hash,
                command.language(),
                now);

        Map<String, String> metadata = new HashMap<>();
        metadata.put("tenant-id", command.tenantId().value().toString());
        metadata.put("transcript-id", transcript.id().value().toString());
        metadata.put("meeting-occurrence-id", command.meetingOccurrenceId().toString());
        metadata.put("content-hash-sha256", hash.sha256Hex());
        metadata.put("immutable", "true");
        metadata.put("source-format", "vtt");
        if (command.retentionPolicyDays() != null && command.retentionPolicyDays() > 0) {
            Instant retentionUntil = now.plus(command.retentionPolicyDays(), ChronoUnit.DAYS);
            metadata.put("retention-until", retentionUntil.toString());
            metadata.put("retention-policy-days", command.retentionPolicyDays().toString());
        }

        objectStorage.put(ObjectPutRequest.builder()
                .key(transcript.rawStorageKey())
                .content(new java.io.ByteArrayInputStream(command.content()))
                .contentLength(command.content().length)
                .contentType("text/vtt")
                .metadata(metadata)
                .immutable(true)
                .build());

        transcript.markPendingParse(now);
        transcriptRepository.save(transcript);
        // Outbox enqueue is local to transcript schema — survives process restart; no XA.
        eventPublisher.publishIngested(transcript);

        log.info(
                "Transcript stored transcriptId={} tenantId={} meetingOccurrenceId={} contentHash={} sizeBytes={} key={}",
                transcript.id().value(),
                command.tenantId().value(),
                command.meetingOccurrenceId(),
                hash.sha256Hex(),
                command.content().length,
                transcript.rawStorageKey());

        return new UploadManualVttResult(
                transcript.id(),
                hash,
                transcript.status(),
                transcript.rawStorageKey(),
                false);
    }

    public UploadManualVttResult ingestFromGraphVtt(IngestGraphVttCommand command) {
        validator.validate("teams-graph.vtt", "text/vtt", command.content());

        if (!knownMeetings.isKnown(command.tenantId(), command.meetingOccurrenceId())) {
            throw new TranscriptDomainException(
                    "UNKNOWN_MEETING_OCCURRENCE",
                    "Meeting occurrence is not known for this tenant");
        }

        Optional<Transcript> existingByExternal = transcriptRepository.findByTenantAndExternalTranscriptId(
                command.tenantId(), command.externalTranscriptId());
        if (existingByExternal.isPresent()) {
            Transcript duplicate = existingByExternal.get();
            return new UploadManualVttResult(
                    duplicate.id(),
                    duplicate.contentHash(),
                    duplicate.status(),
                    duplicate.rawStorageKey(),
                    true);
        }

        ContentHash hash = ContentHash.ofBytes(command.content());
        Optional<Transcript> existingByHash = transcriptRepository.findByTenantAndContentHash(
                command.tenantId(), hash);
        if (existingByHash.isPresent()) {
            Transcript duplicate = existingByHash.get();
            return new UploadManualVttResult(
                    duplicate.id(),
                    hash,
                    TranscriptStatus.DUPLICATE,
                    duplicate.rawStorageKey(),
                    true);
        }

        Instant now = clock.now();
        Transcript transcript = Transcript.createGraphIngest(
                command.tenantId(),
                command.meetingOccurrenceId(),
                command.externalTranscriptId(),
                hash,
                command.language(),
                now);

        Map<String, String> metadata = new HashMap<>();
        metadata.put("tenant-id", command.tenantId().value().toString());
        metadata.put("transcript-id", transcript.id().value().toString());
        metadata.put("meeting-occurrence-id", command.meetingOccurrenceId().toString());
        metadata.put("content-hash-sha256", hash.sha256Hex());
        metadata.put("external-transcript-id", command.externalTranscriptId());
        metadata.put("immutable", "true");
        metadata.put("source-format", "vtt");
        metadata.put("source", "teams-graph");

        objectStorage.put(ObjectPutRequest.builder()
                .key(transcript.rawStorageKey())
                .content(new java.io.ByteArrayInputStream(command.content()))
                .contentLength(command.content.length)
                .contentType("text/vtt")
                .metadata(metadata)
                .immutable(true)
                .build());

        transcript.markPendingParse(now);
        transcriptRepository.save(transcript);
        eventPublisher.publishIngested(transcript);

        log.info(
                "Graph transcript stored transcriptId={} tenantId={} meetingOccurrenceId={} externalTranscriptId={} sizeBytes={}",
                transcript.id().value(),
                command.tenantId().value(),
                command.meetingOccurrenceId(),
                command.externalTranscriptId(),
                command.content().length);

        Transcript parsed = reparse(new ReparseTranscriptCommand(command.tenantId(), transcript.id()));
        return new UploadManualVttResult(
                parsed.id(),
                hash,
                parsed.status(),
                parsed.rawStorageKey(),
                false);
    }

    public AuthorizedUrl authorizeDownload(AuthorizeTranscriptDownloadQuery query) {
        Transcript transcript = requireOwned(query.requesterTenantId(), query.transcriptId());
        TenantObjectKeys.assertTenantOwnsKey(query.requesterTenantId(), transcript.rawStorageKey());
        if (!objectStorage.exists(transcript.rawStorageKey())) {
            throw new TranscriptDomainException("RAW_MISSING", "Raw transcript object missing");
        }
        log.info(
                "Authorized transcript download transcriptId={} tenantId={} ttlSeconds={}",
                transcript.id().value(),
                query.requesterTenantId().value(),
                query.ttl().toSeconds());
        return objectStorage.generateAuthorizedUrl(transcript.rawStorageKey(), query.ttl());
    }

    public Transcript reparse(ReparseTranscriptCommand command) {
        Transcript transcript = requireOwned(command.tenantId(), command.transcriptId());
        TenantObjectKeys.assertTenantOwnsKey(command.tenantId(), transcript.rawStorageKey());

        byte[] raw;
        try (InputStream in = objectStorage.get(transcript.rawStorageKey())) {
            raw = in.readAllBytes();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new TranscriptDomainException("REPARSE_READ_FAILED", "Failed to read raw transcript");
        }

        Instant now = clock.now();
        try {
            List<TranscriptSegment> segments = StructuralVttParser.parse(
                    transcript.tenantId(), transcript.id(), raw);
            ContentHash rawHash = ContentHash.ofBytes(raw);
            if (!rawHash.equals(transcript.contentHash())) {
                throw new TranscriptDomainException(
                        "HASH_MISMATCH",
                        "Stored raw content hash does not match transcript metadata");
            }
            segmentRepository.replaceAll(command.tenantId(), transcript.id(), segments);
            transcript.markParsed(now);
            transcriptRepository.save(transcript);
            eventPublisher.publishReady(transcript, segments.size());
            log.info(
                    "Transcript reparsed transcriptId={} tenantId={} segmentCount={}",
                    transcript.id().value(),
                    command.tenantId().value(),
                    segments.size());
            return transcript;
        } catch (TranscriptDomainException e) {
            transcript.markFailed(now);
            transcriptRepository.save(transcript);
            throw e;
        }
    }

    public Transcript renormalize(RenormalizeTranscriptCommand command) {
        Transcript transcript = requireOwned(command.tenantId(), command.transcriptId());
        Instant now = clock.now();
        transcript.markPendingNormalize(now);
        transcriptRepository.save(transcript);
        log.info(
                "Transcript renormalize requested transcriptId={} tenantId={} normalizedKey={}",
                transcript.id().value(),
                command.tenantId().value(),
                transcript.normalizedStorageKey().orElse(null));
        return transcript;
    }

    /**
     * FAZ 27 retention deletion — removes object bytes and marks transcript DELETED.
     * Caller must already have enforced legal-hold checks.
     */
    public void deleteForRetention(TenantId tenantId, TranscriptId transcriptId) {
        Transcript transcript = requireOwned(tenantId, transcriptId);
        if (transcript.status() == TranscriptStatus.DELETED) {
            return;
        }
        TenantObjectKeys.assertTenantOwnsKey(tenantId, transcript.rawStorageKey());
        if (objectStorage.exists(transcript.rawStorageKey())) {
            objectStorage.delete(transcript.rawStorageKey());
        }
        transcript.normalizedStorageKey().ifPresent(key -> {
            TenantObjectKeys.assertTenantOwnsKey(tenantId, key);
            if (objectStorage.exists(key)) {
                objectStorage.delete(key);
            }
        });
        Instant now = clock.now();
        transcript.markDeleted(now);
        transcriptRepository.save(transcript);
        log.info(
                "Transcript deleted for retention transcriptId={} tenantId={}",
                transcript.id().value(),
                tenantId.value());
    }

    private Transcript requireOwned(TenantId tenantId, TranscriptId id) {
        return transcriptRepository.findById(tenantId, id)
                .orElseThrow(() -> new TranscriptDomainException(
                        "TRANSCRIPT_NOT_FOUND",
                        "Transcript not found for tenant"));
    }

    public Transcript get(TenantId tenantId, TranscriptId id) {
        return requireOwned(tenantId, id);
    }
}
