package com.nanobaseai.actenora.meetingintelligence.domain.validation.rules;

import com.nanobaseai.actenora.meetingintelligence.domain.validation.RuleVerdict;
import com.nanobaseai.actenora.meetingintelligence.domain.validation.ValidationCandidate;
import com.nanobaseai.actenora.meetingintelligence.domain.validation.ValidationContext;
import com.nanobaseai.actenora.meetingintelligence.domain.validation.ValidationRule;
import com.nanobaseai.actenora.meetingintelligence.domain.validation.ValidationRuleCodes;
import com.nanobaseai.actenora.meetingintelligence.domain.validation.ValidationRuleResult;

import java.util.ArrayList;
import java.util.List;

public final class AmountInTranscriptRule implements ValidationRule {

    @Override
    public String ruleId() {
        return ValidationRuleCodes.AMOUNT_IN_TRANSCRIPT;
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
            if (candidate.amountText().isEmpty()) {
                results.add(pass(candidate, "No amount claimed"));
                continue;
            }
            String amount = candidate.amountText().orElseThrow();
            if (ValidationContext.corpusContains(corpus, amount)) {
                results.add(pass(candidate, "Amount appears in transcript"));
            } else {
                results.add(fail(candidate, "Amount is not present in transcript text: " + amount));
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
