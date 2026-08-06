package com.nanobaseai.actenora.aiprocessing.domain.pipeline.normalization;

import java.util.regex.Pattern;

/**
 * Keeps the executive summary as clean prose. Weak local models sometimes dump the whole
 * note (decisions, agenda/topics, facts) into the summary field as numbered lists with
 * section headers like "Sonuçlar:", "Sonraki adımlar:", "GÖRÜŞÜLEN KONULAR", "AKSİYON PLANI".
 * Those structured items already render in their own sections, so we deterministically keep
 * only the intro prose before the first dump marker and strip trailing numbered fragments.
 */
public final class ExecutiveSummarySanitizer {

    // Header fragments that mark the start of a structured dump inside the summary.
    private static final Pattern DUMP_MARKER = Pattern.compile(
            "(?iu)(sonraki\\s+ad[ıi]mlar|aksiyon\\s+plan[ıi]|g[öo]r[üu][şs][üu]len\\s+konular|"
                    + "sonu[çc]lar\\s*:|dikkat\\s*:|kararlar\\s*:|riskler\\s*:|"
                    + "a[çc][ıi]k\\s+sorular|[öo]nemli\\s+bulgular|g[üu]ndem\\s*:)");

    // Numbered / labeled list fragments: "1. ", "12) ", "A-01 —", "K-01 -", "R-1:".
    private static final Pattern LEADING_LIST = Pattern.compile(
            "(?u)(^|\\s)(\\d{1,3}[\\.\\)]\\s|[A-ZÇĞİÖŞÜ]{1,3}-?\\d{1,3}\\s*[—\\-:])");

    private ExecutiveSummarySanitizer() {
    }

    public static String clean(String summary) {
        if (summary == null || summary.isBlank()) {
            return summary == null ? "" : summary;
        }
        String text = summary.strip();

        // Cut at the first dump marker — keep only the prose introduction before it.
        var marker = DUMP_MARKER.matcher(text);
        if (marker.find()) {
            text = text.substring(0, marker.start()).strip();
        }

        // If the surviving prose still starts with an enumerated item, the whole summary was a
        // dump with no real prose — drop it so the note relies on its structured sections.
        if (LEADING_LIST.matcher(text).lookingAt() || text.length() < 30) {
            return "";
        }

        // Trim a dangling numbered fragment at the very end (e.g. "... belirtti. 1.").
        text = text.replaceAll("(?u)\\s+\\d{1,3}[\\.\\)]\\s*$", "").strip();
        return text;
    }
}
