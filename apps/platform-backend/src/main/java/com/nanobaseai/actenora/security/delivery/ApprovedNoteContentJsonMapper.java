package com.nanobaseai.actenora.security.delivery;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nanobaseai.actenora.meetingintelligence.api.dto.MeetingNoteDetailResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Builds Template Studio {@code contentJson} from an approved meeting note detail.
 */
public final class ApprovedNoteContentJsonMapper {

    private static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules();
    private static final Pattern AGENDA_PREFIX = Pattern.compile("(?i)^(?:Gündem|Agenda)\\s*:\\s*");
    private static final Pattern AGENDA_STAT_LINE = Pattern.compile(
            "(?im)^\\d+\\s+(?:karar\\b|decision\\b|aksiyon\\b|action\\b|risk\\b)"
    );
    /** Same-line legacy summaries: {@code Budget. 1 karar kaydedildi.} */
    private static final Pattern AGENDA_INLINE_STAT = Pattern.compile(
            "(?i)\\.\\s*\\d+\\s+(?:karar\\b|decision\\b|aksiyon\\b|action\\b|risk\\b)"
    );
    private static final Pattern NUMBERED_ITEM = Pattern.compile("^\\d+\\.\\s+(.+)$");

    private ApprovedNoteContentJsonMapper() {
    }

    /**
     * Lightweight participant projection for note contentJson (avoids meeting-module coupling in callers' tests).
     */
    public record ParticipantRow(String name, String email, String role, String attendance) {
    }

    public static String toContentJson(MeetingNoteDetailResponse note, String meetingTitle) {
        return toContentJson(note, meetingTitle, List.of());
    }

    public static String toContentJson(
            MeetingNoteDetailResponse note,
            String meetingTitle,
            List<ParticipantRow> participants
    ) {
        try {
            ObjectNode root = MAPPER.createObjectNode();
            root.put("header", blankToEmpty(meetingTitle));
            String summary = note.currentVersion() == null ? "" : blankToEmpty(note.currentVersion().executiveSummary());
            root.put("executive_summary", summary);

            ArrayNode agenda = root.putArray("agenda");
            for (String item : parseAgendaItems(summary)) {
                ObjectNode row = agenda.addObject();
                row.put("item", item);
                row.put("duration", "");
            }

            ArrayNode decisions = root.putArray("decisions");
            if (note.decisions() != null) {
                for (var d : note.decisions()) {
                    if (d == null || d.text() == null || d.text().isBlank()) {
                        continue;
                    }
                    ObjectNode row = decisions.addObject();
                    row.put("decision", d.text().trim());
                    row.put("owner", "");
                    row.put("rationale", blankToEmpty(d.rationale()));
                    row.put("status", blankToEmpty(d.decisionStatus()));
                }
            }

            ArrayNode actions = root.putArray("actions");
            if (note.actionItems() != null) {
                for (var a : note.actionItems()) {
                    if (a == null || a.text() == null || a.text().isBlank()) {
                        continue;
                    }
                    ObjectNode row = actions.addObject();
                    row.put("action", a.text().trim());
                    row.put("assignee", blankToEmpty(a.owner()));
                    row.put("due", a.dueDate() == null ? "" : a.dueDate().toString());
                    row.put("ownerType", blankToEmpty(a.ownerType()));
                    row.put("priority", blankToEmpty(a.priority()));
                    row.put("relativeDate", blankToEmpty(a.relativeDate()));
                }
            }

            ArrayNode risks = root.putArray("risks");
            if (note.risks() != null) {
                for (var r : note.risks()) {
                    if (r == null || r.text() == null || r.text().isBlank()) {
                        continue;
                    }
                    ObjectNode row = risks.addObject();
                    row.put("risk", r.text().trim());
                    row.put("impact", blankToEmpty(r.likelihood()));
                    row.put("mitigation", blankToEmpty(r.mitigation()));
                    row.put("likelihood", blankToEmpty(r.likelihood()));
                }
            }

            ArrayNode questions = root.putArray("open_questions");
            if (note.openQuestions() != null) {
                for (var q : note.openQuestions()) {
                    if (q == null || q.text() == null || q.text().isBlank()) {
                        continue;
                    }
                    ObjectNode row = questions.addObject();
                    row.put("question", q.text().trim());
                    row.put("owner", "");
                }
            }

            ArrayNode commitments = root.putArray("commitments");
            if (note.commitments() != null) {
                for (var c : note.commitments()) {
                    if (c == null || c.text() == null || c.text().isBlank()) {
                        continue;
                    }
                    ObjectNode row = commitments.addObject();
                    row.put("commitment", c.text().trim());
                    row.put("owner", blankToEmpty(c.owner()));
                    row.put("due", "");
                }
            }

            ArrayNode issues = root.putArray("issues");
            if (note.issues() != null) {
                for (var i : note.issues()) {
                    if (i == null || i.text() == null || i.text().isBlank()) {
                        continue;
                    }
                    ObjectNode row = issues.addObject();
                    row.put("issue", i.text().trim());
                }
            }

            ArrayNode proposals = root.putArray("proposals");
            if (note.proposals() != null) {
                for (var p : note.proposals()) {
                    if (p == null || p.text() == null || p.text().isBlank()) {
                        continue;
                    }
                    ObjectNode row = proposals.addObject();
                    row.put("proposal", p.text().trim());
                }
            }

            ArrayNode facts = root.putArray("important_facts");
            if (note.importantFacts() != null) {
                for (var f : note.importantFacts()) {
                    if (f == null || f.text() == null || f.text().isBlank()) {
                        continue;
                    }
                    ObjectNode row = facts.addObject();
                    row.put("fact", f.text().trim());
                }
            }

            ArrayNode topics = root.putArray("topics");
            if (note.topics() != null) {
                for (var tpc : note.topics()) {
                    if (tpc == null || tpc.text() == null || tpc.text().isBlank()) {
                        continue;
                    }
                    ObjectNode row = topics.addObject();
                    row.put("topic", tpc.text().trim());
                }
            }

            ArrayNode table = root.putArray("participant_table");
            if (participants != null) {
                for (ParticipantRow p : participants) {
                    if (p == null) {
                        continue;
                    }
                    ObjectNode row = table.addObject();
                    row.put("name", blankToEmpty(p.name()));
                    row.put("email", blankToEmpty(p.email()));
                    row.put("role", blankToEmpty(p.role()));
                    row.put("attendance", blankToEmpty(p.attendance()));
                }
            }
            root.put("footer", "Actenora · NanobaseAI");
            return MAPPER.writeValueAsString(root);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to build approved note contentJson", ex);
        }
    }

