package com.nanobaseai.actenora.delivery.application.model;

import java.util.Objects;
import java.util.UUID;

/**
 * Structured plain-text body for {@code MEETING_ENDED} organizer mail.
 * Parsed by SMTP HTML rendering so the CTA links to the meeting detail page.
 */
public record MeetingEndedMailBody(
        String meetingTitle,
        String whenLabel,
        String meetingUrl,
        UUID meetingOccurrenceId
) {

    private static final String KEY_TITLE = "title=";
    private static final String KEY_WHEN = "when=";
    private static final String KEY_URL = "meetingUrl=";
    private static final String KEY_ID = "meetingId=";

    public MeetingEndedMailBody {
        Objects.requireNonNull(meetingTitle, "meetingTitle");
        Objects.requireNonNull(whenLabel, "whenLabel");
        Objects.requireNonNull(meetingUrl, "meetingUrl");
        Objects.requireNonNull(meetingOccurrenceId, "meetingOccurrenceId");
    }

    public String encode() {
        return KEY_TITLE + escapeLine(meetingTitle) + "\n"
                + KEY_WHEN + escapeLine(whenLabel) + "\n"
                + KEY_URL + meetingUrl.trim() + "\n"
                + KEY_ID + meetingOccurrenceId + "\n"
                + "\n"
                + meetingTitle + " toplantısı tamamlandı. "
                + "NanobaseAI EasyMeeting toplantı notu oluşturma işlemlerine başladı. "
                + "İşlemler tamamlandığında size tekrar haber vereceğiz.\n"
                + "Toplantı: " + meetingUrl.trim();
    }

    public static MeetingEndedMailBody decode(String bodyText) {
        String title = "";
        String when = "";
        String url = "";
        UUID id = null;
        if (bodyText != null) {
            for (String line : bodyText.split("\n")) {
                String trimmed = line.trim();
                if (trimmed.startsWith(KEY_TITLE)) {
                    title = unescapeLine(trimmed.substring(KEY_TITLE.length()));
                } else if (trimmed.startsWith(KEY_WHEN)) {
                    when = unescapeLine(trimmed.substring(KEY_WHEN.length()));
                } else if (trimmed.startsWith(KEY_URL)) {
                    url = trimmed.substring(KEY_URL.length()).trim();
                } else if (trimmed.startsWith(KEY_ID)) {
                    try {
                        id = UUID.fromString(trimmed.substring(KEY_ID.length()).trim());
                    } catch (IllegalArgumentException ignored) {
                        // leave null
                    }
                }
            }
        }
        if (title.isBlank()) {
            title = "Toplantı";
        }
        if (url.isBlank()) {
            url = "#";
        }
        if (id == null) {
            id = new UUID(0L, 0L);
        }
        return new MeetingEndedMailBody(title, when, url, id);
    }

    /** Absolute portal meeting detail URL: {base}/meetings/{id} */
    public static String meetingDetailUrl(String portalBaseUrl, UUID meetingOccurrenceId) {
        Objects.requireNonNull(meetingOccurrenceId, "meetingOccurrenceId");
        String base = portalBaseUrl == null ? "" : portalBaseUrl.trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + "/meetings/" + meetingOccurrenceId;
    }

    private static String escapeLine(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\r", " ").replace("\n", " ").trim();
    }

    private static String unescapeLine(String value) {
        return value == null ? "" : value.trim();
    }
}
