package com.nanobaseai.actenora.microsoftconnection.infrastructure.graph;

import com.nanobaseai.actenora.microsoftconnection.application.model.TranscriptAvailability;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GraphTranscriptGatewayTest {

    @Test
    void resolveOrganizerUserIdPrefersOidEmbeddedInMeetingId() {
        String organizerOid = "8d69a8bc-0165-4fd3-ba31-541933b5d1f0";
        String meetingId = Base64.getEncoder().withoutPadding().encodeToString(
                ("1*" + organizerOid + "*0**19:meeting_abc@thread.v2").getBytes(StandardCharsets.UTF_8));

        assertEquals(
                organizerOid,
                GraphTranscriptGateway.resolveOrganizerUserId("muratsancar@nanobase.ai", meetingId));
    }

    @Test
    void resolveOrganizerUserIdFallsBackWhenMeetingIdHasNoOid() {
        assertEquals("user-1", GraphTranscriptGateway.resolveOrganizerUserId("user-1", "plain-meeting-id"));
    }

    @Test
    void transcriptSelectionUsesNewestRevisionInsteadOfGraphArrayOrder() {
        TranscriptAvailability availability = new TranscriptAvailability(
                "meeting-1",
                true,
                List.of(
                        new TranscriptAvailability.TranscriptRef(
                                "new", Instant.parse("2026-08-06T10:05:00Z")),
                        new TranscriptAvailability.TranscriptRef(
                                "old", Instant.parse("2026-08-06T10:00:00Z"))
                ));

        assertEquals("new", availability.latestTranscript().orElseThrow().transcriptId());
        assertEquals("new", availability.firstTranscript().orElseThrow().transcriptId());
    }
}
