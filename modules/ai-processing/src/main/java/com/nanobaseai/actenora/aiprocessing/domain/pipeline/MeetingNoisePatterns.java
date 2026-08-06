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

    /**
     * Meeting mechanics and attendance chatter are useful in the transcript, but they are not
     * business risks, open questions, decisions, or commitments for the minutes document.
     */
    private static final Pattern MEETING_LOGISTICS = Pattern.compile(
            "(?iu)("
                    + "mikrofon[\\p{L}]*\\s+(?:a[cç]|kapa|[cç]al[ıi][sş]|gelm|kesil)"
                    + "|kamera[\\p{L}]*\\s+(?:a[cç]|kapa|[cç]al[ıi][sş]|gelm|don|kesil)"
                    + "|ses(?:iniz|im)?\\s+(?:gelmiyor|geldi|kesiliyor)"
                    + "|g[oö]r[uü]nt[uü](?:n[uü]z|m)?\\s+(?:gelmiyor|geldi|dondu|kesiliyor)"
                    + "|(?:toplant[ıi]|ses)\\s+ba[gğ]lant[ıi](?:s[ıi]|\\s+sorun)[^.]{0,30}"
                    + "|ekran[ıi]?\\s+payla[sş]|toplant[ıi]ya\\s+(?:ge[cç]\\s+)?kat[ıi]l"
                    + "|kat[ıi]l[ıi]mc[ıi]\\s+listesi|ba[sş]ka\\s+kat[ıi]l[ıi]mc[ıi]"
                    + "|kim(?:ler)?\\s+kat[ıi]lacak|kendimi\\s+tan[ıi]t"
                    + "|tan[ıi][sş]ma\\s+fasl[ıi]|s[ıi]ra\\s+kimde"
                    + "|(?:audio|video)\\s+(?:is\\s+)?(?:not\\s+)?(?:working|cutting|frozen|off|on)"
                    + "|connection\\s+(?:issue|problem|dropped)|screen\\s+shar|who\\s+is\\s+joining"
                    + ")"
    );

    /** Facilitation/preparation language about what to say, rather than a delivered commitment. */
    private static final Pattern FACILITATION_OR_PREPARATION = Pattern.compile(
            "(?iu)("
                    + "\\b(?:anlatal[ıi]m|bahsedelim|g[oö]sterelim|soral[ıi]m)\\b"
                    + "|sunum(?:un|da|daki)?[^.]{0,80}(?:[uü]zerinden\\s+ge[cç]elim|anlatal[ıi]m)"
                    + "|topu[^.]{0,40}at(?:ay[ıi]m|al[ıi]m)|kendimi\\s+tan[ıi]t"
                    + "|what\\s+we\\s+should\\s+(?:say|present|show)"
                    + ")"
    );

    private static final Pattern HEDGED_NUMERIC_CLAIM = Pattern.compile(
            "(?iu)((?:yanl[ıi][sş]\\s+hat[ıi]rlam[ıi]yorsam|hat[ıi]rlad[ıi][gğ][ıi]m\\s+kadar[ıi]yla|"
                    + "san[ıi]r[ıi]m|galiba|emin\\s+de[gğ]ilim|if\\s+i\\s+remember\\s+correctly)"
                    + "[^.]{0,140}\\d|\\d[^.]{0,140}(?:yanl[ıi][sş]\\s+hat[ıi]rlam[ıi]yorsam|"
                    + "hat[ıi]rlad[ıi][gğ][ıi]m\\s+kadar[ıi]yla|san[ıi]r[ıi]m|galiba|"
                    + "emin\\s+de[gğ]ilim|if\\s+i\\s+remember\\s+correctly))"
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

    public static boolean isMeetingLogistics(String text) {
        return text != null && !text.isBlank() && MEETING_LOGISTICS.matcher(text.strip()).find();
    }

    public static boolean isFacilitationOrPreparation(String text) {
        return text != null
                && !text.isBlank()
                && FACILITATION_OR_PREPARATION.matcher(text.strip()).find();
    }

    public static boolean hasHedgedNumericClaim(String text) {
        return text != null
                && !text.isBlank()
                && HEDGED_NUMERIC_CLAIM.matcher(text.strip()).find();
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
