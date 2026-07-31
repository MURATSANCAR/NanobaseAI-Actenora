package com.nanobaseai.actenora.microsoftconnection.infrastructure.graph;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

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
}
