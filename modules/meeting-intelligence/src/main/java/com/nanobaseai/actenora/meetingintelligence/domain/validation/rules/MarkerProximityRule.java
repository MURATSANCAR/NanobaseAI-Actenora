package com.nanobaseai.actenora.meetingintelligence.domain.validation.rules;

import com.nanobaseai.actenora.meetingintelligence.domain.validation.RuleVerdict;
import com.nanobaseai.actenora.meetingintelligence.domain.validation.ValidationCandidate;
import com.nanobaseai.actenora.meetingintelligence.domain.validation.ValidationContext;
import com.nanobaseai.actenora.meetingintelligence.domain.validation.ValidationRule;
import com.nanobaseai.actenora.meetingintelligence.domain.validation.ValidationRuleCodes;
import com.nanobaseai.actenora.meetingintelligence.domain.validation.ValidationRuleResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class MarkerProximityRule implements ValidationRule {

    @Override
    public String ruleId() {
        return ValidationRuleCodes.MARKER_PROXIMITY;
    }

    @Override
    public String ruleVersion() {
        return ValidationRuleCodes.VERSION_1_0_0;
    }

    @Override
    public List<ValidationRuleResult> evaluate(ValidationContext context) {
        List<ValidationRuleResult> results = new ArrayList<>();
        for (ValidationCandidate candidate : context.candidates()) {
            if (!candidate.requiresMarkerProximity()) {
                results.add(pass(candidate, "Marker proximity not required"));
                continue;
            }
            boolean nearMarker = candidate.evidenceSegmentIds().stream()
                    .map(context::segment)
                    .flatMap(Optional::stream)
                    .anyMatch(segment -> segment.markerNear());
            if (nearMarker) {
                results.add(pass(candidate, "Evidence is near a decision marker"));
            } else {
                results.add(fail(candidate, "Required marker proximity not found near evidence"));
            }
        }
        return results;
    }

    private ValidationRuleResult pass(ValidationCandidate candidate, String message) {
        return ValidationRuleResult.of(ruleId(), ruleVersion(), RuleVerdict.PASS, message, candidate.candidateKey(), null);
    }

    private ValidationRuleResult fail(ValidationCandidate candidate, String message) {
        return ValidationRuleResult.of(ruleId(), ruleVersion(), RuleVerdict.FAIL, message, candidate.candidateKey(), null);
    }
}
