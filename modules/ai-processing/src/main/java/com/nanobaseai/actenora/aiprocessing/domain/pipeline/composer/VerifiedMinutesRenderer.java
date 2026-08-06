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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Writes professional final minutes only from the accepted global ledger.
 * Never reuses pre-audit composer prose for concrete claims.
 */
public final class VerifiedMinutesRenderer {

    public static final String FLAG_PROSE_LEDGER_INCONSISTENCY = "PROSE_LEDGER_INCONSISTENCY";
    public static final String FLAG_UNSUPPORTED_FINAL_CLAIM = "UNSUPPORTED_FINAL_CLAIM";
    public static final String FLAG_PROSE_REBUILT_FROM_LEDGER = "PROSE_REBUILT_FROM_LEDGER";

    private static final Pattern OWNERISH = Pattern.compile(
            "(?iu)\\b([\\p{L}][\\p{L}.'-]{2,30}(?:\\s+[\\p{L}][\\p{L}.'-]{2,30}){0,3})\\b"
                    + "\\s*(?:takip|organize|iletecek|g[oö]nderecek|payla[sş]acak|yapacak)"
    );

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
     * If polished prose asserts owners/actions not present on the accepted ledger,
     * rebuild summary from the ledger so JSON ↔ prose inconsistency stays at zero.
     */
    public FinalNoteDraft assertProseConsistent(FinalNoteDraft draft) {
        return assertProseConsistent(draft, null);
    }

    public FinalNoteDraft assertProseConsistent(
            FinalNoteDraft draft,
            GlobalComposition.MeetingFrame meetingFrame
    ) {
        Objects.requireNonNull(draft, "draft");
        String summary = draft.executiveSummary() == null ? "" : draft.executiveSummary();
        Set<String> ledgerOwners = ledgerOwners(draft);
        Set<String> ledgerActionCores = new LinkedHashSet<>();
        draft.actionItems().forEach(a -> ledgerActionCores.add(core(a.text())));
        draft.decisions().forEach(d -> ledgerActionCores.add(core(d.text())));

        List<String> unsupported = new ArrayList<>();
        Matcher m = OWNERISH.matcher(summary);
        while (m.find()) {
            String name = m.group(1).strip().toLowerCase(Locale.ROOT);
            if (name.length() < 3 || isStopword(name)) {
                continue;
            }
            boolean onLedger = ledgerOwners.stream().anyMatch(o -> o.contains(name) || name.contains(o));
            if (!onLedger && looksLikePerson(name)) {
                unsupported.add(name);
            }
        }

        List<String> flags = new ArrayList<>(draft.qualityFlags());
        if (!summary.isBlank()
                && draft.decisions().isEmpty()
                && draft.actionItems().isEmpty()
                && summary.toLowerCase(Locale.ROOT).contains("karar")) {
            flags.add(FLAG_PROSE_LEDGER_INCONSISTENCY);
            unsupported.add("karar-without-ledger");
        }
        if (unsupported.isEmpty() && flags.equals(draft.qualityFlags())) {
            return draft;
        }
        if (!unsupported.isEmpty()) {
            flags.add(FLAG_UNSUPPORTED_FINAL_CLAIM);
            flags.add(FLAG_PROSE_REBUILT_FROM_LEDGER);
            String rebuilt = buildDeterministicSummary(draft, meetingFrame);
            return new FinalNoteDraft(
                    rebuilt,
                    draft.decisions(),
                    draft.actionItems(),
                    draft.risks(),
                    draft.openQuestions(),
                    draft.commitments(),
                    draft.topics(),
                    draft.issues(),
                    draft.proposals(),
                    draft.importantFacts(),
                    List.copyOf(new LinkedHashSet<>(flags)),
                    draft.evidenceSegmentIds(),
                    draft.confidence(),
                    true
            );
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

    private static Set<String> ledgerOwners(FinalNoteDraft draft) {
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
        return owners;
    }

    private static String core(String text) {
        return text == null ? "" : text.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").strip();
    }

    private static boolean isStopword(String name) {
        return Set.of("bu", "şu", "o", "bir", "ile", "için", "sonra", "önce", "takım", "ekip")
                .contains(name);
    }

    private static boolean looksLikePerson(String name) {
        return name.chars().filter(Character::isLetter).count() >= 4
                && !name.contains("toplant")
                && !name.contains("proje")
                && !name.contains("süreç");
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
