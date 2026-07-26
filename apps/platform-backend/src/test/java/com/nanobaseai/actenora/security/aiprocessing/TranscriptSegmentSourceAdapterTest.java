package com.nanobaseai.actenora.security.aiprocessing;

import com.nanobaseai.actenora.aiprocessing.domain.pipeline.SegmentInput;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;
import com.nanobaseai.actenora.transcript.api.TranscriptId;
import com.nanobaseai.actenora.transcript.domain.TranscriptSegment;
import com.nanobaseai.actenora.transcript.infrastructure.persistence.InMemoryTranscriptSegmentRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

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
