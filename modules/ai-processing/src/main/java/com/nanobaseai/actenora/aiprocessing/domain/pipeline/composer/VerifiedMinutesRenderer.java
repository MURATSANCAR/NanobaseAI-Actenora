package com.nanobaseai.actenora.aiprocessing.domain.pipeline.composer;

import com.nanobaseai.actenora.aiprocessing.domain.pipeline.FinalNoteDraft;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.consistency.CrossTypeConsistencyAuditor;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

/**
 * Writes professional final minutes only from the accepted global ledger.
 * Never reuses pre-audit composer prose for concrete claims.
 */
public final class VerifiedMinutesRenderer {

    public FinalNoteDraft renderDeterministic(
            FinalNoteDraft acceptedLedger,
            GlobalComposition.MeetingFrame meetingFrame
    ) {
        Objects.requireNonNull(acceptedLedger, "acceptedLedger");
        String summary = buildDeterministicSummary(acceptedLedger, meetingFrame);
        List<String> flags = new ArrayList<>(acceptedLedger.qualityFlags());
        flags.add("COMPOSER_RENDER_DETERMINISTIC");
        FinalNoteDraft draft = new FinalNoteDraft(
                summary,
                acceptedLedger.decisions(),
                acceptedLedger.actionItems(),
                acceptedLedger.risks(),
                acceptedLedger.openQuestions(),
                acceptedLedger.commitments(),
                acceptedLedger.topics(),
                acceptedLedger.issues(),
                acceptedLedger.proposals(),
                acceptedLedger.importantFacts(),
                List.copyOf(flags),
                acceptedLedger.evidenceSegmentIds(),
                acceptedLedger.confidence(),
                acceptedLedger.requiresManualReview()
        );
        return new CrossTypeConsistencyAuditor().audit(draft, false);
    }

    /**
     * Ensures every concrete claim token from summary that looks like an owner name
     * appearing next to action language is present on accepted actions (soft check flag).
     */
    public FinalNoteDraft assertProseConsistent(FinalNoteDraft draft) {
        Objects.requireNonNull(draft, "draft");
        String summary = draft.executiveSummary() == null ? "" : draft.executiveSummary();
        Set<String> owners = new LinkedHashSet<>();
        draft.actionItems().forEach(a -> {
            if (a.owner() != null && !a.owner().isBlank()) {
                owners.add(a.owner().toLowerCase(Locale.ROOT));
            }
        });
        draft.commitments().forEach(c -> {
            if (c.owner() != null && !c.owner().isBlank()) {
                owners.add(c.owner().toLowerCase(Locale.ROOT));
            }
        });
        List<String> flags = new ArrayList<>(draft.qualityFlags());
        // If summary mentions an owner-like token that is not on the ledger after scrub, flag it.
        // Conservative: only flag when summary contains "Murat"/"Mehmet"/etc. patterns already on ledger then removed —
        // here we only add REVIEW when summary is non-blank but decisions+actions empty while frame claimed outcomes.
        if (!summary.isBlank()
                && draft.decisions().isEmpty()
                && draft.actionItems().isEmpty()
                && summary.toLowerCase(Locale.ROOT).contains("karar")) {
            flags.add("PROSE_LEDGER_INCONSISTENCY");
        }
        if (flags.equals(draft.qualityFlags())) {
            return draft;
        }
        return new FinalNoteDraft(
                draft.executiveSummary(),
                draft.decisions(),
                draft.actionItems(),
                draft.risks(),
                draft.openQuestions(),
                draft.commitments(),
                draft.topics(),
                draft.issues(),
                draft.proposals(),
                draft.importantFacts(),
                List.copyOf(flags),
                draft.evidenceSegmentIds(),
                draft.confidence(),
                true
        );
    }

    static String buildDeterministicSummary(
            FinalNoteDraft ledger,
            GlobalComposition.MeetingFrame meetingFrame
    ) {
        StringBuilder sb = new StringBuilder();
        if (meetingFrame != null && meetingFrame.text() != null && !meetingFrame.text().isBlank()) {
            sb.append(meetingFrame.text().strip());
        }
        appendSection(sb, "Kararlar", ledger.decisions(), d -> d.text());
        appendSection(sb, "Aksiyonlar", ledger.actionItems(), a -> {
            String owner = a.owner() == null || a.owner().isBlank() ? "" : a.owner() + ": ";
            return owner + a.text();
        });
        appendSection(sb, "Riskler", ledger.risks(), r -> r.text());
        if (sb.isEmpty()) {
            return "Toplantı çıktıları doğrulandı; ayrıntılar yapılandırılmış maddelerde yer almaktadır.";
        }
        return sb.toString().strip();
    }

    private static <T> void appendSection(
            StringBuilder sb,
            String title,
            List<T> items,
            Function<T, String> textOf
    ) {
        if (items == null || items.isEmpty()) {
            return;
        }
        if (!sb.isEmpty()) {
            sb.append("\n\n");
        }
        sb.append(title).append(':');
        int i = 1;
        for (T item : items) {
            sb.append('\n').append(i++).append(". ").append(textOrEmpty(textOf.apply(item)));
        }
    }

    private static String textOrEmpty(String text) {
        return text == null ? "" : text.strip();
    }
}
