package com.nanobaseai.actenora.delivery;

import com.nanobaseai.actenora.delivery.application.model.DraftMinutesReadyMailBody;
import com.nanobaseai.actenora.delivery.application.model.MeetingEndedMailBody;
import com.nanobaseai.actenora.delivery.domain.DeliveryIntent;
import com.nanobaseai.actenora.delivery.domain.DeliveryPolicySnapshot;
import com.nanobaseai.actenora.delivery.infrastructure.render.MeetingNoteBrandedTemplates;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MeetingEndedMailBodyTest {

    @Test
    void meetingDetailUrlStripsTrailingSlash() {
        UUID id = UUID.fromString("22222222-2222-2222-2222-222222222222");
        assertEquals(
                "https://portal.nanobase.ai/easymeeting/meetings/" + id,
                MeetingEndedMailBody.meetingDetailUrl("https://portal.nanobase.ai/easymeeting/", id)
        );
        assertEquals(
                "http://127.0.0.1:3000/meetings/" + id,
                MeetingEndedMailBody.meetingDetailUrl("http://127.0.0.1:3000", id)
        );
    }

    @Test
    void encodeDecodeRoundTripPreservesCtaUrl() {
        UUID id = UUID.randomUUID();
        String url = MeetingEndedMailBody.meetingDetailUrl(
                "https://portal.nanobase.ai/easymeeting", id);
        MeetingEndedMailBody original = new MeetingEndedMailBody(
                "Q3 Ürün Planlama",
                "28 Temmuz 2026 · 10:00–11:00",
                url,
                id
        );
        MeetingEndedMailBody decoded = MeetingEndedMailBody.decode(original.encode());
        assertEquals(original.meetingTitle(), decoded.meetingTitle());
        assertEquals(original.whenLabel(), decoded.whenLabel());
        assertEquals(original.meetingUrl(), decoded.meetingUrl());
        assertEquals(original.meetingOccurrenceId(), decoded.meetingOccurrenceId());
    }

    @Test
    void meetingEndedHtmlContainsWorkingMeetingDeepLink() {
        UUID id = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
        String url = "https://portal.nanobase.ai/easymeeting/meetings/" + id;
        String html = MeetingNoteBrandedTemplates.meetingEndedEmailHtml(
                new MeetingEndedMailBody("Sprint Review", "28 Temmuz 2026 · 10:00–11:00", url, id)
        );
        assertTrue(html.contains("href=\"" + url + "\""));
        assertTrue(html.contains("Toplantıya git"));
        assertTrue(html.contains("NanobaseAI EasyMeeting"));
        assertTrue(html.contains("Sprint Review"));
    }

    @Test
    void draftMinutesReadyHtmlContainsWorkingMinutesDeepLink() {
        UUID id = UUID.fromString("bbbbbbbb-cccc-dddd-eeee-ffffffffffff");
        String url = "https://portal.nanobase.ai/easymeeting/meetings/" + id;
        DraftMinutesReadyMailBody body = new DraftMinutesReadyMailBody(
                "Q3 Ürün Planlama",
                "28 Temmuz 2026 · 10:00–11:00",
                url,
                id,
                "Roadmap öncelikleri netleşti."
        );
        DraftMinutesReadyMailBody decoded = DraftMinutesReadyMailBody.decode(body.encode());
        assertEquals(url, decoded.meetingUrl());
        String html = MeetingNoteBrandedTemplates.draftMinutesReadyEmailHtml(decoded);
        assertTrue(html.contains("href=\"" + url + "\""));
        assertTrue(html.contains("Tutanaga git"));
        assertTrue(html.contains("Onayınızı bekliyor") || html.contains("onayınızı bekliyor"));
        assertTrue(html.contains("Q3 Ürün Planlama"));
        assertTrue(html.contains("Roadmap öncelikleri netleşti."));
    }

    @Test
    void meetingEndedPolicyResolvesDistinctIntent() {
        assertEquals(DeliveryIntent.MEETING_ENDED, DeliveryPolicySnapshot.meetingEndedOrganizer().resolvedIntent());
        assertEquals(DeliveryIntent.DRAFT_ORGANIZER, DeliveryPolicySnapshot.draftOrganizer().resolvedIntent());
        assertEquals(DeliveryIntent.FINAL_EXTERNAL, DeliveryPolicySnapshot.defaults().resolvedIntent());
    }
}
