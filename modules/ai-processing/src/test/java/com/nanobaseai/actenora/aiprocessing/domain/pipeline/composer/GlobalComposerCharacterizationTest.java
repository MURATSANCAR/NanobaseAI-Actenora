package com.nanobaseai.actenora.aiprocessing.domain.pipeline.composer;

import com.nanobaseai.actenora.aiprocessing.domain.pipeline.DecisionCandidate;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.ExtractionBundle;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.FinalNoteDraft;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.SegmentInput;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.TokenEstimator;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Characterization fixtures for COMPOSER grounded-union path (BIM failure modes).
 */
class GlobalComposerCharacterizationTest {

    @Test
    void eylulFutureCommitmentSurvivesUnionEvenWhenLedgerEmpty() {
        List<SegmentInput> segments = List.of(
                seg("s417", 417, "Mehmet Doğanyiğit",
                        "Biz sizinle eylülden sonra tekrar konuşalım; o zaman bir güncelleme toplantısı yaparız.")
        );
        TranscriptDigest digest = new TranscriptDigestBuilder().build(segments);
        assertTrue(digest.candidateFacts().stream()
                .anyMatch(f -> TranscriptDigestBuilder.KIND_FUTURE_COMMITMENT.equals(f.kind())));

        GlobalComposition seeded = new GlobalMinutesComposer(
                unusedRuntime(), 1).seedFromDigest(digest);
        GlobalCompositionAuditor.VerifiedComposition verified =
                new GlobalCompositionAuditor().verify(
                        seeded, segments, Set.of("Mehmet Doğanyiğit", "Murat Sancar"), Set.of("s417"));

        ExtractionBundle ledger = ExtractionBundle.empty();
        ExtractionBundle unioned = new GlobalLedgerMerger().unionAndDedupe(ledger, verified.acceptedItems());

        assertTrue(
                unioned.actionItems().stream().anyMatch(a -> a.text().toLowerCase().contains("eylül")),
                "Eylül action must enter via grounded UNION, not ledger intersection");
    }

    @Test
    void simpleHistoryWithoutSelectionConfirmationDoesNotBecomeDecision() {
        List<SegmentInput> segments = List.of(
                seg("s1", 1, "Mehmet",
                        "Daha önce başlangıç toplantısında başka bir çözüm değerlendirilmişti.")
        );
        GlobalComposition composition = new GlobalComposition(
                null,
                List.of(new GlobalComposition.GlobalCandidate(
                        GlobalComposition.CandidateType.DECISION,
                        "Başka çözüm seçildi",
                        null, null, null,
                        List.of("s1"),
                        "DIGEST",
                        0.9))
        );
        GlobalCompositionAuditor.VerifiedComposition verified =
                new GlobalCompositionAuditor().verify(composition, segments, Set.of("Mehmet"), Set.of("s1"));
        assertTrue(verified.acceptedItems().isEmpty());
    }

    @Test
    void simpleSelectionConfirmationIsKeptAsDecision() {
        List<SegmentInput> segments = List.of(
                seg("s1", 1, "Mehmet",
                        "Aşağı yukarı 17 aylık bir süreç yaşadık. Orada Simple devam edeceğiz inşallah.")
        );
        GlobalComposition composition = new GlobalComposition(
                null,
                List.of(new GlobalComposition.GlobalCandidate(
                        GlobalComposition.CandidateType.DECISION,
                        "Simple çözümü seçildi",
                        null, null, null,
                        List.of("s1"),
                        "DIGEST",
                        0.95))
        );
        GlobalCompositionAuditor.VerifiedComposition verified =
                new GlobalCompositionAuditor().verify(composition, segments, Set.of("Mehmet"), Set.of("s1"));
        assertTrue(verified.acceptedItems().stream()
                .anyMatch(c -> c.type() == GlobalComposition.CandidateType.DECISION
                        && c.text().toLowerCase().contains("simple")));
    }

    @Test
    void mucahitSpeculationNeverSeededFromDigestQuestionsWithoutSupport() {
        List<SegmentInput> segments = List.of(
                seg("s9", 9, "Murat", "Maliyet bandı nedir?")
        );
        TranscriptDigest digest = new TranscriptDigestBuilder().build(segments);
        GlobalComposition seeded = new GlobalMinutesComposer(unusedRuntime(), 1).seedFromDigest(digest);
        assertFalse(seeded.candidates().stream()
                .anyMatch(c -> c.text().toLowerCase().contains("mücahit")
                        || c.text().toLowerCase().contains("abdurrahman")));
    }

    @Test
    void rendererDoesNotKeepDroppedOwnerClaimWhenLedgerHasNoOwner() {
        FinalNoteDraft ledger = new FinalNoteDraft(
                "",
                List.of(new DecisionCandidate("Simple seçildi", List.of("s1"), 0.9)),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of("s1"),
                0.9,
                false
        );
        FinalNoteDraft rendered = new VerifiedMinutesRenderer().renderDeterministic(
                ledger,
                new GlobalComposition.MeetingFrame(
                        "INTRODUCTION_AND_EXPLORATION",
                        "Tanışma ve vizyon paylaşımı.",
                        List.of("s1"),
                        0.9)
        );
        assertFalse(rendered.executiveSummary().toLowerCase().contains("murat takip"));
        assertTrue(rendered.executiveSummary().toLowerCase().contains("simple")
                || rendered.executiveSummary().toLowerCase().contains("tanışma"));
    }

