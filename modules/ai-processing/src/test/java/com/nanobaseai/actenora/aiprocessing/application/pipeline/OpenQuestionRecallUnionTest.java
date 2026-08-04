package com.nanobaseai.actenora.aiprocessing.application.pipeline;

import com.nanobaseai.actenora.aiprocessing.domain.pipeline.OpenQuestionCandidate;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenQuestionRecallUnionTest {

    @Test
    void unionKeepsFallbackQuestionsWhenSynthesisReturnsSubset() {
        List<OpenQuestionCandidate> synthesis = List.of(
                new OpenQuestionCandidate(
                        "Mobil istemcide aynı yarış koşulu bulunuyor mu?",
                        List.of("seg-1"),
                        0.9
                )
        );
        List<OpenQuestionCandidate> candidates = List.of(
                new OpenQuestionCandidate(
                        "Mobil istemcide aynı yarış koşulu bulunuyor mu?",
                        List.of("seg-1"),
                        0.8
                ),
                new OpenQuestionCandidate(
                        "Kesinti veya gecikmede ilk kullanıcı iletişimini kim yapacak?",
                        List.of("seg-2"),
                        0.85
                ),
                new OpenQuestionCandidate(
                        "Hata durumunda hangi config geri alınacak?",
                        List.of("seg-3"),
                        0.88
                )
        );

        List<OpenQuestionCandidate> united = MinutesSynthesisAndAudit.unionOpenQuestions(
                synthesis, candidates);

        assertEquals(3, united.size());
        assertTrue(united.stream().anyMatch(q -> q.text().contains("iletişimini kim")));
        assertTrue(united.stream().anyMatch(q -> q.text().contains("config geri")));
        // Prefer richer evidence / confidence for the shared question.
        OpenQuestionCandidate shared = united.stream()
                .filter(q -> q.text().contains("Mobil istemcide"))
                .findFirst()
                .orElseThrow();
        assertEquals(0.9, shared.confidence());
    }

    @Test
    void unionIsIdempotentOnNearDuplicateText() {
        List<OpenQuestionCandidate> a = List.of(
                new OpenQuestionCandidate("Gmail test matrisi ayrıca çalıştırılmalı mı?", List.of("s1"), 0.7)
        );
        List<OpenQuestionCandidate> b = List.of(
                new OpenQuestionCandidate("  gmail test matrisi ayrıca çalıştırılmalı mı? ", List.of("s1", "s2"), 0.6)
        );

        List<OpenQuestionCandidate> united = MinutesSynthesisAndAudit.unionOpenQuestions(a, b);
        assertEquals(1, united.size());
        assertEquals(2, united.getFirst().evidenceSegmentIds().size());
    }
}
