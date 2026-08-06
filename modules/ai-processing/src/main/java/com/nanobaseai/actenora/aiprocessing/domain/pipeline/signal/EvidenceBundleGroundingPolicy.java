package com.nanobaseai.actenora.aiprocessing.domain.pipeline.signal;

import com.nanobaseai.actenora.aiprocessing.domain.pipeline.ActionItemCandidate;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.CommitmentCandidate;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.DecisionCandidate;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.ExtractionBundle;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.ImportantFactCandidate;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.IssueCandidate;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.MeetingNoisePatterns;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.OpenQuestionCandidate;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.ProposalCandidate;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.RiskCandidate;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.TopicCandidate;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Central post-extract grounding. Soft-drops unsupported decisions here — not in merger/assembler/synth.
 */
public final class EvidenceBundleGroundingPolicy implements BundleGroundingPolicy {

    private static final Pattern POSITIVE_DECISION = Pattern.compile(
            "(?iu)(kararlaştır|karar\\s+verd|onayla|onaylandı|kabul\\s+ed|dondur|kapattık|kapatıyoruz|"
                    + "değişmeyecek|freeze|approve|decided|agreed|we\\s+will|"
                    + "devam\\s+edece[gğ]iz|se[cç]tik|se[cç]ildi|tercih\\s+ettik|"
                    + "anla[sş]maya\\s+vard[ıi]k|yola\\s+devam|netle[sş]tik|netle[sş]tirdik)"
    );
    /**
     * Vendor/product selection confirmed in-meeting even when the speaker narrates the prior
     * evaluation process (e.g. "17 aylık süreç… Simple ile devam edeceğiz").
     */
    private static final Pattern SELECTION_CONFIRMATION = Pattern.compile(
            "(?iu)((?:\\S{2,40}\\s+)?(?:ile\\s+)?devam\\s+edece[gğ]iz|"
                    + "(?:olarak|ile)\\s+ilerleyece[gğ]iz|"
                    + "se[cç]tik|se[cç]ildi|se[cç]im(?:imiz|de)?\\b|"
                    + "tercih\\s+(?:ettik|edildi)|"
                    + "anla[sş]maya\\s+vard[ıi]k|"
                    + "we\\s+(?:chose|selected|will\\s+continue\\s+with)|"
                    + "selected\\s+(?:as|the))"
    );
    private static final Pattern SCOPE_OR_DATE = Pattern.compile(
            "(?iu)(cuma|çarşamba|pazartesi|hafta|çeyrek|sprint|api|fiyat|politika|sözleşme|"
                    + "\\d{1,2}[./]\\d|next\\s+week|this\\s+quarter)"
    );
    private static final Pattern PROPOSAL_ONLY = Pattern.compile(
            "(?iu)\\b(belki|öner|should|could|might|erteleyebiliriz|değerlendirelim|henüz\\s+karar\\s+değil)\\b"
    );
    private static final Pattern META_DECISION = Pattern.compile(
            "(?iu)(karar\\s+ekran|kararları\\s+(tekrar\\s+)?oku|yeni\\s+(bir\\s+)?karar\\s+alınmadı)"
    );
    private static final Pattern HISTORICAL_DECISION_CONTEXT = Pattern.compile(
            "(?iu)(daha\\s+[oö]nce|[oö]nceden|ge[cç]mi[sş]te|o\\s+d[oö]nemde|"
                    + "ba[sş]lang[ıi][cç]\\s+toplant[ıi]|karar\\s+verirken|"
                    + "s[uü]re[cç](?:te|in|inin)?[^.]{0,80}sonucunda|"
                    + "\\d+\\s*(?:ayl[ıi]k|y[ıi]ll[ıi]k)[^.]{0,60}s[uü]re[cç]|"
                    + "previously|earlier|in\\s+the\\s+previous\\s+meeting)"
    );
    private static final Pattern CURRENT_DECISION_CONTEXT = Pattern.compile(
            "(?iu)(bu\\s+toplant[ıi]da|bug[uü]n|[sş]imdi|bundan\\s+sonra|"
                    + "itibar[ıi]yla|kararla[sş]t[ıi]rd[ıi]k|mutab[ıi]k\\s+kald[ıi]k|"
                    + "netle[sş]tirdik|onaylad[ıi]k|karar[ıi]m[ıi]z|"
                    + "in\\s+this\\s+meeting|effective\\s+from|we\\s+now\\s+decide)"
    );
    private static final Pattern CONDITIONAL_OR_PROPOSAL = Pattern.compile(
            "(?iu)\\b(e[gğ]er|uygunsa|belki|[oö]neririm|isterseniz|yapabiliriz|"
                    + "de[gğ]erlendirebiliriz|could|might|if)\\b"
    );
    private static final Pattern ACCEPTED_ACTION = Pattern.compile(
            "(?iu)(tamam(?:d[ıi]r)?\\b|anla[sş]t[ıi]k|kabul\\s+edildi|onayland[ıi]|"
                    + "not\\s+al[ıi]yorum|[uü]stleniyorum|sorumlulu[gğ]u\\s+al[ıi]yorum|"
                    + "ben[^.]{0,60}(?:aca[gğ][ıi]m|ece[gğ]im)|"
                    + "biz[^.]{0,60}(?:aca[gğ][ıi]z|ece[gğ]iz)|agreed|accepted|i\\s+will)"
    );
    private static final Pattern COMMITMENT_SIGNAL = Pattern.compile(
            "(?iu)(\\b[\\p{L}]+(?:y?aca[gğ][ıi](?:m|z)|y?ece[gğ][ıi](?:m|z))\\b|"
                    + "g[oö]nderece[gğ]im|payla[sş]aca[gğ][ıi]m|haz[ıi]rlayaca[gğ][ıi]m|"
                    + "yapaca[gğ][ıi]m|iletece[gğ]im|organize\\s+edece[gğ]im|"
                    + "g[oö]nderece[gğ]iz|payla[sş]aca[gğ][ıi]z|haz[ıi]rlayaca[gğ][ıi]z|"
                    + "yapaca[gğ][ıi]z|g[oö]nderilecek|payla[sş][ıi]lacak|haz[ıi]rlanacak|"
                    + "yap[ıi]lacak|organize\\s+edilecek|planlanacak|i\\s+will|we\\s+will)"
    );
    private static final Pattern UNRESOLVED_QUESTION_SIGNAL = Pattern.compile(
            "(?iu)(a[cç][ıi]k\\s+kalan\\s+soru|netle[sş]medi|netle[sş]tirilmedi|"
                    + "cevaps[ıi]z|bekliyor|ne\\s+zaman|kim\\s+(?:sorumlu|g[oö]nderecek|[uü]stlenecek)|"
                    + "hangi\\s+(?:alarm|dashboard|metrik|log|kriter|ortam|versiyon|ad[ıi]m)|"
                    + "rollback|geri\\s+alma|k[oö]k\\s+neden|takvim|teslim\\s+tarihi|"
                    + "owner|deadline|unresolved|open\\s+question)"
    );

