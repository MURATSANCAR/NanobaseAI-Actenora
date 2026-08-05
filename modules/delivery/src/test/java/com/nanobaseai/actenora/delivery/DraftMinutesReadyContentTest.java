package com.nanobaseai.actenora.delivery;

import com.nanobaseai.actenora.delivery.application.model.DraftMinutesReadyMailBody;
import com.nanobaseai.actenora.delivery.application.model.DraftMinutesReadyMailBody.ActionLine;
import com.nanobaseai.actenora.delivery.infrastructure.render.MeetingNoteBrandedTemplates;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DraftMinutesReadyContentTest {

    private static final UUID MEETING_ID = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final String URL = "https://portal.nanobase.ai/easymeeting/meetings/" + MEETING_ID;

    private static DraftMinutesReadyMailBody sample() {
        return new DraftMinutesReadyMailBody(
                "Q3 Ürün Planlama",
                "5 Ağustos 2026 · 14:00–15:00",
                URL,
                MEETING_ID,
                "Roadmap öncelikleri netleşti.",
                List.of("Ödeme altyapısı yenilemesi Q4'e alındı", "Mobil ekip kurulacak"),
                List.of(
                        new ActionLine("Teknik tasarım dokümanı paylaşılacak", "Murat Sancar", "12 Ağustos 2026"),
                        new ActionLine("Bütçe revizyonu hazırlanacak", "", "")),
                List.of("Mobil ekip için bütçe onayı kimden alınacak?"),
                2);
    }

    @Test
    void encodeDecodePreservesDecisionsActionsAndQuestions() {
        DraftMinutesReadyMailBody decoded = DraftMinutesReadyMailBody.decode(sample().encode());

        assertEquals(2, decoded.decisions().size());
        assertEquals(1, decoded.openQuestions().size());
        assertEquals(2, decoded.reviewFlagCount());
        assertEquals(URL, decoded.meetingUrl());
        assertTrue(decoded.hasContent());

        ActionLine owned = decoded.actions().stream()
                .filter(a -> a.text().contains("Teknik tasarım"))
                .findFirst()
                .orElseThrow();
        assertEquals("Murat Sancar", owned.owner());
        assertEquals("12 Ağustos 2026", owned.dueLabel());

        ActionLine unowned = decoded.actions().stream()
                .filter(a -> a.text().contains("Bütçe revizyonu"))
                .findFirst()
                .orElseThrow();
        assertFalse(unowned.hasOwner());
        assertFalse(unowned.hasDue());
    }

    @Test
    void actionTextWithPipeSurvivesRoundTrip() {
        DraftMinutesReadyMailBody body = new DraftMinutesReadyMailBody(
                "Mimari", "", URL, MEETING_ID, "",
                List.of(),
                List.of(new ActionLine("A || B seçimi netleşecek", "Ayşe", "")),
                List.of(),
                0);

        ActionLine decoded = DraftMinutesReadyMailBody.decode(body.encode()).actions().stream()
                .filter(a -> a.owner().equals("Ayşe"))
                .findFirst()
                .orElseThrow();
        assertEquals("A || B seçimi netleşecek", decoded.text());
    }

    @Test
    void htmlRendersContentSectionsAndReviewNotice() {
        String html = MeetingNoteBrandedTemplates.draftMinutesReadyEmailHtml(
                DraftMinutesReadyMailBody.decode(sample().encode()));

        assertTrue(html.contains("Kararlar"));
        assertTrue(html.contains("Ödeme altyapısı yenilemesi Q4'e alındı"));
        assertTrue(html.contains("Aksiyonlar"));
        assertTrue(html.contains("Sorumlu: Murat Sancar"));
        assertTrue(html.contains("Termin: 12 Ağustos 2026"));
        assertTrue(html.contains("Açık sorular"));
        assertTrue(html.contains("2 karar"));
        assertTrue(html.contains("manuel kontrol bekliyor"));
    }

    @Test
    void statusOnlyBodyOmitsContentSections() {
        String html = MeetingNoteBrandedTemplates.draftMinutesReadyEmailHtml(
                new DraftMinutesReadyMailBody("Kısa toplantı", "", URL, MEETING_ID, "Özet yok."));

        assertFalse(html.contains("Kararlar"));
        assertFalse(html.contains("Aksiyonlar"));
        assertFalse(html.contains("manuel kontrol bekliyor"));
        assertTrue(html.contains("Tutanağı incele ve onayla"));
    }
}
