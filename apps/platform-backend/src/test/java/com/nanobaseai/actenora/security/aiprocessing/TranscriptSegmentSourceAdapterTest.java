package com.nanobaseai.actenora.security.aiprocessing;

import com.nanobaseai.actenora.aiprocessing.domain.pipeline.SegmentInput;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;
import com.nanobaseai.actenora.transcript.api.TranscriptId;
import com.nanobaseai.actenora.transcript.domain.TranscriptSegment;
import com.nanobaseai.actenora.transcript.domain.dictionary.DictionaryEntry;
import com.nanobaseai.actenora.transcript.domain.dictionary.DictionaryEntryKind;
import com.nanobaseai.actenora.transcript.domain.dictionary.TenantDictionary;
import com.nanobaseai.actenora.transcript.infrastructure.persistence.InMemoryTenantDictionaryRepository;
import com.nanobaseai.actenora.transcript.infrastructure.persistence.InMemoryTranscriptSegmentRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TranscriptSegmentSourceAdapterTest {

    @Test
    void mapsSegmentsOrderedBySequence() {
        TenantId tenantId = TenantId.random();
        UUID transcriptUuid = UUID.randomUUID();
        TranscriptId transcriptId = TranscriptId.of(transcriptUuid);
        InMemoryTranscriptSegmentRepository repo = new InMemoryTranscriptSegmentRepository();
        repo.replaceAll(tenantId, transcriptId, List.of(
                segment(tenantId, transcriptId, 1, "Bob", "Second"),
                segment(tenantId, transcriptId, 0, "Alice", "First")
        ));

        TranscriptSegmentSourceAdapter adapter = new TranscriptSegmentSourceAdapter(repo);
        List<SegmentInput> inputs = adapter.segmentsFor(tenantId, transcriptUuid);

        assertEquals(2, inputs.size());
        assertEquals(0, inputs.get(0).sequence());
        assertEquals("Alice", inputs.get(0).speakerDisplayName());
        assertEquals("First", inputs.get(0).content());
        assertEquals(1, inputs.get(1).sequence());
    }

    @Test
    void emptyTranscriptReturnsEmptyList() {
        TranscriptSegmentSourceAdapter adapter =
                new TranscriptSegmentSourceAdapter(new InMemoryTranscriptSegmentRepository());
        assertTrue(adapter.segmentsFor(TenantId.random(), UUID.randomUUID()).isEmpty());
    }

    @Test
    void appliesActiveTenantDictionaryBeforeAiExtraction() {
        TenantId tenantId = TenantId.random();
        UUID transcriptUuid = UUID.randomUUID();
        TranscriptId transcriptId = TranscriptId.of(transcriptUuid);
        InMemoryTranscriptSegmentRepository repo = new InMemoryTranscriptSegmentRepository();
        repo.replaceAll(tenantId, transcriptId, List.of(
                segment(tenantId, transcriptId, 0, "BURAG", "En vidia için p o c başlatıyoruz")
        ));
        InMemoryTenantDictionaryRepository dictionaries = new InMemoryTenantDictionaryRepository();
        TenantDictionary dictionary = TenantDictionary.create(tenantId, "production", Instant.parse("2026-08-06T00:00:00Z"))
                .addEntry(DictionaryEntry.of(DictionaryEntryKind.SPEAKER, "Burak Ayık Kesisoğlu", List.of("BURAG")),
                        Instant.parse("2026-08-06T00:00:01Z"))
                .addEntry(DictionaryEntry.of(DictionaryEntryKind.PRODUCT, "NVIDIA", List.of("En vidia")),
                        Instant.parse("2026-08-06T00:00:02Z"))
                .addEntry(DictionaryEntry.of(DictionaryEntryKind.PROJECT, "PoC", List.of("p o c")),
                        Instant.parse("2026-08-06T00:00:03Z"));
        dictionaries.save(dictionary);

        List<SegmentInput> inputs = new TranscriptSegmentSourceAdapter(repo, dictionaries)
                .segmentsFor(tenantId, transcriptUuid);

        assertEquals(1, inputs.size());
        assertEquals("Burak Ayık Kesisoğlu", inputs.getFirst().speakerDisplayName());
        assertEquals("NVIDIA için PoC başlatıyoruz", inputs.getFirst().content());
        assertEquals(repo.findByTranscript(tenantId, transcriptId).getFirst().id().toString(),
                inputs.getFirst().segmentId());
    }

    private static TranscriptSegment segment(
            TenantId tenantId,
            TranscriptId transcriptId,
            int sequence,
            String speaker,
            String content
    ) {
        return new TranscriptSegment(
                UUID.randomUUID(),
                tenantId,
                transcriptId,
                sequence,
                "spk-" + sequence,
                speaker,
                sequence * 1000L,
                sequence * 1000L + 500,
                content
        );
    }
}
