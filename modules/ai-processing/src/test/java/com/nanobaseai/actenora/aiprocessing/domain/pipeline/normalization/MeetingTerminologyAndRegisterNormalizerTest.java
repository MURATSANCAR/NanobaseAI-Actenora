package com.nanobaseai.actenora.aiprocessing.domain.pipeline.normalization;

import com.nanobaseai.actenora.aiprocessing.domain.pipeline.ActionItemCandidate;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.SegmentInput;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.SegmentNormalizer;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.action.ActionPostProcessingPipeline;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MeetingTerminologyAndRegisterNormalizerTest {

    @Test
    void segmentNormalizerRewritesMayusqueToMySql() {
        SegmentNormalizer normalizer = new SegmentNormalizer();
        List<SegmentInput> out = normalizer.normalize(List.of(
                new SegmentInput(
                        "s1",
                        0,
                        "Murat",
                        0,
                        1000,
                        "Mayusque ve Poscree karşılaştırması yapacağız.",
                        true
                )
        ));
        assertEquals(1, out.size());
        SegmentInput rewritten = out.stream()
                .filter(s -> "s1".equals(s.segmentId()))
                .findFirst()
                .orElseThrow();
        assertTrue(rewritten.content().contains("MySQL"));
        assertTrue(rewritten.content().contains("PostgreSQL"));
        assertFalse(rewritten.content().contains("Mayusque"));
    }

    @Test
    void segmentNormalizerAppliesItFinanceLexiconBeforeExtraction() {
        SegmentNormalizer normalizer = new SegmentNormalizer();
        List<SegmentInput> out = normalizer.normalize(List.of(
                new SegmentInput(
                        "s-domain", 0, "Murat", 0, 1000,
                        "Kor banking PoC için n vidia g p u, d w h ve si em logları değerlendirilecek.",
                        true)
        ));

        assertEquals(
                "Core Banking PoC için NVIDIA GPU, DWH ve SIEM logları değerlendirilecek.",
                out.getFirst().content());
    }

    @Test
    void domainRegisterRewritesRecete() {
        DomainRegisterNormalizer register = new DomainRegisterNormalizer();
        assertEquals(
                "Önce gereksinim dokümanı yazılacak.",
                register.rewrite("Önce reçete yazılacak.")
        );
        assertEquals(
                "gereksinim dokümanlarını paylaş",
                register.rewrite("reçetelerini paylaş")
        );
    }

    @Test
    void actionPipelineRewritesReceteInActionText() {
        ActionItemCandidate in = new ActionItemCandidate(
                "Ürün reçetesini tamamla ve paylaş.",
                "Murat Sancar",
                null,
                List.of("s1"),
                0.9
        );
        var result = ActionPostProcessingPipeline.productionDefaults().postProcess(
                List.of(in),
                List.of(),
                new ActionPostProcessingPipeline.Context(
                        List.of(),
                        Set.of("Murat Sancar"),
                        OffsetDateTime.parse("2026-07-29T08:11:26+03:00"),
                        ZoneId.of("Europe/Istanbul"),
                        "m"
                )
        );
        ActionItemCandidate rewritten = result.actions().stream()
                .filter(a -> a.evidenceSegmentIds().contains("s1"))
                .findFirst()
                .orElseThrow();
        assertTrue(rewritten.text().contains("gereksinim dokümanı"));
        assertFalse(rewritten.text().toLowerCase().contains("reçete"));
    }
}
