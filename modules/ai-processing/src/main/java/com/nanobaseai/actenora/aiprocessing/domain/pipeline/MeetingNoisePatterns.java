package com.nanobaseai.actenora.aiprocessing.domain.pipeline;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Shared TR/EN meeting noise detectors for segment drop and false-decision suppression.
 */
public final class MeetingNoisePatterns {

    /**
     * Ops / UI / status-quo chatter. A segment is low-signal only when, after stripping these,
     * little substantive text remains — so mixed signal+filler lines are kept.
     */
    private static final Pattern LOW_SIGNAL_PHRASE = Pattern.compile(
            "(?iu)("
                    + "mikrofonumu\\s+a[cç][iı]yorum"
                    + "|ekran[ıi]\\s+payla[sş][ıi]yorum"
                    + "|tabloyu\\s+yukar[ıi]\\s+kayd[ıi]r[ıi]yorum"
                    + "|k[ıi]sa\\s+bir\\s+ara\\s+verip\\s+d[oö]n[uü]yoruz"
                    + "|benim\\s+taraf[ıi]mda\\s+ek\\s+bir\\s+engel\\s+yok"
                    + "|not\\s+ald[ıi]m[,.]?\\s*devam\\s+edelim"
                    + "|anlad[ıi]m[,.]?\\s*te[sş]ekk[uü]rler"
                    + "|yeni\\s+karar\\s+yok"
                    + "|ekrandaki\\s+madde\\s+listesini\\s+senkronize(\\s+ediyorum)?"
                    + "|mevcut\\s+karar[ıi]?\\s+de[gğ]i[sş]tirmiyoruz"
                    + "|sadece\\s+ba[gğ]lam\\s+payla[sş]"
                    + "([ıi]yorum|[ıi]yoruz|[ıi]m[ıi]|ımı)?"
                    + "|bu\\s+noktay[ıi]\\s+biraz\\s+a[cç]mam[ıi]z\\s+[iı]yi\\s+olur"
                    + "|bu\\s+konuyu\\s+biraz\\s+a[cç]al[ıi]m"
                    + "|buray[ıi]\\s+a[cç]mam[ıi]z\\s+gerekiyor"
                    + "|ayn[ıi]\\s+[sş]ekilde\\s+devam"
                    + "|this\\s+point\\s+needs\\s+more\\s+discussion"
                    + "|sharing\\s+(my\\s+)?screen"
                    + "|unmuting(\\s+myself)?"
                    + ")"
    );

    private static final Pattern STATUS_QUO_DECISION = Pattern.compile(
            "(?iu)("
                    + "mevcut\\s+karar[\\p{L}]*\\s+de[gğ]i[sş]tir(miyoruz|meyece[gğ]iz|ilmeyecek)"
                    + "|karar[\\p{L}]*\\s+de[gğ]i[sş]tir(miyoruz|meyece[gğ]iz|ilmeyecek).{0,40}ba[gğ]lam"
                    + "|sadece\\s+ba[gğ]lam(\\s+payla[sş][\\p{L}]*)?"
                    + "|yeni\\s+karar\\s+yok"
                    + "|not\\s+changing\\s+(the\\s+)?(existing\\s+)?decision"
                    + "|only\\s+(sharing\\s+)?context"
                    + ")"
    );

    private static final Pattern PUNCT_ONLY = Pattern.compile("[\\s\\p{Punct}]+");

    private MeetingNoisePatterns() {
    }

    public static boolean isLowSignalSegment(String content) {
        if (content == null) {
            return true;
        }
        String text = content.strip();
        if (text.isEmpty()) {
            return true;
        }
        if (text.length() < 12) {
            return true;
        }
        if (!LOW_SIGNAL_PHRASE.matcher(text).find()) {
            return false;
        }
        String remainder = PUNCT_ONLY.matcher(LOW_SIGNAL_PHRASE.matcher(text).replaceAll(" ")).replaceAll(" ").strip();
        // Keep if meaningful content survives after removing known filler phrases.
        return remainder.length() < 20;
    }

    public static boolean isStatusQuoNonDecision(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        return STATUS_QUO_DECISION.matcher(text.strip()).find();
    }

    /**
     * @deprecated Prefer {@code CrossTypeMeetingItemScrubber}; retained for legacy tests.
     */
    @Deprecated(forRemoval = false)
    public static ExtractionBundle stripStatusQuoDecisions(ExtractionBundle bundle) {
        List<DecisionCandidate> kept = new ArrayList<>(bundle.decisions().size());
        for (DecisionCandidate decision : bundle.decisions()) {
            if (!isStatusQuoNonDecision(decision.text())) {
                kept.add(decision);
            }
        }
        if (kept.size() == bundle.decisions().size()) {
            return bundle;
        }
        return new ExtractionBundle(
                bundle.topics(),
                kept,
                bundle.actionItems(),
                bundle.risks(),
                bundle.openQuestions(),
                bundle.commitments(),
                bundle.issues(),
                bundle.proposals(),
                bundle.importantFacts(),
                bundle.qualityFlags(),
                bundle.evidenceSegmentIds(),
                bundle.confidence()
        );
    }
}
