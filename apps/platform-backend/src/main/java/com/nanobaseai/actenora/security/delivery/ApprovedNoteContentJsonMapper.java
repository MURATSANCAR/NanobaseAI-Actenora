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
    private static final Pattern AGENDA_PREFIX = Pattern.compile(
            "(?i)^(?:Gündem|Agenda)\\s*:\\s*(.+?)(?:\\.\\s+|\\.$|$)",
            Pattern.DOTALL
    );

    private ApprovedNoteContentJsonMapper() {
    }

    public static String toContentJson(MeetingNoteDetailResponse note, String meetingTitle) {
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

            root.putArray("participant_table");
            root.put("footer", "Actenora · NanobaseAI");
            return MAPPER.writeValueAsString(root);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to build approved note contentJson", ex);
        }
    }

    /**
     * Derives agenda rows from FinalNoteAssembler summary prefix ({@code Gündem:} / {@code Agenda:}).
     */
    public static List<String> parseAgendaItems(String executiveSummary) {
        if (executiveSummary == null || executiveSummary.isBlank()) {
            return List.of();
        }
        Matcher matcher = AGENDA_PREFIX.matcher(executiveSummary.trim());
        if (!matcher.find()) {
            return List.of();
        }
        String payload = matcher.group(1).trim();
        if (payload.isEmpty()) {
            return List.of();
        }
        List<String> items = new ArrayList<>();
        for (String part : payload.split(";")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                items.add(trimmed);
            }
        }
        return items;
    }

    private static String blankToEmpty(String value) {
        return value == null ? "" : value.trim();
    }
}
