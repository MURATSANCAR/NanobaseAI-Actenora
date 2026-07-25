package com.nanobaseai.actenora.transcript.application;

import com.nanobaseai.actenora.sharedkernel.domain.TenantId;
import com.nanobaseai.actenora.sharedkernel.port.storage.ObjectPutRequest;
import com.nanobaseai.actenora.sharedkernel.time.InstantClock;
import com.nanobaseai.actenora.transcript.api.TranscriptId;
import com.nanobaseai.actenora.transcript.application.port.in.NormalizeTranscriptCommand;
import com.nanobaseai.actenora.transcript.application.port.in.ParseTranscriptCommand;
import com.nanobaseai.actenora.transcript.domain.ContentHash;
import com.nanobaseai.actenora.transcript.domain.Transcript;
import com.nanobaseai.actenora.transcript.domain.TranscriptStatus;
import com.nanobaseai.actenora.transcript.domain.dictionary.DictionaryEntry;
import com.nanobaseai.actenora.transcript.domain.dictionary.DictionaryEntryKind;
import com.nanobaseai.actenora.transcript.domain.dictionary.TenantDictionary;
import com.nanobaseai.actenora.transcript.domain.event.TranscriptDomainEvents;
import com.nanobaseai.actenora.transcript.domain.normalization.NormalizationRunStatus;
import com.nanobaseai.actenora.transcript.domain.normalization.NormalizationVersion;
import com.nanobaseai.actenora.transcript.infrastructure.persistence.InMemoryNormalizationRunRepository;
import com.nanobaseai.actenora.transcript.infrastructure.persistence.InMemoryTenantDictionaryRepository;
import com.nanobaseai.actenora.transcript.infrastructure.persistence.InMemoryTranscriptRepository;
import com.nanobaseai.actenora.transcript.infrastructure.persistence.InMemoryTranscriptSegmentRepository;
import com.nanobaseai.actenora.transcript.infrastructure.storage.InMemoryObjectStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TranscriptNormalizationServiceTest {

    private final TenantId tenantId = TenantId.random();
    private final Instant fixed = Instant.parse("2026-07-25T15:00:00Z");
    private final InstantClock clock = new InstantClock(Clock.fixed(fixed, ZoneOffset.UTC));

    private InMemoryTranscriptRepository transcripts;
    private InMemoryTranscriptSegmentRepository segments;
    private InMemoryTenantDictionaryRepository dictionaries;
    private InMemoryNormalizationRunRepository runs;
    private InMemoryObjectStorage storage;
    private TranscriptNormalizationService service;
    private TenantDictionary dictionary;
    private Transcript transcript;

    @BeforeEach
    void setUp() {
        transcripts = new InMemoryTranscriptRepository();
        segments = new InMemoryTranscriptSegmentRepository();
        dictionaries = new InMemoryTenantDictionaryRepository();
        runs = new InMemoryNormalizationRunRepository();
        storage = new InMemoryObjectStorage();
        service = new TranscriptNormalizationService(
                transcripts, segments, dictionaries, runs, storage, clock);

        dictionary = TenantDictionary.create(tenantId, "default", fixed)
                .addEntry(DictionaryEntry.of(
                        DictionaryEntryKind.PRODUCT, "Actenora", List.of("aktenora")), fixed)
                .addEntry(DictionaryEntry.of(
                        DictionaryEntryKind.SPEAKER, "Ayşe Yılmaz", List.of("Ayşe")), fixed);
        dictionaries.save(dictionary);

        String vtt = """
                WEBVTT

                00:00:01.000 --> 00:00:02.000
                <v Ayşe>aktenora hazır mı?

                00:00:02.500 --> 00:00:03.500
                Evet, başlayalım.
                """;
        byte[] raw = vtt.getBytes(StandardCharsets.UTF_8);
        ContentHash hash = ContentHash.ofBytes(raw);
        transcript = Transcript.createManualUpload(
                tenantId, UUID.randomUUID(), hash, "tr", fixed);
        storage.put(ObjectPutRequest.builder()
                .key(transcript.rawStorageKey())
                .content(new java.io.ByteArrayInputStream(raw))
                .contentLength(raw.length)
                .contentType("text/vtt")
                .immutable(true)
                .build());
        transcripts.save(transcript);
    }

    @Test
    void parseEmitsTranscriptParsedAndKeepsTurkishContent() {
        TranscriptNormalizationService.ParseResult parsed =
                service.parse(new ParseTranscriptCommand(tenantId, transcript.id()));

        assertEquals(TranscriptStatus.PARSED, parsed.transcript().status());
        assertEquals(2, parsed.parseResult().segments().size());
        assertTrue(parsed.parseResult().segments().getFirst().content().contains("hazır"));
        assertInstanceOf(TranscriptDomainEvents.TranscriptParsed.class, parsed.domainEvents().getFirst());
        assertTrue(parsed.parseResult().segments().get(1).speakerDisplayName().isEmpty());
    }

    @Test
    void normalizeIsIdempotentForSameVersion() {
        service.parse(new ParseTranscriptCommand(tenantId, transcript.id()));

        TranscriptNormalizationService.NormalizeResult first =
                service.normalize(new NormalizeTranscriptCommand(tenantId, transcript.id(), dictionary.id()));
        assertFalse(first.idempotentHit());
        assertEquals(NormalizationRunStatus.SUCCEEDED, first.run().status());
        assertTrue(first.run().normalizedTranscriptHash().isPresent());
        assertEquals(
                NormalizationVersion.compose(dictionary.revision()),
                first.run().normalizationVersion());
        assertTrue(first.domainEvents().stream()
                .anyMatch(e -> e instanceof TranscriptDomainEvents.TranscriptNormalizationRequested));
        assertTrue(first.domainEvents().stream()
                .anyMatch(e -> e instanceof TranscriptDomainEvents.TranscriptNormalized));

        String hash1 = first.run().normalizedTranscriptHash().orElseThrow().sha256Hex();

        TranscriptNormalizationService.NormalizeResult second =
                service.normalize(new NormalizeTranscriptCommand(tenantId, transcript.id(), dictionary.id()));

        assertTrue(second.idempotentHit());
        assertEquals(first.run().id(), second.run().id());
        assertEquals(hash1, second.run().normalizedTranscriptHash().orElseThrow().sha256Hex());
        assertEquals(TranscriptStatus.NORMALIZED, second.transcript().status());
    }

    @Test
    void normalizeRewritesDictionaryTermsAndResolvesSpeaker() {
        service.parse(new ParseTranscriptCommand(tenantId, transcript.id()));
        TranscriptNormalizationService.NormalizeResult result =
                service.normalize(new NormalizeTranscriptCommand(tenantId, transcript.id(), null));

        assertEquals("Actenora hazır mı?", result.run().segments().getFirst().normalizedContent());
        assertEquals("Ayşe Yılmaz", result.run().segments().getFirst().speakerDisplayName().orElseThrow());
        assertEquals(
                result.run().segments().getFirst().originalSegmentId(),
                segments.findByTranscript(tenantId, transcript.id()).getFirst().id());
        assertTrue(storage.exists(transcript.normalizedStorageKey().orElseThrow()));
    }
}
