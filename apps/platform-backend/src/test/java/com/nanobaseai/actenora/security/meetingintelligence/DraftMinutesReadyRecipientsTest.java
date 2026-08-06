package com.nanobaseai.actenora.security.meetingintelligence;

import com.nanobaseai.actenora.aiprocessing.domain.pipeline.SegmentInput;
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

    @Test
    void transcriptSpeakersProvidePositiveAttendanceWithoutGenericLabelsOrDuplicates() {
        var records = MeetingIntelligenceHandoffAdapter.transcriptAttendanceRecords(List.of(
                new SegmentInput("s1", 1, "Murat Sancar", 0, 1000, "Merhaba", false),
                new SegmentInput("s2", 2, " murat   sancar ", 1000, 2000, "Devam", false),
                new SegmentInput("s3", 3, "Speaker 1", 2000, 3000, "Ses", false),
                new SegmentInput("s4", 4, "Gökay Yılmaz", 3000, 4000, "Katılıyorum", false)
        ));

        assertEquals(List.of("Murat Sancar", "Gökay Yılmaz"),
                records.stream().map(r -> r.displayName()).toList());
        assertTrue(records.stream().allMatch(r -> r.email() == null && r.entraUserId() == null));
    }
}
