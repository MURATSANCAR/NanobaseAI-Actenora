package com.nanobaseai.actenora.aiprocessing.domain.pipeline.consistency;

import com.nanobaseai.actenora.aiprocessing.domain.pipeline.ActionItemCandidate;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.DecisionCandidate;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.FinalNoteDraft;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.ProposalCandidate;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.SegmentInput;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DecisionProposalSubsumerTest {

    private final DecisionProposalSubsumer subsumer = new DecisionProposalSubsumer();

    @Test
    void happyPathDropsPromiseAndUtf8ProposalsKeepsAcceptanceCriteria() {
        var decisions = List.of(
                new DecisionCandidate(
                        "Paralel refresh çağrıları frontend tarafında tek promise üzerinde birleştirilecek.",
                        List.of("d1"), 0.95),
                new DecisionCandidate(
                        "Yeni gönderimlerde UTF-8 başlığı zorunlu olacak.",
                        List.of("d2"), 0.95)
        );
        var proposals = List.of(
                new ProposalCandidate(
                        "Henüz karar değil; değerlendirdiğimiz seçenek şu: Frontend tarafında tek promise üzerinden refresh yapılması.",
                        List.of("p1"), 0.9),
                new ProposalCandidate(
                        "Bu başlığın kabul kriterini kullanıcı diliyle yazmayı öneriyorum: beklenen davranış, hata mesajı ve tamamlanma süresi aynı maddede görünmeli.",
                        List.of("p2"), 0.9),
                new ProposalCandidate(
                        "Henüz karar değil; değerlendirdiğimiz seçenek şu: UTF-8 ve quoted-printable başlıklarını servis katmanında sabitlemek.",
                        List.of("p3"), 0.9)
        );
        DecisionProposalSubsumer.Outcome out = subsumer.apply(decisions, proposals);
        assertEquals(1, out.proposals().size());
        assertTrue(out.proposals().getFirst().text().toLowerCase().contains("kabul kriter"));
        assertTrue(out.flags().contains(DecisionProposalSubsumer.DROPPED));
        assertEquals(0, out.unresolved());
    }

    @Test
    void negativePolarityDoesNotDropConflictingProposal() {
        var decisions = List.of(new DecisionCandidate(
                "Token yenileme yöntemi değiştirilmeyecek.", List.of("d1"), 0.95));
        var proposals = List.of(new ProposalCandidate(
                "Paralel refresh çağrıları tek promise üzerinde birleştirilmeli.", List.of("p1"), 0.9));
        DecisionProposalSubsumer.Outcome out = subsumer.apply(decisions, proposals);
        assertEquals(1, out.proposals().size());
        assertFalse(out.flags().contains(DecisionProposalSubsumer.DROPPED));
    }

    @Test
    void paraphraseWithLowTokenOverlapStillDrops() {
        var decisions = List.of(new DecisionCandidate(
                "Yenileme istekleri istemci tarafında ortak bir işlem üzerinden yönetilecek.",
                List.of("d1"), 0.92));
        var proposals = List.of(new ProposalCandidate(
                "Paralel refresh çağrıları tek promise altında toplanabilir.",
                List.of("p1"), 0.9));
        DecisionProposalSubsumer.Outcome out = subsumer.apply(decisions, proposals);
        assertEquals(0, out.proposals().size());
        assertTrue(out.flags().contains(DecisionProposalSubsumer.DROPPED));
    }

    @Test
    void scopeMismatchKeepsMobileProposal() {
        var decisions = List.of(new DecisionCandidate(
                "Web istemcisindeki paralel refresh çağrıları tek promise üzerinde birleştirilecek.",
                List.of("d1"), 0.95));
        var proposals = List.of(new ProposalCandidate(
                "Mobil istemcide de aynı refresh yöntemi değerlendirilmeli.",
                List.of("p1"), 0.9));
        DecisionProposalSubsumer.Outcome out = subsumer.apply(decisions, proposals);
        assertEquals(1, out.proposals().size());
    }

    @Test
    void speechActKeepExplicitNonChangeDecision() {
        // Smoke: decision text with "değiştirmeme" is still a decision candidate input;
        // subsumer must not invent dropping it — only proposals are filtered.
        var decisions = List.of(new DecisionCandidate(
                "Ekip, mevcut token yöntemini değiştirmemeye karar verdi.", List.of("d1"), 0.95));
        DecisionProposalSubsumer.Outcome out = subsumer.apply(decisions, List.of());
        assertEquals(0, out.dropped());
    }

    @Test
    void successfulDropDoesNotForceManualReviewOnDraft() {
        FinalNoteDraft draft = new FinalNoteDraft(
                "Özet",
                List.of(new DecisionCandidate(
                        "Paralel refresh çağrıları frontend tarafında tek promise üzerinde birleştirilecek.",
                        List.of("d1"), 0.95)),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(new ProposalCandidate(
                        "Henüz karar değil; değerlendirdiğimiz seçenek şu: Frontend tarafında tek promise üzerinden refresh yapılması.",
                        List.of("p1"), 0.9)),
                List.of(),
                List.of(),
                List.of("d1", "p1"),
                0.9,
                false
        );
        FinalNoteDraft audited = new CrossTypeConsistencyAuditor().audit(draft);
        assertTrue(audited.qualityFlags().stream().anyMatch(f -> f.contains(DecisionProposalSubsumer.DROPPED)));
        assertTrue(audited.proposals().isEmpty());
        assertFalse(audited.requiresManualReview());
        assertFalse(audited.qualityFlags().stream().anyMatch(f -> f.equalsIgnoreCase("REQUIRES_MANUAL_REVIEW")));
    }

    @Test
    void enrichUsesSegmentContextNotDecision() {
        FinalNoteDraft draft = new FinalNoteDraft(
                "Özet",
                List.of(),
                List.of(new ActionItemCandidate(
                        "Selin düzeltmeyi yapacak.", "Selin", null, List.of("s1"), 0.9)),
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
        List<SegmentInput> segs = List.of(
                new SegmentInput("s1", 1, "Selin", 0, 1000,
                        "Selin frontend'deki paralel refresh işini ben düzelteceğim.", false)
        );
        FinalNoteDraft enriched = new ActionContextualEnricher().enrich(draft, segs);
        assertTrue(enriched.actionItems().getFirst().text().toLowerCase().contains("refresh")
                || enriched.actionItems().getFirst().text().toLowerCase().contains("frontend"));
        assertFalse(enriched.actionItems().getFirst().text().equals("Selin düzeltmeyi yapacak."));
    }

    @Test
    void enrichRefusesVagueBakirim() {
        FinalNoteDraft draft = new FinalNoteDraft(
                "Özet",
                List.of(),
                List.of(new ActionItemCandidate(
                        "Selin düzeltmeyi yapacak.", "Selin", null, List.of("s1"), 0.9)),
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
        List<SegmentInput> segs = List.of(
                new SegmentInput("s1", 1, "Selin", 0, 1000, "Selin ben bakarım.", false)
        );
        FinalNoteDraft enriched = new ActionContextualEnricher().enrich(draft, segs);
        assertEquals("Selin düzeltmeyi yapacak.", enriched.actionItems().getFirst().text());
    }

    @Test
    void relativeDatePreservedOnAction() {
        ActionItemCandidate a = new ActionItemCandidate(
                "Selin frontend düzeltmesini tamamlayacak.",
                "Selin",
                null,
                List.of("s1"),
                0.9,
                null,
                null,
                "cuma gününe kadar"
        );
        assertEquals("cuma gününe kadar", a.relativeDate());
    }

    @Test
    void comparisonCoreStripsScaffoldKeepsOriginalSignalElsewhere() {
        String original = "Henüz karar değil; değerlendirdiğimiz seçenek şu: Frontend tarafında tek promise üzerinden refresh yapılması.";
        String core = ItemTextViews.comparisonCore(original);
        assertFalse(core.contains("henüz karar değil"));
        assertTrue(core.contains("promise") || core.contains("frontend"));
        assertTrue(original.toLowerCase().contains("henüz karar değil"));
    }
}
