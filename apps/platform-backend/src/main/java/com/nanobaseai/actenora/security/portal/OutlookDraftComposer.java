package com.nanobaseai.actenora.security.portal;

import com.nanobaseai.actenora.meeting.api.dto.MeetingResponse;
import com.nanobaseai.actenora.meetingintelligence.api.dto.MeetingNoteDetailResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Configurable, versioned Outlook follow-up rendering.
 */
@Component
public final class OutlookDraftComposer {

    private static final Pattern TEMPLATE_TOKEN = Pattern.compile("\\{\\{([a-zA-Z0-9_.-]+)}}");

    private final String subjectTemplate;
    private final String htmlTemplate;
    private final String actionTemplate;

    public OutlookDraftComposer(
            ResourceLoader loader,
            @Value("${actenora.portal.outlook-draft.subject-template}") String subjectResource,
            @Value("${actenora.portal.outlook-draft.html-template}") String htmlResource,
            @Value("${actenora.portal.outlook-draft.action-template}") String actionResource
    ) {
        this.subjectTemplate = read(loader.getResource(subjectResource)).trim();
        this.htmlTemplate = read(loader.getResource(htmlResource));
        this.actionTemplate = read(loader.getResource(actionResource));
    }

    public ComposedDraft compose(MeetingResponse meeting, MeetingNoteDetailResponse note) {
        Objects.requireNonNull(meeting, "meeting");
        Objects.requireNonNull(note, "note");
        String plainTitle = singleLine(meeting.title());
        String htmlTitle = escape(meeting.title());
        String summary = escape(note.currentVersion().executiveSummary());
        String actions = note.actionItems().stream()
                .map(action -> render(actionTemplate, Map.of(
                        "action.text", escape(action.text()),
                        "action.owner", escape(action.owner()),
                        "action.dueDate", escape(action.dueAt() == null
                                ? action.dueDate() == null ? "" : action.dueDate().toString()
                                : action.dueAt().toString()))))
                .collect(Collectors.joining());
        return new ComposedDraft(
                render(subjectTemplate, Map.of("meeting.title", plainTitle)),
                render(htmlTemplate, Map.of(
                        "meeting.title", htmlTitle,
                        "note.summary", summary,
                        "note.actions", actions))
        );
    }

    private static String render(String template, Map<String, String> values) {
        Matcher matcher = TEMPLATE_TOKEN.matcher(template);
        StringBuilder rendered = new StringBuilder();
        while (matcher.find()) {
            String token = matcher.group(1);
            String value = values.get(token);
            if (value == null) {
                throw new IllegalStateException("Missing template value: " + token);
            }
            matcher.appendReplacement(rendered, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(rendered);
        return rendered.toString();
    }

    private static String singleLine(String value) {
        return value == null ? "" : value.replaceAll("\\R+", " ").trim();
    }

    private static String read(Resource resource) {
        try (var input = resource.getInputStream()) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("Outlook draft template resource could not be loaded", ex);
        }
    }

    private static String escape(Object value) {
        String text = value == null ? "" : value.toString();
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    public record ComposedDraft(String subject, String bodyHtml) {
    }
}
