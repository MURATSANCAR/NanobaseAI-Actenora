package com.nanobaseai.actenora.meetingintelligence.domain.validation.rules;

import com.nanobaseai.actenora.meetingintelligence.domain.validation.RuleVerdict;
import com.nanobaseai.actenora.meetingintelligence.domain.validation.ValidationCandidate;
import com.nanobaseai.actenora.meetingintelligence.domain.validation.ValidationContext;
import com.nanobaseai.actenora.meetingintelligence.domain.validation.ValidationRule;
import com.nanobaseai.actenora.meetingintelligence.domain.validation.ValidationRuleCodes;
import com.nanobaseai.actenora.meetingintelligence.domain.validation.ValidationRuleResult;

import java.util.ArrayList;
import java.util.List;

public final class OwnerIsParticipantRule implements ValidationRule {

    @Override
    public String ruleId() {
        return ValidationRuleCodes.OWNER_IS_PARTICIPANT;
    }

    @Override
    public String ruleVersion() {
        return ValidationRuleCodes.VERSION_1_0_0;
    }

    @Override
    public List<ValidationRuleResult> evaluate(ValidationContext context) {
        List<ValidationRuleResult> results = new ArrayList<>();
        for (ValidationCandidate candidate : context.candidates()) {
            if (candidate.ownerParticipantId().isEmpty() && candidate.ownerDisplayName().isEmpty()) {
                results.add(pass(candidate, "No owner claimed"));
                continue;
            }
            boolean knownId = candidate.ownerParticipantId()
                    .map(context::isKnownParticipantId)
                    .orElse(false);
            boolean knownName = candidate.ownerDisplayName()
                    .map(context::isKnownParticipantName)
                    .orElse(false);
            if (knownId || knownName) {
                results.add(pass(candidate, "Owner matches a meeting participant"));
            } else {
                results.add(fail(candidate, "Owner is not a known meeting participant"));
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
