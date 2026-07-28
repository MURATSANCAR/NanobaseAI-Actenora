package com.nanobaseai.actenora.aiprocessing.domain.pipeline.speechact;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeterministicSpeechActMatcherTest {

    private final DeterministicSpeechActMatcher matcher = DeterministicSpeechActMatcher.loadDefaultTr();

    @Test
    void classifiesStatusQuoAndExplicitDecision() {
        assertEquals(MeetingSpeechAct.STATUS_QUO,
                matcher.classify("Mevcut kararı değiştirmiyoruz.").speechAct());
        assertEquals(MeetingSpeechAct.EXPLICIT_DECISION,
                matcher.classify("Kurul, mevcut kararı değiştirmemeye karar verdi.").speechAct());
    }

    @Test
    void classifiesProposalAndDiscussion() {
        assertEquals(MeetingSpeechAct.PROPOSAL_CUE,
                matcher.classify("Bu öneriyi not ediyorum ama henüz karar değil.").speechAct());
        assertEquals(MeetingSpeechAct.DISCUSSION_PROMPT,
                matcher.classify("Bu noktayı biraz açmamız iyi olur.").speechAct());
    }

    @Test
    void classifiesNoteAndClosing() {
        assertEquals(MeetingSpeechAct.NOTE_INSTRUCTION,
                matcher.classify("Alınan kararları tutanağa taşıyoruz.").speechAct());
        assertEquals(MeetingSpeechAct.CLOSING_META,
                matcher.classify("Teşekkürler, toplantıyı kapatıyorum.").speechAct());
        assertEquals(MeetingSpeechAct.CLOSING_META,
                matcher.classify("Yönetici özeti için bugünkü ana sonuçları kısa tutalım.").speechAct());
    }
}
