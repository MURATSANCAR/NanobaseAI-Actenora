package com.nanobaseai.actenora.meetingintelligence.domain.validation.rules;

import com.nanobaseai.actenora.meetingintelligence.domain.validation.RuleVerdict;
import com.nanobaseai.actenora.meetingintelligence.domain.validation.ValidationCandidate;
import com.nanobaseai.actenora.meetingintelligence.domain.validation.ValidationContext;
import com.nanobaseai.actenora.meetingintelligence.domain.validation.ValidationRule;
import com.nanobaseai.actenora.meetingintelligence.domain.validation.ValidationRuleCodes;
import com.nanobaseai.actenora.meetingintelligence.domain.validation.ValidationRuleResult;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class EvidenceSegmentExistsRule implements ValidationRule {

    @Override
    public String ruleId() {
        return ValidationRuleCodes.EVIDENCE_SEGMENT_EXISTS;
    }

    @Override
    public String ruleVersion() {
        return ValidationRuleCodes.VERSION_1_0_0;
    }

    @Override
    public List<ValidationRuleResult> evaluate(ValidationContext context) {
        List<ValidationRuleResult> results = new ArrayList<>();
        for (ValidationCandidate candidate : context.candidates()) {
            if (candidate.evidenceSegmentIds().isEmpty()) {
                results.add(fail(candidate, "Candidate has no evidence segments"));
                continue;
            }
            List<UUID> missing = candidate.evidenceSegmentIds().stream()
                    .filter(id -> context.segment(id).isEmpty())
                    .toList();
            if (!missing.isEmpty()) {
                results.add(fail(candidate, "Missing evidence segments: " + missing));
            } else {
                results.add(pass(candidate, "All evidence segments exist"));
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
