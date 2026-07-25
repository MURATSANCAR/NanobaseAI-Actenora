package com.nanobaseai.actenora.transcript.faz28;

import com.nanobaseai.actenora.sharedkernel.domain.TenantId;
import com.nanobaseai.actenora.sharedkernel.port.storage.ObjectStorageException;
import com.nanobaseai.actenora.sharedkernel.time.InstantClock;
import com.nanobaseai.actenora.transcript.application.TranscriptIngestionService;
import com.nanobaseai.actenora.transcript.application.VttUploadValidator;
import com.nanobaseai.actenora.transcript.application.port.in.UploadManualVttCommand;
import com.nanobaseai.actenora.transcript.application.port.in.UploadManualVttResult;
import com.nanobaseai.actenora.transcript.infrastructure.persistence.InMemoryTranscriptRepository;
import com.nanobaseai.actenora.transcript.infrastructure.persistence.InMemoryTranscriptSegmentRepository;
import com.nanobaseai.actenora.transcript.infrastructure.storage.InMemoryObjectStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * FAZ 28: MinIO temporary failure then recovery without data loss / duplicates.
 */
class MinioFailureScenarioTest {

    private static final Instant FIXED = Instant.parse("2026-07-25T12:00:00Z");

    private InMemoryObjectStorage objectStorage;
    private TranscriptIngestionService service;
    private TenantId tenant;
    private UUID meetingOccurrenceId;
    private byte[] validVtt;

    @BeforeEach
    void setUp() throws IOException {
        objectStorage = new InMemoryObjectStorage();
        service = new TranscriptIngestionService(
                new InMemoryTranscriptRepository(),
                new InMemoryTranscriptSegmentRepository(),
                objectStorage,
                new VttUploadValidator(1024),
                new InstantClock(Clock.fixed(FIXED, ZoneOffset.UTC)));
        tenant = TenantId.random();
        meetingOccurrenceId = UUID.randomUUID();
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("fixtures/valid.vtt")) {
            validVtt = in.readAllBytes();
        }
    }

    @Test
    void minioFailure_thenRecoveryUploadsOnceWithoutDuplicateBusinessRecord() {
        objectStorage.forceTimeout(true);
        ObjectStorageException timeout = assertThrows(
                ObjectStorageException.class,
                this::upload
        );
        assertEquals("OBJECT_STORAGE_TIMEOUT", timeout.code());

        objectStorage.forceTimeout(false);
        UploadManualVttResult first = upload();
        assertFalse(first.duplicate());
        assertTrue(objectStorage.exists(first.rawStorageKey()));

        UploadManualVttResult second = upload();
        assertTrue(second.duplicate());
        assertEquals(first.transcriptId(), second.transcriptId());
    }

    private UploadManualVttResult upload() {
        return service.uploadManualVtt(new UploadManualVttCommand(
                tenant,
                meetingOccurrenceId,
                "meeting.vtt",
                "text/vtt",
                validVtt,
                "en",
                30
        ));
    }
}
