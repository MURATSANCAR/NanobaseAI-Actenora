package com.nanobaseai.actenora.aiprocessing.domain.pipeline.consistency;

import com.nanobaseai.actenora.aiprocessing.domain.pipeline.ActionItemCandidate;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.DecisionCandidate;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.OpenQuestionCandidate;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * BIM / production acceptance cues (generic, not meeting-hardcoded):
 * - speculative third-party role/influence questions drop
 * - questions already answered by high-confidence decisions drop
 * - genuine unresolved cost/scope questions remain
 */
class OpenQuestionHygieneFilterTest {

    private final OpenQuestionHygieneFilter filter = new OpenQuestionHygieneFilter();

    @Test
    void dropsSpeculativeThirdPartyRoleQuestions() {
        List<String> flags = new ArrayList<>();
        List<OpenQuestionCandidate> kept = filter.filter(
                List.of(new OpenQuestionCandidate(
                        "Simple seçiminde Mücahit ve Abdurrahman'ın rolü veya tercihindeki etkisi nedir?",
                        List.of("s1"),
                        0.85)),
                List.of(),
                List.of(),
                List.of(),
                flags
        );
        assertTrue(kept.isEmpty());
        assertTrue(flags.contains(OpenQuestionHygieneFilter.OPEN_QUESTION_HYGIENE_DROPPED));
    }

    @Test
    void dropsQuestionsAnsweredByHighConfidenceDecision() {
        List<OpenQuestionCandidate> kept = filter.filter(
                List.of(new OpenQuestionCandidate(
                        "Core banking için hangi çözümle devam edilecek?",
                        List.of("s1"),
                        0.9)),
                List.of(new DecisionCandidate(
                        "Core banking için Simple çözümü ile devam edilecek.",
                        List.of("s1"),
                        0.95)),
                List.of(),
                List.of(),
                new ArrayList<>()
        );
        assertTrue(kept.isEmpty());
    }

    @Test
    void keepsGenuineUnresolvedCostQuestions() {
        List<OpenQuestionCandidate> kept = filter.filter(
                List.of(new OpenQuestionCandidate(
                        "Yapay zeka çözümlerinin kabaca üst maliyet bandı nedir?",
                        List.of("s9"),
                        0.9)),
                List.of(),
                List.of(new ActionItemCandidate(
                        "Vizyon sunumunu PDF olarak iletmek",
                        "Onur",
                        null,
                        List.of("s2"),
                        0.9)),
                List.of(),
                new ArrayList<>()
        );
        assertEquals(1, kept.size());
        assertTrue(kept.getFirst().text().contains("maliyet"));
    }
}
