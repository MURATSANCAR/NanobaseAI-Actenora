package com.nanobaseai.actenora.security.meetingintelligence;

import com.nanobaseai.actenora.meetingintelligence.domain.model.NoteReviewStatus;
import com.nanobaseai.actenora.meetingintelligence.domain.validation.QualityGateOutcome;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test
    void manualReviewGateNeverSharesDraftByMail() {
        assertFalse(MeetingIntelligenceHandoffAdapter.mayShareDraftByMail(
                QualityGateOutcome.MANUAL_REVIEW_REQUIRED,
                NoteReviewStatus.MANUAL_REVIEW,
                true
        ));
    }

    @Test
    void activeNoteStillDoesNotShareWhenDraftCarriesReviewSignal() {
        assertFalse(MeetingIntelligenceHandoffAdapter.mayShareDraftByMail(
                QualityGateOutcome.PASSED_WITH_WARNINGS,
                NoteReviewStatus.ACTIVE,
                true
        ));
    }

    @Test
    void onlyPassedActiveNonReviewDraftMayBeShared() {
        assertTrue(MeetingIntelligenceHandoffAdapter.mayShareDraftByMail(
                QualityGateOutcome.PASSED,
                NoteReviewStatus.ACTIVE,
                false
        ));
    }
}
