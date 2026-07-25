package com.nanobaseai.actenora.transcript;

import com.nanobaseai.actenora.sharedkernel.domain.TenantId;
import com.nanobaseai.actenora.sharedkernel.port.storage.AuthorizedUrl;
import com.nanobaseai.actenora.sharedkernel.port.storage.ObjectStorageException;
import com.nanobaseai.actenora.sharedkernel.time.InstantClock;
import com.nanobaseai.actenora.transcript.api.TranscriptId;
import com.nanobaseai.actenora.transcript.application.TranscriptIngestionService;
import com.nanobaseai.actenora.transcript.application.VttUploadValidator;
import com.nanobaseai.actenora.transcript.application.port.in.AuthorizeTranscriptDownloadQuery;
import com.nanobaseai.actenora.transcript.application.port.in.ReparseTranscriptCommand;
import com.nanobaseai.actenora.transcript.application.port.in.RenormalizeTranscriptCommand;
import com.nanobaseai.actenora.transcript.application.port.in.UploadManualVttCommand;
import com.nanobaseai.actenora.transcript.application.port.in.UploadManualVttResult;
import com.nanobaseai.actenora.transcript.domain.ContentHash;
import com.nanobaseai.actenora.transcript.domain.Transcript;
import com.nanobaseai.actenora.transcript.domain.TranscriptDomainException;
import com.nanobaseai.actenora.transcript.domain.TranscriptStatus;
import com.nanobaseai.actenora.transcript.infrastructure.persistence.InMemoryTranscriptRepository;
import com.nanobaseai.actenora.transcript.infrastructure.persistence.InMemoryTranscriptSegmentRepository;
import com.nanobaseai.actenora.transcript.infrastructure.storage.InMemoryObjectStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TranscriptIngestionServiceTest {

    private static final Instant FIXED = Instant.parse("2026-07-25T12:00:00Z");

    private InMemoryObjectStorage objectStorage;
    private InMemoryTranscriptRepository transcriptRepository;
    private InMemoryTranscriptSegmentRepository segmentRepository;
    private TranscriptIngestionService service;
    private TenantId tenantA;
    private TenantId tenantB;
    private UUID meetingOccurrenceId;
    private byte[] validVtt;

    @BeforeEach
    void setUp() throws IOException {
        objectStorage = new InMemoryObjectStorage();
        transcriptRepository = new InMemoryTranscriptRepository();
        segmentRepository = new InMemoryTranscriptSegmentRepository();
        service = new TranscriptIngestionService(
                transcriptRepository,
                segmentRepository,
                objectStorage,
                new VttUploadValidator(1024),
                new InstantClock(Clock.fixed(FIXED, ZoneOffset.UTC)));
        tenantA = TenantId.random();
        tenantB = TenantId.random();
        meetingOccurrenceId = UUID.randomUUID();
        validVtt = readFixture("fixtures/valid.vtt");
    }

    @Test
    void validVttUploadStoresImmutableRawAndComputesHash() {
        UploadManualVttResult result = upload(tenantA, validVtt, "meeting.vtt", "text/vtt");

        assertFalse(result.duplicate());
        assertEquals(TranscriptStatus.PENDING_PARSE, result.status());
        assertTrue(objectStorage.exists(result.rawStorageKey()));
        assertTrue(result.rawStorageKey().startsWith("tenants/" + tenantA.value() + "/"));
        assertEquals(ContentHash.ofBytes(validVtt), result.contentHash());

        var meta = objectStorage.metadata(result.rawStorageKey()).orElseThrow();
        assertEquals("true", meta.userMeta("immutable").orElseThrow());
        assertEquals(result.contentHash().sha256Hex(), meta.userMeta("content-hash-sha256").orElseThrow());
        assertTrue(meta.retentionUntilOptional().isPresent());
    }

    @Test
    void malformedVttRejectedByMagicBytes() throws IOException {
        byte[] malformed = readFixture("fixtures/malformed.vtt");
        TranscriptDomainException ex = assertThrows(
                TranscriptDomainException.class,
                () -> upload(tenantA, malformed, "bad.vtt", "text/vtt"));
        assertEquals("INVALID_MAGIC", ex.code());
    }

    @Test
    void duplicateUploadDetectedByContentHash() {
        UploadManualVttResult first = upload(tenantA, validVtt, "meeting.vtt", "text/vtt");
        UploadManualVttResult second = upload(tenantA, validVtt, "meeting-copy.vtt", "text/vtt");

        assertTrue(second.duplicate());
        assertEquals(TranscriptStatus.DUPLICATE, second.status());
        assertEquals(first.transcriptId(), second.transcriptId());
        assertEquals(first.contentHash(), second.contentHash());
    }

    @Test
    void oversizedFileRejected() {
        VttUploadValidator tiny = new VttUploadValidator(32);
        TranscriptIngestionService tight = new TranscriptIngestionService(
                transcriptRepository,
                segmentRepository,
                objectStorage,
                tiny,
                new InstantClock(Clock.fixed(FIXED, ZoneOffset.UTC)));

        TranscriptDomainException ex = assertThrows(
                TranscriptDomainException.class,
                () -> tight.uploadManualVtt(new UploadManualVttCommand(
                        tenantA,
                        meetingOccurrenceId,
                        "big.vtt",
                        "text/vtt",
                        validVtt,
                        "en",
                        30)));
        assertEquals("FILE_TOO_LARGE", ex.code());
    }

    @Test
    void wrongMimeRejected() {
        TranscriptDomainException ex = assertThrows(
                TranscriptDomainException.class,
                () -> upload(tenantA, validVtt, "meeting.vtt", "application/pdf"));
        assertEquals("INVALID_MIME", ex.code());
    }

    @Test
    void minioTimeoutSurfacesAsObjectStorageTimeout() {
        objectStorage.forceTimeout(true);
        ObjectStorageException ex = assertThrows(
                ObjectStorageException.class,
                () -> upload(tenantA, validVtt, "meeting.vtt", "text/vtt"));
        assertEquals("OBJECT_STORAGE_TIMEOUT", ex.code());
    }

    @Test
    void unauthorizedDownloadAcrossTenantsDenied() {
        UploadManualVttResult uploaded = upload(tenantA, validVtt, "meeting.vtt", "text/vtt");

        TranscriptDomainException ex = assertThrows(
                TranscriptDomainException.class,
                () -> service.authorizeDownload(new AuthorizeTranscriptDownloadQuery(
                        tenantB,
                        uploaded.transcriptId(),
                        Duration.ofMinutes(5))));
        assertEquals("TRANSCRIPT_NOT_FOUND", ex.code());
    }

    @Test
    void tenantIsolationOnKeysAndLookup() {
        UploadManualVttResult uploaded = upload(tenantA, validVtt, "meeting.vtt", "text/vtt");
        assertTrue(uploaded.rawStorageKey().contains(tenantA.value().toString()));
        assertFalse(uploaded.rawStorageKey().contains(tenantB.value().toString()));

        assertTrue(transcriptRepository.findById(tenantA, uploaded.transcriptId()).isPresent());
        assertTrue(transcriptRepository.findById(tenantB, uploaded.transcriptId()).isEmpty());
    }

    @Test
    void hashConsistencyBetweenUploadAndStoredBytes() throws Exception {
        UploadManualVttResult uploaded = upload(tenantA, validVtt, "meeting.vtt", "text/vtt");
        try (InputStream in = objectStorage.get(uploaded.rawStorageKey())) {
            byte[] stored = in.readAllBytes();
            assertEquals(ContentHash.ofBytes(validVtt), ContentHash.ofBytes(stored));
            assertEquals(uploaded.contentHash(), ContentHash.ofBytes(stored));
        }
    }

    @Test
    void reparseBuildsSegmentsAndIsSeparateFromRenormalize() {
        UploadManualVttResult uploaded = upload(tenantA, validVtt, "meeting.vtt", "text/vtt");

        Transcript parsed = service.reparse(new ReparseTranscriptCommand(tenantA, uploaded.transcriptId()));
        assertEquals(TranscriptStatus.PARSED, parsed.status());
        assertEquals(2, segmentRepository.findByTranscript(tenantA, uploaded.transcriptId()).size());

        Transcript pendingNorm = service.renormalize(
                new RenormalizeTranscriptCommand(tenantA, uploaded.transcriptId()));
        assertEquals(TranscriptStatus.PENDING_NORMALIZE, pendingNorm.status());
    }

    @Test
    void reparseMalformedStoredContentFails() {
        UploadManualVttResult uploaded = upload(tenantA, validVtt, "meeting.vtt", "text/vtt");
        // Simulate corruption after store by replacing repository hash path is not possible;
        // instead upload a WEBVTT header-only file that passes magic but has no cues via direct store.
        byte[] headerOnly = "WEBVTT\n\n".getBytes(StandardCharsets.UTF_8);
        ContentHash hash = ContentHash.ofBytes(headerOnly);
        TranscriptId id = TranscriptId.of(UUID.randomUUID());
        String key = "tenants/" + tenantA.value() + "/transcripts/" + meetingOccurrenceId + "/" + id.value() + "/raw.vtt";
        objectStorage.put(com.nanobaseai.actenora.sharedkernel.port.storage.ObjectPutRequest.ofBytes(
                key, headerOnly, "text/vtt"));
        Transcript orphan = Transcript.builder()
                .id(id)
                .tenantId(tenantA)
                .meetingOccurrenceId(meetingOccurrenceId)
                .source(com.nanobaseai.actenora.transcript.domain.TranscriptSource.MANUAL_UPLOAD)
                .sourceFormat(com.nanobaseai.actenora.transcript.domain.SourceFormat.VTT)
                .rawStorageKey(key)
                .contentHash(hash)
                .status(TranscriptStatus.PENDING_PARSE)
                .fetchedAt(FIXED)
                .createdAt(FIXED)
                .updatedAt(FIXED)
                .version(0)
                .build();
        transcriptRepository.save(orphan);

        TranscriptDomainException ex = assertThrows(
                TranscriptDomainException.class,
                () -> service.reparse(new ReparseTranscriptCommand(tenantA, id)));
        assertEquals("MALFORMED_VTT", ex.code());
        assertEquals(TranscriptStatus.FAILED, transcriptRepository.findById(tenantA, id).orElseThrow().status());
    }

    @Test
    void authorizedDownloadReturnsUrlForOwningTenant() {
        UploadManualVttResult uploaded = upload(tenantA, validVtt, "meeting.vtt", "text/vtt");
        AuthorizedUrl url = service.authorizeDownload(new AuthorizeTranscriptDownloadQuery(
                tenantA, uploaded.transcriptId(), Duration.ofMinutes(5)));
        assertNotNull(url.url());
        assertTrue(url.expiresAt().isAfter(FIXED.minusSeconds(1)));
    }

    @Test
    void expiredAuthorizedUrlRejected() {
        UploadManualVttResult uploaded = upload(tenantA, validVtt, "meeting.vtt", "text/vtt");
        AuthorizedUrl url = service.authorizeDownload(new AuthorizeTranscriptDownloadQuery(
                tenantA, uploaded.transcriptId(), Duration.ofSeconds(1)));
        Instant afterExpiry = url.expiresAt().plusSeconds(1);
        ObjectStorageException ex = assertThrows(
                ObjectStorageException.class,
                () -> objectStorage.resolveAuthorizedUrl(url, afterExpiry));
        assertEquals("SIGNED_URL_EXPIRED", ex.code());
    }

    @Test
    void signedUrlTtlIsClampedToMax() {
        UploadManualVttResult uploaded = upload(tenantA, validVtt, "meeting.vtt", "text/vtt");
        AuthorizeTranscriptDownloadQuery query = new AuthorizeTranscriptDownloadQuery(
                tenantA, uploaded.transcriptId(), Duration.ofDays(30));
        assertEquals(AuthorizeTranscriptDownloadQuery.MAX_TTL, query.ttl());
    }

    @Test
    void retentionDeletesTranscriptObjectAndMarksDeleted() {
        UploadManualVttResult uploaded = upload(tenantA, validVtt, "meeting.vtt", "text/vtt");
        assertTrue(objectStorage.exists(uploaded.rawStorageKey()));

        service.deleteForRetention(tenantA, uploaded.transcriptId());

        assertFalse(objectStorage.exists(uploaded.rawStorageKey()));
        assertEquals(
                TranscriptStatus.DELETED,
                transcriptRepository.findById(tenantA, uploaded.transcriptId()).orElseThrow().status());
    }

    @Test
    void unauthorizedObjectKeyAccessDenied() {
        UploadManualVttResult uploaded = upload(tenantA, validVtt, "meeting.vtt", "text/vtt");
        TranscriptDomainException ex = assertThrows(
                TranscriptDomainException.class,
                () -> com.nanobaseai.actenora.transcript.domain.TenantObjectKeys.assertTenantOwnsKey(
                        tenantB, uploaded.rawStorageKey()));
        assertEquals("TENANT_KEY_MISMATCH", ex.code());
    }

    @Test
    void tenantIsolationPenetrationDeniesCrossTenantObjectGet() {
        UploadManualVttResult uploaded = upload(tenantA, validVtt, "meeting.vtt", "text/vtt");
        // Attacker in tenant B cannot authorize download for A's transcript id
        TranscriptDomainException missing = assertThrows(
                TranscriptDomainException.class,
                () -> service.authorizeDownload(new AuthorizeTranscriptDownloadQuery(
                        tenantB, uploaded.transcriptId(), Duration.ofSeconds(30))));
        assertEquals("TRANSCRIPT_NOT_FOUND", missing.code());
        // Direct key ownership check fails for foreign tenant
        assertThrows(
                TranscriptDomainException.class,
                () -> com.nanobaseai.actenora.transcript.domain.TenantObjectKeys.assertTenantOwnsKey(
                        tenantB, uploaded.rawStorageKey()));
    }

    @Test
    void unknownMeetingOccurrenceRejected() {
        TranscriptIngestionService guarded = new TranscriptIngestionService(
                transcriptRepository,
                segmentRepository,
                objectStorage,
                new VttUploadValidator(1024),
                new InstantClock(Clock.fixed(FIXED, ZoneOffset.UTC)),
                com.nanobaseai.actenora.transcript.application.port.out.TranscriptEventPublisher.noop(),
                new com.nanobaseai.actenora.transcript.infrastructure.persistence.InMemoryKnownMeetingOccurrenceStore());

        TranscriptDomainException ex = assertThrows(
                TranscriptDomainException.class,
                () -> guarded.uploadManualVtt(new UploadManualVttCommand(
                        tenantA,
                        UUID.randomUUID(),
                        "meeting.vtt",
                        "text/vtt",
                        validVtt,
                        "en",
                        30)));
        assertEquals("UNKNOWN_MEETING_OCCURRENCE", ex.code());
    }

    @Test
    void getReturnsMetadataWithoutContent() {
        UploadManualVttResult uploaded = upload(tenantA, validVtt, "meeting.vtt", "text/vtt");
        Transcript detail = service.get(tenantA, uploaded.transcriptId());
        assertEquals(uploaded.transcriptId(), detail.id());
        assertEquals(TranscriptStatus.PENDING_PARSE, detail.status());
        assertEquals(ContentHash.ofBytes(validVtt), detail.contentHash());
    }

    private UploadManualVttResult upload(TenantId tenant, byte[] bytes, String filename, String mime) {
        return service.uploadManualVtt(new UploadManualVttCommand(
                tenant,
                meetingOccurrenceId,
                filename,
                mime,
                bytes,
                "en",
                30));
    }

    private static byte[] readFixture(String path) throws IOException {
        try (InputStream in = TranscriptIngestionServiceTest.class.getClassLoader().getResourceAsStream(path)) {
            assertNotNull(in, "missing fixture " + path);
            return in.readAllBytes();
        }
    }
}
