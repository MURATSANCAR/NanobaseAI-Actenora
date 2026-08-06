package com.nanobaseai.actenora.transcript.api;

import com.nanobaseai.actenora.sharedkernel.domain.TenantId;
import com.nanobaseai.actenora.transcript.domain.ContentHash;
import com.nanobaseai.actenora.transcript.domain.Transcript;
import com.nanobaseai.actenora.transcript.domain.TranscriptSegment;
import com.nanobaseai.actenora.transcript.infrastructure.persistence.InMemoryTranscriptRepository;
import com.nanobaseai.actenora.transcript.infrastructure.persistence.InMemoryTranscriptSegmentRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TranscriptApiSpeakerAttributionTest {

    @Test
    void genericLabelsDoNotCountAsSpeakerAttribution() {
        Fixture fixture = fixture("Speaker 1");
        assertFalse(fixture.api.hasSpeakerAttributionForMeeting(fixture.tenantId, fixture.meetingId));
    }

    @Test
    void namedGraphSpeakerCountsAsAttribution() {
        Fixture fixture = fixture("Ali BAĞATIR (GMY)");
        assertTrue(fixture.api.hasSpeakerAttributionForMeeting(fixture.tenantId, fixture.meetingId));
    }

    private static Fixture fixture(String speaker) {
        TenantId tenantId = TenantId.random();
        UUID meetingId = UUID.randomUUID();
        Instant now = Instant.parse("2026-08-06T10:00:00Z");
        Transcript transcript = Transcript.createGraphIngest(
                tenantId, meetingId, "graph-1", ContentHash.ofUtf8("vtt"), "tr", now);
        transcript.markParsed(now.plusSeconds(1));
        InMemoryTranscriptRepository transcripts = new InMemoryTranscriptRepository();
        transcripts.save(transcript);
        InMemoryTranscriptSegmentRepository segments = new InMemoryTranscriptSegmentRepository();
        segments.replaceAll(tenantId, transcript.id(), List.of(new TranscriptSegment(
                UUID.randomUUID(), tenantId, transcript.id(), 0, "speaker-1", speaker,
                0, 1000, "Merhaba")));
        return new Fixture(new TranscriptApi(null, null, transcripts, segments), tenantId, meetingId);
    }

    private record Fixture(TranscriptApi api, TenantId tenantId, UUID meetingId) {
    }
}
