package com.nanobaseai.actenora.meetingintelligence.domain.validation.rules;

import com.nanobaseai.actenora.meetingintelligence.domain.validation.RuleVerdict;
import com.nanobaseai.actenora.meetingintelligence.domain.validation.ValidationCandidate;
import com.nanobaseai.actenora.meetingintelligence.domain.validation.ValidationContext;
import com.nanobaseai.actenora.meetingintelligence.domain.validation.ValidationRule;
import com.nanobaseai.actenora.meetingintelligence.domain.validation.ValidationRuleCodes;
import com.nanobaseai.actenora.meetingintelligence.domain.validation.ValidationRuleResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.text.Normalizer;

public final class DueDateInTranscriptRule implements ValidationRule {

    @Override
    public String ruleId() {
        return ValidationRuleCodes.DUE_DATE_IN_TRANSCRIPT;
    }

    @Override
    public String ruleVersion() {
        return ValidationRuleCodes.VERSION_1_0_0;
    }

    @Override
    public List<ValidationRuleResult> evaluate(ValidationContext context) {
        List<ValidationRuleResult> results = new ArrayList<>();
        String corpus = context.transcriptCorpus();
        for (ValidationCandidate candidate : context.candidates()) {
            boolean hasDueDate = candidate.dueDateText().isPresent();
            boolean hasRelativeDateText = candidate.relativeDateText().isPresent();
            if (!hasDueDate && !hasRelativeDateText) {
                results.add(pass(candidate, "No due date claimed"));
                continue;
            }

            if (hasDueDate) {
                String due = candidate.dueDateText().orElseThrow();
                if (ValidationContext.corpusContains(corpus, due)) {
                    results.add(pass(candidate, "Due date appears in transcript"));
                    continue;
                }
            }

            if (hasRelativeDateText) {
                String relative = candidate.relativeDateText().orElseThrow();
                if (corpusContainsTurkishNormalized(corpus, relative)) {
                    results.add(pass(candidate, "Relative due date appears in transcript"));
                    continue;
                }
                if (containsRelativeDateCues(candidate.title(), corpus)) {
                    results.add(pass(candidate, "Relative due date cues found"));
                    continue;
                }
            }

            String messageNeedle = candidate.dueDateText().orElse(candidate.relativeDateText().orElse(""));
            results.add(fail(candidate, "Due date is not supported by transcript text: " + messageNeedle));
        }
        return results;
    }

    private static boolean corpusContainsTurkishNormalized(String corpus, String needle) {
        String corpusNorm = normalizeTurkishLoose(corpus);
        String needleNorm = normalizeTurkishLoose(needle);
        if (needleNorm.isBlank()) {
            return true;
        }
        return corpusNorm.contains(needleNorm);
    }

    private static boolean containsRelativeDateCues(String title, String corpus) {
        String haystack = normalizeTurkishLoose(title + " " + corpus);
        // Expected cues (case/diacritic-insensitive) - normalized to ascii inside normalizeTurkishLoose().
        return haystack.contains("bugun") || haystack.contains("yarin") || haystack.contains("oglen");
    }

    private static String normalizeTurkishLoose(String value) {
        if (value == null) {
            return "";
        }
        String v = value.strip().toLowerCase(Locale.ROOT);
        // Turkish-specific replacements to make diacritic-insensitive matching cheap & deterministic.
        v = v.replace('ı', 'i')
                .replace('İ', 'i')
                .replace('ş', 's')
                .replace('Ş', 's')
                .replace('ğ', 'g')
                .replace('Ğ', 'g')
                .replace('ç', 'c')
                .replace('Ç', 'c')
                .replace('ö', 'o')
                .replace('Ö', 'o')
                .replace('ü', 'u')
                .replace('Ü', 'u');

        // Remove any remaining diacritic marks (lightweight safeguard for other characters).
        v = Normalizer.normalize(v, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");

        // Normalize punctuation to whitespace so time formats like "16.00" vs "16:00" still match loosely.
        v = v.replaceAll("[^a-z0-9]+", " ");
        v = v.trim().replaceAll("\\s+", " ");
        return v;
    }

    private ValidationRuleResult pass(ValidationCandidate candidate, String message) {
        return ValidationRuleResult.of(ruleId(), ruleVersion(), RuleVerdict.PASS, message, candidate.candidateKey(), null);
    }

    private ValidationRuleResult fail(ValidationCandidate candidate, String message) {
        return ValidationRuleResult.of(ruleId(), ruleVersion(), RuleVerdict.FAIL, message, candidate.candidateKey(), null);
    }
}