    @Override
    public ExtractionBundle retainGroundedItems(ExtractionBundle bundle, EvidenceIndex evidenceIndex) {
        Objects.requireNonNull(bundle, "bundle");
        Objects.requireNonNull(evidenceIndex, "evidenceIndex");

        List<String> flags = new ArrayList<>(bundle.qualityFlags());
        List<DecisionCandidate> decisions = new ArrayList<>();
        int dropped = 0;

        for (DecisionCandidate decision : bundle.decisions()) {
            if (!evidenceIndex.allResolved(decision.evidenceSegmentIds())) {
                dropped++;
                addFlag(flags, "UNRESOLVED_EVIDENCE");
                continue;
            }
            String evidence = evidenceIndex.resolve(decision.evidenceSegmentIds());
            if (isGroundedDecision(decision.text(), evidence)) {
                decisions.add(decision);
            } else {
                dropped++;
                addFlag(flags, "UNSUPPORTED_DECISION");
            }
        }

        List<ActionItemCandidate> actions = filterActions(bundle.actionItems(), evidenceIndex, flags);
        List<RiskCandidate> risks = filterRisks(bundle.risks(), evidenceIndex, flags);
        List<CommitmentCandidate> commitments = filterCommitments(
                bundle.commitments(), evidenceIndex, flags);
        List<OpenQuestionCandidate> questions = filterQuestions(
                bundle.openQuestions(), evidenceIndex, flags);
        List<TopicCandidate> topics = filterResolved(bundle.topics(), evidenceIndex,
                TopicCandidate::evidenceSegmentIds, flags);
        List<IssueCandidate> issues = filterResolved(bundle.issues(), evidenceIndex,
                IssueCandidate::evidenceSegmentIds, flags);
        List<ProposalCandidate> proposals = filterResolved(bundle.proposals(), evidenceIndex,
                ProposalCandidate::evidenceSegmentIds, flags);
        List<ImportantFactCandidate> facts = filterAndCalibrateFacts(
                bundle.importantFacts(), evidenceIndex, flags);

        if (dropped > 0) {
            addFlag(flags, "DECISION_SOFT_DROPPED");
        }

        return new ExtractionBundle(
                topics,
                decisions,
                actions,
                risks,
                questions,
                commitments,
                issues,
                proposals,
                facts,
                flags,
                bundle.evidenceSegmentIds(),
                bundle.confidence()
        );
    }

