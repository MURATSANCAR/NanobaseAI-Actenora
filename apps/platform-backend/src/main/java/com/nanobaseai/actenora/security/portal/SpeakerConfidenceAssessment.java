package com.nanobaseai.actenora.security.portal;

import com.nanobaseai.actenora.sharedkernel.domain.PersonIdentityNormalizer;

import java.util.Collection;
import java.util.Locale;

/**
 * Conservative, deterministic speaker identity assessment for the review UI.
 * It never auto-resolves generic or single-token ASR labels.
 */
final class SpeakerConfidenceAssessment {

    private SpeakerConfidenceAssessment() {
    }

    static Result assess(String speaker, Collection<String> roster) {
        if (PersonIdentityNormalizer.isGenericSpeakerLabel(speaker)) {
            return new Result("MISSING", 0.0d, true);
        }
        String display = PersonIdentityNormalizer.displayName(speaker);
        if (display.split("\\s+").length < 2 || looksLikeAsrLabel(display)) {
            return new Result("UNRESOLVED", 0.35d, true);
        }
        String key = PersonIdentityNormalizer.identityKey(display);
        boolean exact = roster != null && roster.stream()
                .map(PersonIdentityNormalizer::identityKey)
                .anyMatch(key::equals);
        if (exact) {
            return new Result("RESOLVED_ROSTER", 0.98d, false);
        }
        if (PersonIdentityNormalizer.resolveUnique(display, roster).isPresent()) {
            return new Result("RESOLVED_ALIAS", 0.85d, false);
        }
        return new Result("UNRESOLVED", 0.40d, true);
    }

    private static boolean looksLikeAsrLabel(String value) {
        String letters = value.replaceAll("[^\\p{L}]", "");
        return letters.length() >= 3
                && value.equals(value.toUpperCase(Locale.forLanguageTag("tr")));
    }

    record Result(String status, double confidence, boolean reviewRequired) {
    }
}
