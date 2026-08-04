package com.nanobaseai.actenora.aiprocessing.application.pipeline;

import com.nanobaseai.actenora.aiprocessing.domain.pipeline.ImportantFactCandidate;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImportantFactRecallUnionTest {

    @Test
    void unionKeepsMeasuredFactsWhenSynthesisOmitsThem() {
        List<ImportantFactCandidate> synthesis = List.of();
        List<ImportantFactCandidate> candidates = List.of(
                new ImportantFactCandidate(
                        "Oturum yenileme testi yapılan 40 tekrarın 3'ünde 401 görüldü.",
                        List.of("seg-1"),
                        0.9
                ),
                new ImportantFactCandidate(
                        "Test edilen 5 istemcinin 2'sinde e-posta konu satırı hatalı görüldü.",
                        List.of("seg-2"),
                        0.88
                )
        );

        List<ImportantFactCandidate> united =
                MinutesSynthesisAndAudit.unionImportantFacts(synthesis, candidates);

        assertEquals(2, united.size());
        assertTrue(united.stream().anyMatch(f -> f.text().contains("401")));
        assertTrue(united.stream().anyMatch(f -> f.text().contains("istemcinin")));
    }

    @Test
    void unionDedupsNearDuplicateFacts() {
        List<ImportantFactCandidate> a = List.of(
                new ImportantFactCandidate(
                        "Oturum yenileme testi yapılan 40 tekrarın 3'ünde 401 görüldü.",
                        List.of("seg-1"),
                        0.7
                )
        );
        List<ImportantFactCandidate> b = List.of(
                new ImportantFactCandidate(
                        "  oturum yenileme testi yapılan 40 tekrarın 3'ünde 401 görüldü. ",
                        List.of("seg-1", "seg-2"),
                        0.6
                )
        );

        List<ImportantFactCandidate> united =
                MinutesSynthesisAndAudit.unionImportantFacts(a, b);
        assertEquals(1, united.size());
        assertEquals(2, united.getFirst().evidenceSegmentIds().size());
    }
}
