package com.nanobaseai.actenora.security.delivery;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nanobaseai.actenora.meetingintelligence.api.dto.MeetingNoteDetailResponse;

/**
 * Builds Template Studio {@code contentJson} from an approved meeting note detail.
 */
public final class ApprovedNoteContentJsonMapper {

    private static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules();

    private ApprovedNoteContentJsonMapper() {
    }

    public static String toContentJson(MeetingNoteDetailResponse note, String meetingTitle) {
        try {
            ObjectNode root = MAPPER.createObjectNode();
            root.put("header", blankToEmpty(meetingTitle));
            String summary = note.currentVersion() == null ? "" : blankToEmpty(note.currentVersion().executiveSummary());
            root.put("executive_summary", summary);

            ArrayNode decisions = root.putArray("decisions");
            if (note.decisions() != null) {
                for (var d : note.decisions()) {
                    if (d == null || d.text() == null || d.text().isBlank()) {
                        continue;
                    }
                    ObjectNode row = decisions.addObject();
                    row.put("decision", d.text().trim());
                    row.put("owner", "");
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
                    row.put("impact", "");
                    row.put("mitigation", blankToEmpty(r.mitigation()));
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

    private static String blankToEmpty(String value) {
        return value == null ? "" : value.trim();
    }
}
