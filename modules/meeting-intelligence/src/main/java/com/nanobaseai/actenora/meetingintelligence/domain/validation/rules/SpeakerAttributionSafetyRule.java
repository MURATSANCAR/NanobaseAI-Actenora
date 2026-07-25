package com.nanobaseai.actenora.meetingintelligence.domain.validation.rules;

import com.nanobaseai.actenora.meetingintelligence.domain.validation.RuleVerdict;
import com.nanobaseai.actenora.meetingintelligence.domain.validation.ValidationCandidate;
import com.nanobaseai.actenora.meetingintelligence.domain.validation.ValidationContext;
import com.nanobaseai.actenora.meetingintelligence.domain.validation.ValidationRule;
import com.nanobaseai.actenora.meetingintelligence.domain.validation.ValidationRuleCodes;
import com.nanobaseai.actenora.meetingintelligence.domain.validation.ValidationRuleResult;
import com.nanobaseai.actenora.meetingintelligence.domain.validation.ValidationSegment;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class SpeakerAttributionSafetyRule implements ValidationRule {

    @Override
    public String ruleId() {
        return ValidationRuleCodes.SPEAKER_ATTRIBUTION_SAFE;
    }

    @Override
    public String ruleVersion() {
        return ValidationRuleCodes.VERSION_1_0_0;
    }

    @Override
    public List<ValidationRuleResult> evaluate(ValidationContext context) {
        List<ValidationRuleResult> results = new ArrayList<>();
        for (ValidationCandidate candidate : context.candidates()) {
            if (candidate.attributedSpeakerId().isEmpty()) {
                results.add(pass(candidate, "No speaker attribution claimed"));
                continue;
            }
            String claimed = candidate.attributedSpeakerId().orElseThrow();
            List<ValidationSegment> evidence = candidate.evidenceSegmentIds().stream()
                    .map(context::segment)
                    .flatMap(Optional::stream)
                    .toList();
            if (evidence.isEmpty()) {
                results.add(fail(candidate, "Cannot verify speaker without evidence segments"));
                continue;
            }
            boolean anyUnknown = evidence.stream().anyMatch(s -> s.speakerIdOptional().isEmpty());
            boolean matches = evidence.stream().anyMatch(s ->
                    s.speakerIdOptional().map(id -> id.equalsIgnoreCase(claimed)).orElse(false)
                            || s.speakerDisplayNameOptional()
                            .map(name -> ValidationContext.normalize(name)
                                    .equals(ValidationContext.normalize(claimed)))
                            .orElse(false)
            );
            if (matches) {
                results.add(pass(candidate, "Speaker attribution matches evidence"));
            } else if (anyUnknown) {
                results.add(warn(candidate, "Speaker attribution unsafe: evidence speaker unknown"));
            } else {
                results.add(fail(candidate, "Speaker attribution does not match evidence speakers"));
            }
        }
        return results;
    }

    private ValidationRuleResult pass(ValidationCandidate candidate, String message) {
        return ValidationRuleResult.of(ruleId(), ruleVersion(), RuleVerdict.PASS, message, candidate.candidateKey(), null);
    }

    private ValidationRuleResult warn(ValidationCandidate candidate, String message) {
        return ValidationRuleResult.of(ruleId(), ruleVersion(), RuleVerdict.WARN, message, candidate.candidateKey(), null);
    }

    private ValidationRuleResult fail(ValidationCandidate candidate, String message) {
        return ValidationRuleResult.of(ruleId(), ruleVersion(), RuleVerdict.FAIL, message, candidate.candidateKey(), null);
    }
}
