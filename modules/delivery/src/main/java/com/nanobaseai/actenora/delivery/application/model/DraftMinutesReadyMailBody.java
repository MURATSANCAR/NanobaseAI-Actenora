package com.nanobaseai.actenora.delivery.application.model;

import java.util.Objects;
import java.util.UUID;

/**
 * Structured plain-text body for {@code DRAFT_ORGANIZER} minutes-ready mail.
 * Parsed by SMTP HTML rendering so the CTA links to the meeting / minutes page.
 */
public record DraftMinutesReadyMailBody(
        String meetingTitle,
        String whenLabel,
        String meetingUrl,
        UUID meetingOccurrenceId,
        String executiveSummary
) {

    private static final String KEY_TITLE = "title=";
    private static final String KEY_WHEN = "when=";
    private static final String KEY_URL = "meetingUrl=";
    private static final String KEY_ID = "meetingId=";
    private static final String KEY_SUMMARY = "summary=";

    public DraftMinutesReadyMailBody {
        Objects.requireNonNull(meetingTitle, "meetingTitle");
        Objects.requireNonNull(whenLabel, "whenLabel");
        Objects.requireNonNull(meetingUrl, "meetingUrl");
        Objects.requireNonNull(meetingOccurrenceId, "meetingOccurrenceId");
        if (executiveSummary == null) {
            executiveSummary = "";
        }
    }

    public String encode() {
        return KEY_TITLE + escapeLine(meetingTitle) + "\n"
                + KEY_WHEN + escapeLine(whenLabel) + "\n"
                + KEY_URL + meetingUrl.trim() + "\n"
                + KEY_ID + meetingOccurrenceId + "\n"
                + KEY_SUMMARY + escapeLine(truncate(executiveSummary, 400)) + "\n"
                + "\n"
                + meetingTitle + " toplantısı için tutanak hazırlandı ve sizin onayınızı bekliyor.\n"
                + "Tutanak: " + meetingUrl.trim();
    }

    public static DraftMinutesReadyMailBody decode(String bodyText) {
        String title = "";
        String when = "";
        String url = "";
        String summary = "";
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
                } else if (trimmed.startsWith(KEY_SUMMARY)) {
                    summary = unescapeLine(trimmed.substring(KEY_SUMMARY.length()));
                }
            }
            // Legacy plain draft bodies: keep a short preview from free text.
            if (title.isBlank() && url.isBlank() && !bodyText.isBlank()) {
                summary = truncate(bodyText.replace("\r\n", "\n").strip(), 400);
                title = "Toplantı";
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
        return new DraftMinutesReadyMailBody(title, when, url, id, summary);
    }

    /** Absolute portal meeting (minutes) URL: {base}/meetings/{id} */
    public static String meetingDetailUrl(String portalBaseUrl, UUID meetingOccurrenceId) {
        return MeetingEndedMailBody.meetingDetailUrl(portalBaseUrl, meetingOccurrenceId);
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        if (trimmed.length() <= max) {
            return trimmed;
        }
        return trimmed.substring(0, max - 1) + "…";
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
