package com.nanobaseai.actenora.aiprocessing.domain.pipeline.signal;

import com.nanobaseai.actenora.aiprocessing.domain.pipeline.DecisionCandidate;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.ExtractionBundle;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.SegmentInput;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.TranscriptChunk;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChunkSignalGateScenariosTest {

    private SignalGateConfig hardSkipConfig;
    private ChunkSignalGate gate;
    private ChunkExtractionService extraction;

    @BeforeEach
    void setUp() {
        hardSkipConfig = SignalGateConfig.productionDefaults().withShadowMode(false);
        gate = new ChunkSignalGate(hardSkipConfig);
        extraction = ChunkExtractionService.create(hardSkipConfig);
    }

    @Test
    void a_outOfVocabularyDecisionStillExtracts() {
        TranscriptChunk chunk = chunk(120, seg("s1",
                "Bu noktayı artık kapatıyoruz; API yapısı cuma itibarıyla değişmeyecek."));
        ChunkGateDecision d = gate.evaluate(chunk, ChunkContext.of(hardSkipConfig));
        assertTrue(d.shouldExtract(), () -> "outcome=" + d.outcome() + " reasons=" + d.reasons());
    }

    @Test
    void b_assignmentStructureExtracts() {
        TranscriptChunk chunk = chunk(80, seg("s1",
                "Can bunun sahipliğini alsın ve cuma günü paketi kanala bıraksın."));
        ChunkGateDecision d = gate.evaluate(chunk, ChunkContext.of(hardSkipConfig));
        assertTrue(d.shouldExtract(), () -> "outcome=" + d.outcome() + " reasons=" + d.reasons());
    }

    @Test
    void c_decisionWordInUiNoiseSkips() {
        TranscriptChunk chunk = chunk(90,
                seg("s1", "Karar ekranını şimdi açıyorum."),
                seg("s2", "Mevcut kararları tekrar okuyacağım."),
                seg("s3", "Yeni bir karar alınmadı."));
        ChunkGateDecision d = gate.evaluate(chunk, ChunkContext.of(hardSkipConfig));
        assertEquals(GateOutcome.SKIP_LOW_SIGNAL, d.outcome(), () -> d.reasons().toString());
    }

    @Test
    void d_proposalAndDecisionSameChunkExtracts() {
        TranscriptChunk chunk = chunk(100,
                seg("s1", "Belki erteleyebiliriz."),
                seg("s2", "Henüz karar değil."),
                seg("s3", "Bu konuyu kapattık; cuma donduruyoruz."));
        ChunkGateDecision d = gate.evaluate(chunk, ChunkContext.of(hardSkipConfig));
        assertTrue(d.shouldExtract(), () -> d.reasons().toString());
    }

    @Test
    void e_longChunkSingleCriticalDecisionExtracts() {
        StringBuilder filler = new StringBuilder();
        for (int i = 0; i < 40; i++) {
            filler.append("Genel durum güncellemesi devam ediyor, ek bir madde yok. ");
        }
        filler.append("Bu noktayı kapatıyoruz; API yapısı cuma itibarıyla değişmeyecek.");
        TranscriptChunk chunk = chunk(2000, seg("s1", filler.toString()));
        ChunkGateDecision d = gate.evaluate(chunk, ChunkContext.of(hardSkipConfig));
        assertTrue(d.shouldExtract(), () -> "score=" + d.score() + " " + d.reasons());
    }

    @Test
    void f_mitigationContinuation() {
        ChunkSignalSummary prev = new ChunkSignalSummary(true, false, false, false, false);
        TranscriptChunk chunk = chunk(40, seg("s1",
                "Bunun önlemi olarak erken smoke uygulayacağız."));
        ChunkGateDecision d = gate.evaluate(chunk, ChunkContext.withPrevious(hardSkipConfig, prev));
        assertEquals(GateOutcome.EXTRACT_CONTINUATION, d.outcome(), () -> d.reasons().toString());
    }

    @Test
    void g_lowSignalSkipsInferEntirely() {
        AtomicInteger inferCalls = new AtomicInteger();
        TranscriptChunk chunk = chunk(90,
                seg("s1", "Karar ekranını şimdi açıyorum."),
                seg("s2", "Yeni bir karar alınmadı."));
        ChunkExtractionResult result = extraction.extract(
                chunk,
                ChunkContext.of(hardSkipConfig),
                c -> {
                    inferCalls.incrementAndGet();
                    return ExtractionBundle.empty();
                }
        );
        assertEquals(0, inferCalls.get());
        assertTrue(result.skippedWithoutInfer());
        assertTrue(result.bundle().qualityFlags().contains("SKIPPED_LOW_SIGNAL"));
    }

    @Test
    void h_groundingDropsUnsupportedKeepsGrounded() {
        EvidenceBundleGroundingPolicy policy = new EvidenceBundleGroundingPolicy();
        EvidenceIndex index = EvidenceIndex.from(List.of(
                seg("s1", "Karar ekranını açıyorum."),
                seg("s2", "API sözleşmesini cuma donduruyoruz.")
        ));
        ExtractionBundle raw = new ExtractionBundle(
                List.of(),
                List.of(
                        new DecisionCandidate("Yeni karar alınmadı", List.of("s1"), 0.9),
                        new DecisionCandidate("API cuma dondurulacak", List.of("s2"), 0.95)
                ),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of("s1", "s2"), 0.9
        );
        ExtractionBundle grounded = policy.retainGroundedItems(raw, index);
        assertEquals(1, grounded.decisions().size());
        assertEquals("API cuma dondurulacak", grounded.decisions().getFirst().text());
        assertTrue(grounded.qualityFlags().contains("UNSUPPORTED_DECISION"));
    }

    private static TranscriptChunk chunk(int tokens, SegmentInput... segments) {
        return new TranscriptChunk(0, List.of(segments), tokens);
    }

    private static SegmentInput seg(String id, String content) {
        return new SegmentInput(id, 0, "Emre", 0, 1000, content, false);
    }
}
