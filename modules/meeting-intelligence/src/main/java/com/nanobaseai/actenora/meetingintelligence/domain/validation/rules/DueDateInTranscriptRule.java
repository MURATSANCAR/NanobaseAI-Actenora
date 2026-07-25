package com.nanobaseai.actenora.meetingintelligence.domain.validation.rules;

import com.nanobaseai.actenora.meetingintelligence.domain.validation.RuleVerdict;
import com.nanobaseai.actenora.meetingintelligence.domain.validation.ValidationCandidate;
import com.nanobaseai.actenora.meetingintelligence.domain.validation.ValidationContext;
import com.nanobaseai.actenora.meetingintelligence.domain.validation.ValidationRule;
import com.nanobaseai.actenora.meetingintelligence.domain.validation.ValidationRuleCodes;
import com.nanobaseai.actenora.meetingintelligence.domain.validation.ValidationRuleResult;

import java.util.ArrayList;
import java.util.List;

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
            if (candidate.dueDateText().isEmpty()) {
                results.add(pass(candidate, "No due date claimed"));
                continue;
            }
            String due = candidate.dueDateText().orElseThrow();
            if (ValidationContext.corpusContains(corpus, due)) {
                results.add(pass(candidate, "Due date appears in transcript"));
            } else {
                results.add(fail(candidate, "Due date is not supported by transcript text: " + due));
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
