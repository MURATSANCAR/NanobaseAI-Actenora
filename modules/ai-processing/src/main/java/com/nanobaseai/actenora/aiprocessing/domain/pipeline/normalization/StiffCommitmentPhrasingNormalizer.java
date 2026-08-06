package com.nanobaseai.actenora.aiprocessing.domain.pipeline.normalization;

import com.nanobaseai.actenora.aiprocessing.domain.pipeline.CommitmentCandidate;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.FinalNoteDraft;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Softens stiff legalistic Turkish commitment phrasing in user-facing minutes prose.
 * Prefer natural meeting language ("…yapacağını belirtti") over "taahhüt etti/etmiştir".
 */
public final class StiffCommitmentPhrasingNormalizer {

    /**
     * "oluşturmayı taahhüt etmiştir" → "oluşturacağını belirtti"
     * "göndermeyi taahhüt etti" → "göndereceğini belirtti"
     * "eklemeyi taahhüt etti" → "ekleyeceğini belirtti" (y-buffer after vowel stem)
     */
    private static final Pattern INFINITIVE_COMMIT = Pattern.compile(
            "(?iu)(\\p{L}+?)(ma|me)y[ıiuü]\\s+taahhüt\\s+et(?:miştir|ti|miş)"
    );

    /** Leftover bare forms: "taahhüt etti / etmiştir / eder / edilmektedir". */
    private static final Pattern BARE_COMMIT = Pattern.compile(
            "(?iu)\\btaahhüt\\s+(?:et(?:miştir|ti|miş|er|mektedir)|edil(?:miştir|di|miş|mektedir))\\b"
    );

    private static final String VOWELS = "aeıioöuüAEIİOÖUÜ";

    private StiffCommitmentPhrasingNormalizer() {
    }

    public static String soften(String text) {
        if (text == null || text.isBlank()) {
            return text == null ? "" : text;
        }
        Matcher infinitive = INFINITIVE_COMMIT.matcher(text);
        StringBuffer sb = new StringBuffer();
        while (infinitive.find()) {
            String stem = infinitive.group(1);
            String maMe = infinitive.group(2);
            boolean aHarmony = maMe.equalsIgnoreCase("ma");
            String buffer = endsWithVowel(stem) ? "y" : "";
            String replacement = stem + buffer + (aHarmony ? "acağını" : "eceğini") + " belirtti";
            infinitive.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        infinitive.appendTail(sb);
        String softened = BARE_COMMIT.matcher(sb.toString()).replaceAll("belirtti");
        return softened.replaceAll("\\s{2,}", " ").strip();
    }

    private static boolean endsWithVowel(String stem) {
        if (stem == null || stem.isEmpty()) {
            return false;
        }
        return VOWELS.indexOf(stem.charAt(stem.length() - 1)) >= 0;
    }

    public static FinalNoteDraft softenDraft(FinalNoteDraft draft) {
        if (draft == null) {
            return null;
        }
        List<CommitmentCandidate> commitments = new ArrayList<>(draft.commitments().size());
        for (CommitmentCandidate c : draft.commitments()) {
            commitments.add(new CommitmentCandidate(
                    soften(c.text()),
                    c.owner(),
                    c.evidenceSegmentIds(),
                    c.confidence()
            ));
        }
        return new FinalNoteDraft(
                soften(draft.executiveSummary()),
                draft.decisions(),
                draft.actionItems(),
                draft.risks(),
                draft.openQuestions(),
                List.copyOf(commitments),
                draft.topics(),
                draft.issues(),
                draft.proposals(),
                draft.importantFacts(),
                draft.qualityFlags(),
                draft.evidenceSegmentIds(),
                draft.confidence(),
                draft.requiresManualReview()
        );
    }
}