    public static boolean isGroundedDecision(String decisionText, String evidence) {
        if (evidence == null || evidence.isBlank()) {
            return false;
        }
        String ev = evidence.toLowerCase(Locale.ROOT);
        String dt = decisionText == null ? "" : decisionText.toLowerCase(Locale.ROOT);

        boolean selectionConfirmedInEvidence = SELECTION_CONFIRMATION.matcher(ev).find();
        boolean selectionConfirmed = selectionConfirmedInEvidence
                || SELECTION_CONFIRMATION.matcher(dt).find();
        // History narration alone is not an in-meeting decision; only evidence-side selection
        // confirmation (e.g. "Simple ile devam edeceğiz") can keep it.
        if (HISTORICAL_DECISION_CONTEXT.matcher(ev).find()
                && !CURRENT_DECISION_CONTEXT.matcher(ev).find()
                && !selectionConfirmedInEvidence) {
            return false;
        }

        if (META_DECISION.matcher(ev).find() && !POSITIVE_DECISION.matcher(ev).find()) {
            return false;
        }
        if (PROPOSAL_ONLY.matcher(ev).find() && !POSITIVE_DECISION.matcher(ev).find()
                && !closeOrFreeze(ev) && !selectionConfirmed) {
            return false;
        }
        if (POSITIVE_DECISION.matcher(ev).find() || closeOrFreeze(ev) || selectionConfirmed) {
            return true;
        }
        if (MeetingNoisePatterns.isStatusQuoNonDecision(dt)
                || MeetingNoisePatterns.isStatusQuoNonDecision(ev)) {
            return POSITIVE_DECISION.matcher(ev).find() && SCOPE_OR_DATE.matcher(ev).find();
        }
        // Decisions are fail-closed: topical overlap alone cannot turn discussion, history, or a
        // future possibility into a decision made in this meeting.
        return false;
    }

    private static boolean closeOrFreeze(String ev) {
        return ev.contains("dondur") || ev.contains("kapatıyoruz") || ev.contains("kapattık")
                || ev.contains("değişmeyecek") || ev.contains("freeze");
    }

    private static double tokenOverlap(String a, String b) {
        String[] ta = a.split("\\s+");
        int hits = 0;
        int meaningful = 0;
        for (String t : ta) {
            if (t.length() < 4) {
                continue;
            }
            meaningful++;
            if (b.contains(t)) {
                hits++;
            }
        }
        return meaningful == 0 ? 0.0d : (double) hits / (double) meaningful;
    }

    private static void addFlag(List<String> flags, String flag) {
        if (!flags.contains(flag)) {
            flags.add(flag);
        }
    }

    private static List<ActionItemCandidate> filterActions(
            List<ActionItemCandidate> items,
            EvidenceIndex index,
            List<String> flags
    ) {
        List<ActionItemCandidate> kept = new ArrayList<>();
        for (ActionItemCandidate item : items) {
            if (!resolved(item.evidenceSegmentIds(), index, flags)) {
                continue;
            }
            String evidence = index.resolve(item.evidenceSegmentIds());
            if (MeetingNoisePatterns.isMeetingLogistics(item.text())
                    || MeetingNoisePatterns.isMeetingLogistics(evidence)) {
                addFlag(flags, "MEETING_META_ITEM_DROPPED");
                continue;
            }
            if (CONDITIONAL_OR_PROPOSAL.matcher(evidence).find()
                    && !ACCEPTED_ACTION.matcher(evidence).find()) {
                addFlag(flags, "UNSUPPORTED_ACTION");
                continue;
            }
            kept.add(item);
        }
        return kept;
    }

    private static List<RiskCandidate> filterRisks(
            List<RiskCandidate> items,
            EvidenceIndex index,
            List<String> flags
    ) {
        List<RiskCandidate> kept = new ArrayList<>();
        for (RiskCandidate item : items) {
            if (!resolved(item.evidenceSegmentIds(), index, flags)) {
                continue;
            }
            String evidence = index.resolve(item.evidenceSegmentIds());
            if (MeetingNoisePatterns.isMeetingLogistics(item.text())
                    || MeetingNoisePatterns.isMeetingLogistics(evidence)) {
                addFlag(flags, "MEETING_META_ITEM_DROPPED");
                continue;
            }
            kept.add(item);
        }
        return kept;
    }

