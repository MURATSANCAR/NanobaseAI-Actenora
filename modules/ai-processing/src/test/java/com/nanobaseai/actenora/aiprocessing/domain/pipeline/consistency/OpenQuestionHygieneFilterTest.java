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
 * - mid-meeting chat / facilitation questions never become open-question outcomes
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
    void dropsMidMeetingChatAndFacilitationQuestions() {
        List<OpenQuestionCandidate> kept = filter.filter(
                List.of(
                        new OpenQuestionCandidate(
                                "Bi bi şey var mı kafanıza bununla ilgili bir zamanlama var mı?",
                                List.of("s1"), 0.84),
                        new OpenQuestionCandidate(
                                "Hangi örnekler var, neler yapılabilir?",
                                List.of("s2"), 0.84),
                        new OpenQuestionCandidate(
                                "Yani o anlamda aslında hani çalışabilecek ekipler hangileri?",
                                List.of("s3"), 0.84),
                        new OpenQuestionCandidate("Çok mu doğru?", List.of("s4"), 0.84),
                        new OpenQuestionCandidate("Geriden mi gelecek?", List.of("s5"), 0.84)
                ),
                List.of(),
                List.of(),
                List.of(),
                new ArrayList<>()
        );
        assertTrue(kept.isEmpty(), "chatty mid-meeting questions must never become open questions");
    }

    @Test
    void dropsQuestionsAnsweredByHighConfidenceDecision() {
        List<OpenQuestionCandidate> kept = filter.filter(
                List.of(new OpenQuestionCandidate(
                        "Core banking çözümü Simple ile devam edilecek mi?",
                        List.of("s1"),
                        0.9)),
                List.of(new DecisionCandidate(
                        "Core banking çözümü Simple ile devam edilecek.",
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
