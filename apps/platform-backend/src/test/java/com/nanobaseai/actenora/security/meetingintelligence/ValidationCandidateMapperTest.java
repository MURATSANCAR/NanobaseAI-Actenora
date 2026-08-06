package com.nanobaseai.actenora.security.meetingintelligence;

import com.nanobaseai.actenora.aiprocessing.domain.pipeline.DecisionCandidate;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.ActionItemCandidate;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.FinalNoteDraft;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.SegmentInput;
import com.nanobaseai.actenora.meetingintelligence.domain.validation.CandidateKind;
import com.nanobaseai.actenora.meetingintelligence.domain.validation.ValidationCandidate;
import com.nanobaseai.actenora.meetingintelligence.domain.validation.ValidationParticipant;
import com.nanobaseai.actenora.meetingintelligence.domain.validation.ValidationSegment;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ValidationCandidateMapperTest {

    @Test
    void mapsSegmentsCandidatesAndStableSpeakerParticipants() {
        List<SegmentInput> segments = List.of(
                new SegmentInput("seg-1", 0, "Alice", 0, 1000, "Ship Friday.", true),
                new SegmentInput("seg-2", 1, "alice", 1000, 2000, "Confirmed.", false)
        );

        List<ValidationSegment> mappedSegments = ValidationCandidateMapper.toSegments(segments);
        assertEquals(2, mappedSegments.size());
        assertEquals(ValidationCandidateMapper.toSegmentUuid("seg-1"), mappedSegments.getFirst().segmentId());

        List<ValidationParticipant> participants = ValidationCandidateMapper.participantsFromSpeakers(segments);
        assertEquals(1, participants.size());
        assertEquals("Alice", participants.getFirst().displayName());

        List<ValidationParticipant> merged = ValidationCandidateMapper.mergeParticipants(
                participants,
                List.of(ValidationCandidateMapper.fromInvitee("Murat Sancar", "murat@example.com", "oid-1"))
        );
        assertEquals(2, merged.size());
        assertTrue(merged.stream().anyMatch(p -> "Murat Sancar".equals(p.displayName())));

        FinalNoteDraft draft = new FinalNoteDraft(
                "Summary",
                List.of(new DecisionCandidate("Ship Friday", List.of("seg-1"), 0.9)),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of("seg-1"),
                0.9,
                false
        );
        List<ValidationCandidate> candidates = ValidationCandidateMapper.toCandidates(
                draft,
                "2026-07-29T08:11:26+03:00",
                "Europe/Istanbul"
        );
        assertEquals(1, candidates.size());
        assertEquals(CandidateKind.DECISION, candidates.getFirst().kind());
        assertEquals(List.of(ValidationCandidateMapper.toSegmentUuid("seg-1")), candidates.getFirst().evidenceSegmentIds());
        assertTrue(candidates.getFirst().markedAsDecision());
    }

    @Test
    void mergesTitledSpeakerAndEmailDisplayNameIntoOneIdentity() {
        List<ValidationParticipant> speakers = ValidationCandidateMapper.participantsFromSpeakers(List.of(
                new SegmentInput(
                        "seg-ali", 0, "Ali  BAĞATIR (MÜŞTERİ ÇÖZÜMLERİ GMY)",
                        0, 1000, "PoC planını paylaşacağım.", false)
        ));
        ValidationParticipant attendee = ValidationCandidateMapper.fromInvitee(
                "ali.bagatir@example.com", "ali.bagatir@example.com", "oid-ali");

        List<ValidationParticipant> merged = ValidationCandidateMapper.mergeParticipants(
                speakers, List.of(attendee));

        assertEquals(1, merged.size());
        assertEquals("Ali BAĞATIR", merged.getFirst().displayName());
        assertEquals("ali.bagatir@example.com", merged.getFirst().email());
        assertEquals("oid-ali", merged.getFirst().entraUserId());
    }

    @Test
    void mergesUniqueAsrSpeakerDriftIntoFullParticipantIdentity() {
        List<ValidationParticipant> speakers = ValidationCandidateMapper.participantsFromSpeakers(List.of(
                new SegmentInput("seg-burag", 0, "BURAG", 0, 1000, "Planı paylaşacağım.", false)
        ));

        List<ValidationParticipant> merged = ValidationCandidateMapper.mergeParticipants(
                speakers,
                List.of(ValidationCandidateMapper.fromInvitee(
                        "Burak Ayık Kesisoğlu", "burak@example.com", "oid-burak"))
        );

        assertEquals(1, merged.size());
        assertEquals("Burak Ayık Kesisoğlu", merged.getFirst().displayName());
    }

    @Test
    void mapsRelativeDateFromActionItem() {
        FinalNoteDraft draft = new FinalNoteDraft(
                "Summary",
                List.of(),
                List.of(new ActionItemCandidate(
                        "Call customer",
                        "Ada",
                        "2026-07-29",
                        List.of("seg-1"),
                        0.9,
                        null,
                        null,
                        "bugün 16.00'ya kadar",
                        "2026-07-29T16:00:00+03:00"
                )),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of("seg-1"),
                0.9,
                false
        );

        List<ValidationCandidate> candidates = ValidationCandidateMapper.toCandidates(
                draft,
                "2026-07-29T08:11:26+03:00",
                "Europe/Istanbul"
        );
        assertEquals(1, candidates.size());

        ValidationCandidate action = candidates.getFirst();
        assertEquals(CandidateKind.ACTION_ITEM, action.kind());
        assertEquals("bugün 16.00'ya kadar", action.relativeDateText().orElseThrow());
        assertEquals("2026-07-29T16:00:00+03:00", action.dueAtText().orElseThrow());
        assertEquals("RESOLVED", action.dateResolutionStatus().orElseThrow());
        assertEquals("RELATIVE_DATE_EVIDENCE", action.dateResolutionSource().orElseThrow());
        assertEquals("2026-07-29T08:11:26+03:00",
                action.dateResolutionReferenceTime().orElseThrow());
        assertEquals("Europe/Istanbul", action.dateResolutionTimezone().orElseThrow());
    }
}
