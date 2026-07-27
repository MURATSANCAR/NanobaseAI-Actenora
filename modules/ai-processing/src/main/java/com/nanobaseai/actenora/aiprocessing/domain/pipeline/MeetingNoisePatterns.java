package com.nanobaseai.actenora.aiprocessing.domain.pipeline;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Shared TR/EN meeting noise detectors for segment drop and false-decision suppression.
 */
public final class MeetingNoisePatterns {

    /**
     * Ops / UI chatter with no extractable decisions, actions, or risks.
     * Applied after whitespace cleanup; marker keywords alone must not keep these.
     */
    private static final Pattern LOW_SIGNAL_SEGMENT = Pattern.compile(
            "(?iu)^("
                    + "mikrofonumu\\s+a[cç][iı]yorum"
                    + "|ekran[ıi]\\s+payla[sş][ıi]yorum[^\\p{L}]*"
                    + "|tabloyu\\s+yukar[ıi]\\s+kayd[ıi]r[ıi]yorum"
                    + "|k[ıi]sa\\s+bir\\s+ara\\s+verip\\s+d[oö]n[uü]yoruz"
                    + "|benim\\s+taraf[ıi]mda\\s+ek\\s+bir\\s+engel\\s+yok"
                    + "|not\\s+ald[ıi]m[,.]?\\s*devam\\s+edelim"
                    + "|anlad[ıi]m[,.]?\\s*te[sş]ekk[uü]rler"
                    + "|yeni\\s+karar\\s+yok"
                    + "|ekrandaki\\s+madde\\s+listesini\\s+senkronize\\s+ediyorum[^\\p{L}]*yeni\\s+karar\\s+yok"
                    + "|this\\s+point\\s+needs\\s+more\\s+discussion"
                    + "|sharing\\s+(my\\s+)?screen"
                    + "|unmuting(\\s+myself)?"
                    + ")$"
                    + "|(?iu)bu\\s+arada\\s+ekrandaki\\s+madde\\s+listesini\\s+senkronize"
                    + "|(?iu)\\bmevcut\\s+karar[ıi]?\\s+de[gğ]i[sş]tirmiyoruz\\b"
                    + "|(?iu)\\bsadece\\s+ba[gğ]lam\\s+payla[sş]"
    );

    /**
     * Status-quo / "not changing the decision" filler that models elevate to Decision.
     */
    private static final Pattern STATUS_QUO_DECISION = Pattern.compile(
            "(?iu)("
                    + "mevcut\\s+karar(lar)?([ıi]n[ıi]?)?\\s+de[gğ]i[sş]tir(miyoruz|meyece[gğ]iz|ilmeyecek)"
                    + "|karar(lar)?\\s+de[gğ]i[sş]tir(miyoruz|meyece[gğ]iz|ilmeyecek).{0,40}ba[gğ]lam"
                    + "|sadece\\s+ba[gğ]lam\\s+payla[sş]"
                    + "|yeni\\s+karar\\s+yok"
                    + "|not\\s+changing\\s+(the\\s+)?(existing\\s+)?decision"
                    + "|only\\s+(sharing\\s+)?context"
                    + ")"
    );

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
        // Very short acknowledgements with no substance.
        if (text.length() < 12) {
            return true;
        }
        return LOW_SIGNAL_SEGMENT.matcher(text).find();
    }

    public static boolean isStatusQuoNonDecision(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        return STATUS_QUO_DECISION.matcher(text.strip()).find();
    }

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

    static String normalizeKey(String text) {
        return text == null ? "" : text.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }
}
