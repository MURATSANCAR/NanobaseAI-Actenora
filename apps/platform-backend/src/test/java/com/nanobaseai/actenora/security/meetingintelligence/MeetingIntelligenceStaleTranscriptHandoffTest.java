package com.nanobaseai.actenora.security.meetingintelligence;

import com.nanobaseai.actenora.aiprocessing.application.port.MeetingNoteHandoffPort;
import com.nanobaseai.actenora.aiprocessing.application.port.TranscriptSegmentSourcePort;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.FinalNoteDraft;
import com.nanobaseai.actenora.meetingintelligence.api.EvidenceValidationApi;
import com.nanobaseai.actenora.meetingintelligence.api.MeetingIntelligenceApi;
import com.nanobaseai.actenora.meetingintelligence.application.port.MeetingIntelligenceAuditPort;
import com.nanobaseai.actenora.meetingintelligence.application.port.NoteArtifactStoragePort;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;
import com.nanobaseai.actenora.transcript.api.TranscriptApi;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MeetingIntelligenceStaleTranscriptHandoffTest {

    @Test
    void dropsCompletedOldPipelineWhenMeetingHasNewerTranscript() {
        UUID tenantId = UUID.randomUUID();
        UUID meetingId = UUID.randomUUID();
        UUID oldTranscriptId = UUID.randomUUID();
        UUID latestTranscriptId = UUID.randomUUID();
        TranscriptApi transcriptApi = mock(TranscriptApi.class);
        when(transcriptApi.latestProcessableTranscriptIdForMeeting(TenantId.of(tenantId), meetingId))
                .thenReturn(Optional.of(latestTranscriptId));
        MeetingIntelligenceApi intelligence = mock(MeetingIntelligenceApi.class);
        MeetingIntelligenceAuditPort audit = mock(MeetingIntelligenceAuditPort.class);
        var adapter = new MeetingIntelligenceHandoffAdapter(
                intelligence,
                mock(EvidenceValidationApi.class),
                mock(TranscriptSegmentSourcePort.class),
                audit,
                Optional.empty(),
                NoteArtifactStoragePort.noop(),
                Optional.empty(), Optional.empty(), Optional.empty(),
                () -> null, null, List.of(), Optional.of(transcriptApi));
        FinalNoteDraft draft = new FinalNoteDraft(
                "", List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), 0.8d, false);

        Optional<UUID> result = adapter.handoff(new MeetingNoteHandoffPort.HandoffCommand(
                tenantId, meetingId, oldTranscriptId, UUID.randomUUID(),
                "model", "prompt", "schema", null, null, draft));

        assertTrue(result.isEmpty());
        verify(intelligence, never()).mapAiCandidates(any());
        verify(audit).record(any(), any(),
                org.mockito.ArgumentMatchers.eq("STALE_TRANSCRIPT_HANDOFF_DROPPED"),
                any(), any(), any(), any());
    }
}
