package com.nanobaseai.actenora.meetingintelligence.domain.validation.rules;

import com.nanobaseai.actenora.meetingintelligence.domain.validation.CandidateKind;
import com.nanobaseai.actenora.meetingintelligence.domain.validation.RuleVerdict;
import com.nanobaseai.actenora.meetingintelligence.domain.validation.ValidationCandidate;
import com.nanobaseai.actenora.meetingintelligence.domain.validation.ValidationContext;
import com.nanobaseai.actenora.meetingintelligence.domain.validation.ValidationRule;
import com.nanobaseai.actenora.meetingintelligence.domain.validation.ValidationRuleCodes;
import com.nanobaseai.actenora.meetingintelligence.domain.validation.ValidationRuleResult;

import java.util.ArrayList;
import java.util.List;

/**
 * Suggestions must not be promoted to decisions by the model.
 */
public final class SuggestionNotDecisionRule implements ValidationRule {

    @Override
    public String ruleId() {
        return ValidationRuleCodes.SUGGESTION_NOT_DECISION;
    }

    @Override
    public String ruleVersion() {
        return ValidationRuleCodes.VERSION_1_0_0;
    }

    @Override
    public List<ValidationRuleResult> evaluate(ValidationContext context) {
        List<ValidationRuleResult> results = new ArrayList<>();
        for (ValidationCandidate candidate : context.candidates()) {
            boolean suggestionKind = candidate.kind() == CandidateKind.SUGGESTION;
            if (suggestionKind && candidate.markedAsDecision()) {
                results.add(fail(candidate, "Suggestion incorrectly marked as decision"));
            } else if (candidate.kind() != CandidateKind.DECISION && candidate.markedAsDecision()) {
                results.add(fail(candidate, "Non-decision candidate marked as decision"));
            } else {
                results.add(pass(candidate, "Decision marking is consistent"));
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