    @Test
    void gokayIleKonusurumOwnerIsSpeakerNotGokay() {
        List<SegmentInput> segments = List.of(
                seg("s10", 10, "Murat Sancar", "Ben Gökay ile bilgi güvenliği konusunda konuşurum.")
        );
        GlobalComposition.GlobalCandidate candidate = new GlobalComposition.GlobalCandidate(
                GlobalComposition.CandidateType.ACTION,
                "Gökay ile bilgi güvenliği görüşmesi",
                "Gökay",
                null,
                null,
                List.of("s10"),
                "DIGEST",
                0.9
        );
        String owner = GlobalCompositionAuditor.resolveOwner(
                candidate,
                segments.getFirst().content(),
                Set.of("Murat Sancar", "Gökay", "Ali BAĞATIR"),
                segments
        );
        assertEquals("Murat Sancar", owner);
    }

    @Test
    void firatGonderirOwnerIsFirat() {
        List<SegmentInput> segments = List.of(
                seg("s11", 11, "Ali BAĞATIR", "Fırat bunu gönderir.")
        );
        GlobalComposition.GlobalCandidate candidate = new GlobalComposition.GlobalCandidate(
                GlobalComposition.CandidateType.ACTION,
                "Doküman gönderimi",
                null,
                null,
                null,
                List.of("s11"),
                "DIGEST",
                0.9
        );
        String owner = GlobalCompositionAuditor.resolveOwner(
                candidate,
                segments.getFirst().content(),
                Set.of("Ali BAĞATIR", "Fırat"),
                segments
        );
        assertEquals("Fırat", owner);
    }

    @Test
    void inconsistentPolishedProseIsRebuiltFromLedger() {
        FinalNoteDraft ledger = new FinalNoteDraft(
                "Murat takip edecek ve Eylül toplantısını organize edecek.",
                List.of(new DecisionCandidate("Simple seçildi", List.of("s1"), 0.9)),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of("s1"),
                0.9,
                false
        );
        FinalNoteDraft fixed = new VerifiedMinutesRenderer().assertProseConsistent(
                ledger,
                new GlobalComposition.MeetingFrame(
                        "INTRODUCTION_AND_EXPLORATION",
                        "Tanışma ve vizyon.",
                        List.of("s1"),
                        0.9)
        );
        assertFalse(fixed.executiveSummary().toLowerCase().contains("murat takip"));
        assertTrue(fixed.qualityFlags().contains(VerifiedMinutesRenderer.FLAG_UNSUPPORTED_FINAL_CLAIM)
                || fixed.qualityFlags().contains(VerifiedMinutesRenderer.FLAG_PROSE_REBUILT_FROM_LEDGER));
        assertTrue(fixed.executiveSummary().toLowerCase().contains("simple"));
    }

    @Test
    void multiWindowDigestPreservesLateEylulEvidenceIds() {
        List<SegmentInput> segments = new ArrayList<>();
        for (int i = 1; i <= 80; i++) {
            segments.add(seg("s" + i, i, "Speaker", "Genel tanışma ve vizyon konuşması devam ediyor."));
        }
        segments.add(seg("s417", 417, "Mehmet",
                "Biz sizinle eylülden sonra tekrar konuşalım; güncelleme toplantısı yaparız."));
        TranscriptDigest digest = new TranscriptDigestBuilder(TokenEstimator.approximate(), 200).build(segments);
        assertTrue(digest.candidateFacts().stream().anyMatch(f ->
                TranscriptDigestBuilder.KIND_FUTURE_COMMITMENT.equals(f.kind())
                        && f.evidenceSegmentIds().contains("s417")));
    }

    private static SegmentInput seg(String id, int seq, String speaker, String text) {
        return new SegmentInput(id, seq, speaker, seq * 1000L, seq * 1000L + 500L, text, false);
    }

    private static com.nanobaseai.actenora.aiprocessing.application.pipeline.ModelRuntimePort unusedRuntime() {
        return new com.nanobaseai.actenora.aiprocessing.application.pipeline.ModelRuntimePort() {
            @Override
            public com.nanobaseai.actenora.aiprocessing.application.pipeline.ModelDescriptor descriptor() {
                return new com.nanobaseai.actenora.aiprocessing.application.pipeline.ModelDescriptor(
                        "test", "t", "t@1", 16_384, 2_048);
            }

            @Override
            public com.nanobaseai.actenora.aiprocessing.application.pipeline.InferenceResponse infer(
                    com.nanobaseai.actenora.aiprocessing.application.pipeline.InferenceRequest request
            ) {
                throw new UnsupportedOperationException("seed path only");
            }

            @Override
            public boolean healthy() {
                return true;
            }
        };
    }
}
