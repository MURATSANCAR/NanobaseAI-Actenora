package com.nanobaseai.actenora.aiprocessing.domain.pipeline.extraction;

import com.nanobaseai.actenora.aiprocessing.domain.pipeline.ExtractionBundle;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.SegmentInput;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MeasuredObservationFactSeederTest {

    private final MeasuredObservationFactSeeder seeder = new MeasuredObservationFactSeeder();

    @Test
    void seedsStandupGoldMeasuredObservationsFromTurkishNumberWords() {
        List<SegmentInput> segments = List.of(
                new SegmentInput("seg-28", 28, "Burak", 0, 1000,
                        "Elimizdeki doğrulanmış veri şu: Kırk tekrardan üçünde 401 görüldü.", true),
                new SegmentInput("seg-124", 124, "Burak", 0, 1000,
                        "Elimizdeki doğrulanmış veri şu: Beş istemcinin ikisinde konu satırı hatalı.", true)
        );

        ExtractionBundle seeded = seeder.seed(ExtractionBundle.empty(), segments);

        assertEquals(2, seeded.importantFacts().size());
        assertTrue(seeded.qualityFlags().contains("MEASURED_FACT_SEEDED"));
        assertTrue(seeded.importantFacts().stream()
                .anyMatch(f -> f.text().toLowerCase().contains("401")
                        && f.text().toLowerCase().contains("tekrar")));
        assertTrue(seeded.importantFacts().stream()
                .anyMatch(f -> f.text().toLowerCase().contains("istemci")
                        && (f.text().toLowerCase().contains("hatal")
                        || f.text().toLowerCase().contains("konu"))));
    }

    @Test
    void doesNotDuplicateExistingFacts() {
        ExtractionBundle existing = new ExtractionBundle(
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(new com.nanobaseai.actenora.aiprocessing.domain.pipeline.ImportantFactCandidate(
                        "Oturum yenileme testi yapılan 40 tekrarın 3'ünde 401 görüldü.",
                        List.of("seg-x"),
                        0.9
                )),
                List.of(), List.of(), 0.9
        );
        List<SegmentInput> segments = List.of(
                new SegmentInput("seg-28", 28, "Burak", 0, 1000,
                        "Kırk tekrardan üçünde 401 görüldü.", true)
        );
        ExtractionBundle seeded = seeder.seed(existing, segments);
        assertEquals(1, seeded.importantFacts().size());
    }
}
