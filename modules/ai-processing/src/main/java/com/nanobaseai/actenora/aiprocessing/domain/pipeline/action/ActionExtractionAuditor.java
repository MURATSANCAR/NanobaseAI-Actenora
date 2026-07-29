package com.nanobaseai.actenora.aiprocessing.domain.pipeline.action;

import com.nanobaseai.actenora.aiprocessing.domain.pipeline.ActionItemCandidate;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Overall extraction audit for action quality (separate from decision/proposal consistency).
 */
public final class ActionExtractionAuditor {

    public static final String OVERALL_PASSED = "OVERALL_EXTRACTION_AUDIT_PASSED";
    public static final String OVERALL_FAILED = "OVERALL_EXTRACTION_AUDIT_FAILED";
    public static final String DECISION_PASSED = "DECISION_CONSISTENCY_AUDIT_PASSED";
    public static final String PREFIX_LEAK = "ACTION_PREFIX_LEAK";
    public static final String UNSPLIT_COMPOUND = "UNSPLIT_COMPOUND_ACTION";
    public static final String DATE_CUE_MISSING_STRUCTURED = "DATE_CUE_WITHOUT_STRUCTURED_DATE";
    public static final String DUPLICATE_ACTION = "DUPLICATE_ACTION_REMAINING";
    public static final String UNRESOLVED_RELATIVE_DATE = "UNRESOLVED_RELATIVE_DATE";

    private final ActionDiscoursePrefixNormalizer prefixNormalizer = new ActionDiscoursePrefixNormalizer();
    private final ActionDeduplicator deduplicator = new ActionDeduplicator();

    public record AuditResult(boolean passed, List<String> flags) {
    }

    public AuditResult audit(List<ActionItemCandidate> actions) {
        Set<String> flags = new LinkedHashSet<>();
        boolean failed = false;
        for (ActionItemCandidate action : actions) {
            if (prefixNormalizer.startsWithDiscoursePrefix(action.text())) {
                flags.add(PREFIX_LEAK);
                failed = true;
            }
            if (ActionDeduplicator.looksCompound(action.text())
                    && countOwnerLikeTokens(action.text()) >= 2) {
                flags.add(UNSPLIT_COMPOUND);
                failed = true;
            }
            boolean hasCue = TurkishRelativeDateResolver.containsDateCue(action.text())
                    || (action.relativeDate() != null && !action.relativeDate().isBlank());
            boolean structured = (action.dueAt() != null && !action.dueAt().isBlank())
                    || (action.dueDate() != null && !action.dueDate().isBlank())
                    || (action.relativeDate() != null && !action.relativeDate().isBlank());
            if (TurkishRelativeDateResolver.containsDateCue(action.text()) && !structured) {
                flags.add(DATE_CUE_MISSING_STRUCTURED);
                failed = true;
            }
            if (action.relativeDate() != null && !action.relativeDate().isBlank()
                    && (action.dueAt() == null || action.dueAt().isBlank())
                    && (action.dueDate() == null || action.dueDate().isBlank())) {
                // relative present but unresolved to calendar — review, not always fail
                flags.add(UNRESOLVED_RELATIVE_DATE);
            }
            if (hasCue) {
                // no-op; used for clarity
            }
        }
        ActionDeduplicator.Result stillDup = deduplicator.deduplicate(actions);
        if (stillDup.removed() > 0) {
            flags.add(DUPLICATE_ACTION);
            failed = true;
        }
        if (failed) {
            flags.add(OVERALL_FAILED);
        } else {
            flags.add(OVERALL_PASSED);
        }
        return new AuditResult(!failed, new ArrayList<>(flags));
    }

    /**
     * Remaps legacy CONSISTENCY_AUDIT_PASSED into decision-scoped token when overall also present.
     */
    public List<String> remapDecisionFlags(List<String> existingFlags, boolean decisionUnresolved) {
        Set<String> out = new LinkedHashSet<>();
        for (String f : existingFlags) {
            if (f == null) {
                continue;
            }
            if ("CONSISTENCY_AUDIT_PASSED".equals(f)) {
                out.add(DECISION_PASSED);
                continue;
            }
            if ("CONSISTENCY_AUDIT_NEEDS_REVIEW".equals(f) && decisionUnresolved) {
                out.add(f);
                continue;
            }
            out.add(f);
        }
        return new ArrayList<>(out);
    }

    private static int countOwnerLikeTokens(String text) {
        // Count capitalized-ish name tokens before finite verbs separated by ';'
        String[] parts = text.split(";");
        int n = 0;
        for (String part : parts) {
            String p = part.strip();
            if (p.matches("(?iu)^[\\p{L}][\\p{L}'\\-]{1,40}\\s+.+(acak|ecek)\\.?$")) {
                n++;
            }
        }
        return n;
    }
}