    private static List<CommitmentCandidate> filterCommitments(
            List<CommitmentCandidate> items,
            EvidenceIndex index,
            List<String> flags
    ) {
        List<CommitmentCandidate> kept = new ArrayList<>();
        for (CommitmentCandidate item : items) {
            if (!resolved(item.evidenceSegmentIds(), index, flags)) {
                continue;
            }
            String evidence = index.resolve(item.evidenceSegmentIds());
            boolean preparation = MeetingNoisePatterns.isFacilitationOrPreparation(evidence);
            if (MeetingNoisePatterns.isMeetingLogistics(item.text())
                    || MeetingNoisePatterns.isMeetingLogistics(evidence)) {
                addFlag(flags, "MEETING_META_ITEM_DROPPED");
                continue;
            }
            if (!COMMITMENT_SIGNAL.matcher(evidence).find()
                    || (preparation && !ACCEPTED_ACTION.matcher(evidence).find())) {
                addFlag(flags, "UNSUPPORTED_COMMITMENT");
                continue;
            }
            kept.add(item);
        }
        return kept;
    }

    private static List<OpenQuestionCandidate> filterQuestions(
            List<OpenQuestionCandidate> items,
            EvidenceIndex index,
            List<String> flags
    ) {
        List<OpenQuestionCandidate> kept = new ArrayList<>();
        for (OpenQuestionCandidate item : items) {
            if (!resolved(item.evidenceSegmentIds(), index, flags)) {
                continue;
            }
            String evidence = index.resolve(item.evidenceSegmentIds());
            if (MeetingNoisePatterns.isMeetingLogistics(item.text())
                    || MeetingNoisePatterns.isMeetingLogistics(evidence)
                    || MeetingNoisePatterns.isFacilitationOrPreparation(evidence)) {
                addFlag(flags, "MEETING_META_ITEM_DROPPED");
                continue;
            }
            boolean unresolved = UNRESOLVED_QUESTION_SIGNAL.matcher(item.text()).find()
                    || UNRESOLVED_QUESTION_SIGNAL.matcher(evidence).find();
            boolean question = item.text().contains("?") || evidence.contains("?");
            if ((!question && !unresolved) || (meaningfulWords(item.text()) < 4 && !unresolved)) {
                addFlag(flags, "UNSUPPORTED_OPEN_QUESTION");
                continue;
            }
            kept.add(item);
        }
        return kept;
    }

    private static List<ImportantFactCandidate> filterAndCalibrateFacts(
            List<ImportantFactCandidate> items,
            EvidenceIndex index,
            List<String> flags
    ) {
        List<ImportantFactCandidate> kept = new ArrayList<>();
        for (ImportantFactCandidate item : items) {
            if (!resolved(item.evidenceSegmentIds(), index, flags)) {
                continue;
            }
            String evidence = index.resolve(item.evidenceSegmentIds());
            if (MeetingNoisePatterns.isMeetingLogistics(item.text())
                    || MeetingNoisePatterns.isMeetingLogistics(evidence)) {
                addFlag(flags, "MEETING_META_ITEM_DROPPED");
                continue;
            }
            if (MeetingNoisePatterns.hasHedgedNumericClaim(evidence)) {
                kept.add(new ImportantFactCandidate(
                        item.text(), item.evidenceSegmentIds(), Math.min(item.confidence(), 0.65d)));
                addFlag(flags, "HEDGED_FACT_NEEDS_REVIEW");
            } else {
                kept.add(item);
            }
        }
        return kept;
    }

    private static boolean resolved(List<String> evidenceIds, EvidenceIndex index, List<String> flags) {
        if (index.allResolved(evidenceIds)) {
            return true;
        }
        addFlag(flags, "UNRESOLVED_EVIDENCE");
        return false;
    }

    private static int meaningfulWords(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        int count = 0;
        for (String token : text.split("\\s+")) {
            if (token.replaceAll("[^\\p{L}\\p{N}]", "").length() >= 3) {
                count++;
            }
        }
        return count;
    }

    @FunctionalInterface
    private interface EvidenceAccessor<T> {
        List<String> evidence(T item);
    }

    private static <T> List<T> filterResolved(
            List<T> items,
            EvidenceIndex index,
            EvidenceAccessor<T> accessor,
            List<String> flags
    ) {
        List<T> kept = new ArrayList<>(items.size());
        for (T item : items) {
            if (index.allResolved(accessor.evidence(item))) {
                kept.add(item);
            } else {
                addFlag(flags, "UNRESOLVED_EVIDENCE");
            }
        }
        return kept;
    }
}
