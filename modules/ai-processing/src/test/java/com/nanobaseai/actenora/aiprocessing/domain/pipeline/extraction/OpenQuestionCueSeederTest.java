package com.nanobaseai.actenora.aiprocessing.domain.pipeline.extraction;

import com.nanobaseai.actenora.aiprocessing.domain.pipeline.ExtractionBundle;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.SegmentInput;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenQuestionCueSeederTest {

    private final OpenQuestionCueSeeder seeder = new OpenQuestionCueSeeder();

    @Test
    void seedsExplicitOpenQuestionsFromStandupCues() {
        List<SegmentInput> segments = List.of(
                new SegmentInput("s1", 1, "Burak", 0, 1000,
                        "Açık kalan soru şu: Mobil istemcide aynı yarış koşulu var mı?", true),
                new SegmentInput("s2", 2, "Burak", 0, 1000,
                        "Açık kalan soru şu: Gmail test matrisi ayrıca koşulmalı mı?", true),
                new SegmentInput("s3", 3, "Ece", 0, 1000,
                        "Müşteri operasyonu açısından kesinti veya gecikme olduğunda ilk mesajı kim gönderecek?", true),
                new SegmentInput("s4", 4, "Derya", 0, 1000,
                        "Değişiklik açılırsa ilk otuz dakikada hangi alarmı ve dashboard'u izleyeceğiz?", true)
        );
        ExtractionBundle seeded = seeder.seed(ExtractionBundle.empty(), segments);
        assertTrue(seeded.openQuestions().size() >= 3, "got " + seeded.openQuestions().size());
        assertTrue(seeded.qualityFlags().contains("OPEN_QUESTION_CUE_SEEDED"));
        String all = seeded.openQuestions().stream().map(q -> q.text().toLowerCase()).reduce("", (a, b) -> a + " " + b);
        assertTrue(all.contains("mobil") || all.contains("yarış"));
        assertTrue(all.contains("gmail") || all.contains("matrisi"));
        assertTrue(all.contains("kim") || all.contains("gönder"));
    }
}