    /**
     * Derives agenda rows from FinalNoteAssembler summary prefix ({@code Gündem:} / {@code Agenda:}).
     * Supports numbered multiline format and legacy semicolon-joined payloads.
     */
    public static List<String> parseAgendaItems(String executiveSummary) {
        if (executiveSummary == null || executiveSummary.isBlank()) {
            return List.of();
        }
        String text = executiveSummary.trim();
        Matcher prefix = AGENDA_PREFIX.matcher(text);
        if (!prefix.find()) {
            return List.of();
        }
        String rest = text.substring(prefix.end());
        Matcher stop = AGENDA_STAT_LINE.matcher(rest);
        String payload = stop.find() ? rest.substring(0, stop.start()) : rest;
        Matcher inline = AGENDA_INLINE_STAT.matcher(payload);
        if (inline.find()) {
            payload = payload.substring(0, inline.start());
        }
        payload = payload.trim();
        if (payload.endsWith(".")) {
            payload = payload.substring(0, payload.length() - 1).trim();
        }
        if (payload.isEmpty()) {
            return List.of();
        }

        List<String> numbered = new ArrayList<>();
        List<String> legacy = new ArrayList<>();
        for (String line : payload.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            Matcher item = NUMBERED_ITEM.matcher(trimmed);
            if (item.matches()) {
                numbered.add(item.group(1).trim());
            } else {
                for (String part : trimmed.split(";")) {
                    String piece = part.trim();
                    if (!piece.isEmpty()) {
                        legacy.add(piece.replaceAll("\\.$", "").trim());
                    }
                }
            }
        }
        return numbered.isEmpty() ? List.copyOf(legacy) : List.copyOf(numbered);
    }

    private static String blankToEmpty(String value) {
        return value == null ? "" : value.trim();
    }
}
