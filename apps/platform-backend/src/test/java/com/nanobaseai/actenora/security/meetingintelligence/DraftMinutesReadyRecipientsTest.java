package com.nanobaseai.actenora.security.meetingintelligence;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DraftMinutesReadyRecipientsTest {

    @Test
    void parseAdditionalRecipientsSplitsCsvAndTrims() {
        assertEquals(
                List.of("muratsancar@nanobase.ai", "ops@nanobase.ai"),
                MeetingIntelligenceHandoffAdapter.parseAdditionalRecipients(
                        " muratsancar@nanobase.ai , ops@nanobase.ai ")
        );
    }

    @Test
    void mergeKeepsOrganizerAndConfiguredExtrasWithoutCaseDuplicates() {
        assertEquals(
                List.of("organizer@nanobase.ai", "muratsancar@nanobase.ai"),
                MeetingIntelligenceHandoffAdapter.mergeDraftRecipients(
                        "organizer@nanobase.ai",
                        List.of("muratsancar@nanobase.ai", "Organizer@nanobase.ai")
                )
        );
    }

    @Test
    void mergeStillSendsConfiguredExtrasWhenOrganizerMissing() {
        assertEquals(
                List.of("muratsancar@nanobase.ai"),
                MeetingIntelligenceHandoffAdapter.mergeDraftRecipients(
                        null,
                        List.of("muratsancar@nanobase.ai")
                )
        );
    }
}
