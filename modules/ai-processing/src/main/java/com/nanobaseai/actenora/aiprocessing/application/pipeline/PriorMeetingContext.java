package com.nanobaseai.actenora.aiprocessing.application.pipeline;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Compact prior-meeting continuity brief injected into final minutes synthesis.
 * Sourced from the continuity ledger + hybrid knowledge search — never raw transcript.
 */
public record PriorMeetingContext(
        Optional<UUID> previousOccurrenceId,
        List<String> openTasks,
        List<String> openRisks,
        List<String> unresolvedQuestions,
        List<String> activeDecisions,
        List<String> overdueCommitments,
        List<String> relatedKnowledge
) {
    public static final PriorMeetingContext EMPTY = new PriorMeetingContext(
            Optional.empty(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of()
    );

    public PriorMeetingContext(
            Optional<UUID> previousOccurrenceId,
            List<String> openTasks,
            List<String> openRisks,
            List<String> unresolvedQuestions,
            List<String> activeDecisions,
            List<String> overdueCommitments
    ) {
        this(
                previousOccurrenceId,
                openTasks,
                openRisks,
                unresolvedQuestions,
                activeDecisions,
                overdueCommitments,
                List.of()
        );
    }

    public PriorMeetingContext {
        previousOccurrenceId = previousOccurrenceId == null ? Optional.empty() : previousOccurrenceId;
        openTasks = List.copyOf(Objects.requireNonNull(openTasks, "openTasks"));
        openRisks = List.copyOf(Objects.requireNonNull(openRisks, "openRisks"));
        unresolvedQuestions = List.copyOf(Objects.requireNonNull(unresolvedQuestions, "unresolvedQuestions"));
        activeDecisions = List.copyOf(Objects.requireNonNull(activeDecisions, "activeDecisions"));
        overdueCommitments = List.copyOf(Objects.requireNonNull(overdueCommitments, "overdueCommitments"));
        relatedKnowledge = List.copyOf(Objects.requireNonNullElse(relatedKnowledge, List.of()));
    }

    public boolean isEmpty() {
        return openTasks.isEmpty()
                && openRisks.isEmpty()
                && unresolvedQuestions.isEmpty()
                && activeDecisions.isEmpty()
                && overdueCommitments.isEmpty()
                && relatedKnowledge.isEmpty();
    }

    public String toPromptBlock() {
        if (isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        previousOccurrenceId.ifPresent(id -> sb.append("PREVIOUS_OCCURRENCE_ID: ").append(id).append('\n'));
        appendList(sb, "OPEN_TASKS", openTasks);
        appendList(sb, "OPEN_RISKS", openRisks);
        appendList(sb, "UNRESOLVED_QUESTIONS", unresolvedQuestions);
        appendList(sb, "ACTIVE_DECISIONS", activeDecisions);
        appendList(sb, "OVERDUE_COMMITMENTS", overdueCommitments);
        appendList(sb, "RELATED_KNOWLEDGE", relatedKnowledge);
        return sb.toString().trim();
    }

    private static void appendList(StringBuilder sb, String label, List<String> items) {
        if (items.isEmpty()) {
            return;
        }
        sb.append(label).append(":\n");
        for (String item : items) {
            sb.append("- ").append(item).append('\n');
        }
    }
}
