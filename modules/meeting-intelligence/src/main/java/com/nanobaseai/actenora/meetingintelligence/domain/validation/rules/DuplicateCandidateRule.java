package com.nanobaseai.actenora.meetingintelligence.domain.validation.rules;

import com.nanobaseai.actenora.meetingintelligence.domain.validation.RuleVerdict;
import com.nanobaseai.actenora.meetingintelligence.domain.validation.ValidationCandidate;
import com.nanobaseai.actenora.meetingintelligence.domain.validation.ValidationContext;
import com.nanobaseai.actenora.meetingintelligence.domain.validation.ValidationRule;
import com.nanobaseai.actenora.meetingintelligence.domain.validation.ValidationRuleCodes;
import com.nanobaseai.actenora.meetingintelligence.domain.validation.ValidationRuleResult;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class DuplicateCandidateRule implements ValidationRule {

    @Override
    public String ruleId() {
        return ValidationRuleCodes.DUPLICATE_CANDIDATE;
    }

    @Override
    public String ruleVersion() {
        return ValidationRuleCodes.VERSION_1_0_0;
    }

    @Override
    public List<ValidationRuleResult> evaluate(ValidationContext context) {
        List<ValidationRuleResult> results = new ArrayList<>();
        Set<String> seenKeys = new HashSet<>();
        Set<String> seenFingerprints = new HashSet<>();
        for (ValidationCandidate candidate : context.candidates()) {
            String fingerprint = fingerprint(candidate);
            boolean duplicateKey = !seenKeys.add(candidate.candidateKey());
            boolean duplicateFingerprint = !seenFingerprints.add(fingerprint);
            if (duplicateKey || duplicateFingerprint) {
                results.add(fail(candidate, "Duplicate candidate detected"));
            } else {
                results.add(pass(candidate, "Candidate is unique"));
            }
        }
        return results;
    }

    private static String fingerprint(ValidationCandidate candidate) {
        return candidate.kind() + "|"
                + ValidationContext.normalize(candidate.title()) + "|"
                + candidate.evidenceSegmentIds();
    }

    private ValidationRuleResult pass(ValidationCandidate candidate, String message) {
        return ValidationRuleResult.of(ruleId(), ruleVersion(), RuleVerdict.PASS, message, candidate.candidateKey(), null);
    }

    private ValidationRuleResult fail(ValidationCandidate candidate, String message) {
        return ValidationRuleResult.of(ruleId(), ruleVersion(), RuleVerdict.FAIL, message, candidate.candidateKey(), null);
    }
}
