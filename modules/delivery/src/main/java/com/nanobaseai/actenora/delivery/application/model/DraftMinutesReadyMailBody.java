package com.nanobaseai.actenora.delivery.application.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Structured plain-text body for {@code DRAFT_ORGANIZER} minutes-ready mail.
 * Parsed by SMTP / Graph HTML rendering so the mail carries the actual minutes
 * content (decisions, actions, open questions) and the CTA links to the minutes page.
 */
public record DraftMinutesReadyMailBody(
        String meetingTitle,
        String whenLabel,
        String meetingUrl,
        UUID meetingOccurrenceId,
        String executiveSummary,
        List<String> decisions,
        List<ActionLine> actions,
        List<String> openQuestions,
        int reviewFlagCount
) {

    /** One action item as carried in the mail: what, who, by when. */
    public record ActionLine(String text, String owner, String dueLabel) {
        public ActionLine {
            text = text == null ? "" : text.trim();
            owner = owner == null ? "" : owner.trim();
            dueLabel = dueLabel == null ? "" : dueLabel.trim();
        }

        public boolean hasOwner() {
            return !owner.isBlank();
        }

        public boolean hasDue() {
            return !dueLabel.isBlank();
        }
    }

    private static final String KEY_TITLE = "title=";
    private static final String KEY_WHEN = "when=";
    private static final String KEY_URL = "meetingUrl=";
    private static final String KEY_ID = "meetingId=";
    private static final String KEY_SUMMARY = "summary=";
    private static final String KEY_DECISION = "decision=";
    private static final String KEY_ACTION = "action=";
    private static final String KEY_QUESTION = "question=";
    private static final String KEY_REVIEW_FLAGS = "reviewFlags=";

    private static final String FIELD_SEPARATOR = "||";
    private static final String PIPE_PLACEHOLDER = "&#124;";

    private static final int SUMMARY_MAX = 600;
    private static final int ITEM_MAX = 200;
    private static final int MAX_DECISIONS = 6;
    private static final int MAX_ACTIONS = 8;
    private static final int MAX_QUESTIONS = 5;

    public DraftMinutesReadyMailBody {
        Objects.requireNonNull(meetingTitle, "meetingTitle");
        Objects.requireNonNull(whenLabel, "whenLabel");
        Objects.requireNonNull(meetingUrl, "meetingUrl");
        Objects.requireNonNull(meetingOccurrenceId, "meetingOccurrenceId");
        if (executiveSummary == null) {
            executiveSummary = "";
        }
        decisions = limit(decisions, MAX_DECISIONS);
        actions = limitActions(actions, MAX_ACTIONS);
        openQuestions = limit(openQuestions, MAX_QUESTIONS);
        if (reviewFlagCount < 0) {
            reviewFlagCount = 0;
        }
    }

    /** Minimal body without extracted content (status-only mail). */
    public DraftMinutesReadyMailBody(
            String meetingTitle,
            String whenLabel,
            String meetingUrl,
            UUID meetingOccurrenceId,
            String executiveSummary
    ) {
        this(
                meetingTitle,
                whenLabel,
                meetingUrl,
                meetingOccurrenceId,
                executiveSummary,
                List.of(),
                List.of(),
                List.of(),
                0
        );
    }

    public boolean hasContent() {
        return !decisions.isEmpty() || !actions.isEmpty() || !openQuestions.isEmpty();
    }

    public String encode() {
        StringBuilder machine = new StringBuilder()
                .append(KEY_TITLE).append(escapeLine(meetingTitle)).append('\n')
                .append(KEY_WHEN).append(escapeLine(whenLabel)).append('\n')
                .append(KEY_URL).append(meetingUrl.trim()).append('\n')
                .append(KEY_ID).append(meetingOccurrenceId).append('\n')
                .append(KEY_SUMMARY).append(escapeLine(truncate(executiveSummary, SUMMARY_MAX))).append('\n');
        for (String decision : decisions) {
            machine.append(KEY_DECISION).append(escapeLine(truncate(decision, ITEM_MAX))).append('\n');
        }
        for (ActionLine action : actions) {
            machine.append(KEY_ACTION)
                    .append(encodeField(truncate(action.text(), ITEM_MAX)))
                    .append(FIELD_SEPARATOR).append(encodeField(action.owner()))
                    .append(FIELD_SEPARATOR).append(encodeField(action.dueLabel()))
                    .append('\n');
        }
        for (String question : openQuestions) {
            machine.append(KEY_QUESTION).append(escapeLine(truncate(question, ITEM_MAX))).append('\n');
        }
        machine.append(KEY_REVIEW_FLAGS).append(reviewFlagCount).append('\n');
        return machine.append('\n').append(plainTextSummary()).toString();
    }

    /** Human-readable text/plain alternative shown by clients without HTML. */
    private String plainTextSummary() {
        StringBuilder text = new StringBuilder()
                .append(meetingTitle)
                .append(" toplantısı için tutanak hazırlandı ve onayınızı bekliyor.\n");
        if (!whenLabel.isBlank()) {
            text.append("Tarih: ").append(whenLabel).append('\n');
        }
        if (!executiveSummary.isBlank()) {
            text.append("\nYÖNETİCİ ÖZETİ\n").append(truncate(executiveSummary, SUMMARY_MAX)).append('\n');
        }
        if (!decisions.isEmpty()) {
            text.append("\nKARARLAR\n");
            for (String decision : decisions) {
                text.append("- ").append(truncate(decision, ITEM_MAX)).append('\n');
            }
        }
        if (!actions.isEmpty()) {
            text.append("\nAKSİYONLAR\n");
            for (ActionLine action : actions) {
                text.append("- ").append(truncate(action.text(), ITEM_MAX));
                if (action.hasOwner()) {
                    text.append(" (Sorumlu: ").append(action.owner());
                    text.append(action.hasDue() ? " · Termin: " + action.dueLabel() + ")" : ")");
                } else if (action.hasDue()) {
                    text.append(" (Termin: ").append(action.dueLabel()).append(')');
                }
                text.append('\n');
            }
        }
        if (!openQuestions.isEmpty()) {
            text.append("\nAÇIK SORULAR\n");
            for (String question : openQuestions) {
                text.append("- ").append(truncate(question, ITEM_MAX)).append('\n');
            }
        }
        if (reviewFlagCount > 0) {
            text.append("\nNot: ").append(reviewFlagCount)
                    .append(" madde manuel kontrol gerektiriyor olarak işaretlendi.\n");
        }
        return text.append("\nTutanağı incelemek ve onaylamak için: ")
                .append(meetingUrl.trim())
                .toString();
    }

    public static DraftMinutesReadyMailBody decode(String bodyText) {
        String title = "";
        String when = "";
        String url = "";
        String summary = "";
        UUID id = null;
        int reviewFlags = 0;
        List<String> decisions = new ArrayList<>();
        List<ActionLine> actions = new ArrayList<>();
        List<String> questions = new ArrayList<>();
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
                } else if (trimmed.startsWith(KEY_DECISION)) {
                    addIfPresent(decisions, unescapeLine(trimmed.substring(KEY_DECISION.length())));
                } else if (trimmed.startsWith(KEY_QUESTION)) {
                    addIfPresent(questions, unescapeLine(trimmed.substring(KEY_QUESTION.length())));
                } else if (trimmed.startsWith(KEY_ACTION)) {
                    ActionLine action = decodeAction(trimmed.substring(KEY_ACTION.length()));
                    if (action != null) {
                        actions.add(action);
                    }
                } else if (trimmed.startsWith(KEY_REVIEW_FLAGS)) {
                    try {
                        reviewFlags = Integer.parseInt(trimmed.substring(KEY_REVIEW_FLAGS.length()).trim());
                    } catch (NumberFormatException ignored) {
                        // keep 0
                    }
                }
            }
            // Legacy plain draft bodies: keep a short preview from free text.
            if (title.isBlank() && url.isBlank() && !bodyText.isBlank()) {
                summary = truncate(bodyText.replace("\r\n", "\n").strip(), SUMMARY_MAX);
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
        return new DraftMinutesReadyMailBody(
                title, when, url, id, summary, decisions, actions, questions, reviewFlags);
    }

    /** Absolute portal meeting (minutes) URL: {base}/meetings/{id} */
    public static String meetingDetailUrl(String portalBaseUrl, UUID meetingOccurrenceId) {
        return MeetingEndedMailBody.meetingDetailUrl(portalBaseUrl, meetingOccurrenceId);
    }

    private static ActionLine decodeAction(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.isEmpty()) {
            return null;
        }
        String[] parts = value.split(java.util.regex.Pattern.quote(FIELD_SEPARATOR), -1);
        String text = decodeField(parts.length > 0 ? parts[0] : "");
        String owner = decodeField(parts.length > 1 ? parts[1] : "");
        String due = decodeField(parts.length > 2 ? parts[2] : "");
        return text.isBlank() ? null : new ActionLine(text, owner, due);
    }

    private static void addIfPresent(List<String> target, String value) {
        if (value != null && !value.isBlank()) {
            target.add(value.trim());
        }
    }

    private static List<String> limit(List<String> values, int max) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        List<String> kept = new ArrayList<>();
        for (String value : values) {
            if (value == null || value.isBlank()) {
                continue;
            }
            kept.add(value.trim());
            if (kept.size() == max) {
                break;
            }
        }
        return List.copyOf(kept);
    }

    private static List<ActionLine> limitActions(List<ActionLine> values, int max) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        List<ActionLine> kept = new ArrayList<>();
        for (ActionLine value : values) {
            if (value == null || value.text().isBlank()) {
                continue;
            }
            kept.add(value);
            if (kept.size() == max) {
                break;
            }
        }
        return List.copyOf(kept);
    }

    private static String encodeField(String value) {
        return escapeLine(value).replace("|", PIPE_PLACEHOLDER);
    }

    private static String decodeField(String value) {
        return unescapeLine(value == null ? "" : value.replace(PIPE_PLACEHOLDER, "|"));
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
