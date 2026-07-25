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

public final class OffsetValidityRule implements ValidationRule {

    @Override
    public String ruleId() {
        return ValidationRuleCodes.OFFSET_VALID;
    }

    @Override
    public String ruleVersion() {
        return ValidationRuleCodes.VERSION_1_0_0;
    }

    @Override
    public List<ValidationRuleResult> evaluate(ValidationContext context) {
        List<ValidationRuleResult> results = new ArrayList<>();
        for (ValidationCandidate candidate : context.candidates()) {
            Optional<Long> start = candidate.claimedStartOffsetMs();
            Optional<Long> end = candidate.claimedEndOffsetMs();
            if (start.isEmpty() && end.isEmpty()) {
                results.add(pass(candidate, "No claimed offsets to validate"));
                continue;
            }
            if (start.isEmpty() || end.isEmpty()) {
                results.add(fail(candidate, "Partial offset claim is invalid"));
                continue;
            }
            long claimedStart = start.get();
            long claimedEnd = end.get();
            if (claimedStart < 0 || claimedEnd < claimedStart) {
                results.add(fail(candidate, "Claimed offsets are chronologically invalid"));
                continue;
            }
            boolean covered = candidate.evidenceSegmentIds().stream()
                    .map(context::segment)
                    .flatMap(Optional::stream)
                    .anyMatch(segment -> covers(segment, claimedStart, claimedEnd));
            if (!covered) {
                results.add(fail(candidate, "Claimed offsets are not covered by evidence segments"));
            } else {
                results.add(pass(candidate, "Claimed offsets are covered by evidence"));
            }
        }
        return results;
    }

    private static boolean covers(ValidationSegment segment, long start, long end) {
        return segment.startOffsetMs() <= start && segment.endOffsetMs() >= end;
    }

    private ValidationRuleResult pass(ValidationCandidate candidate, String message) {
        return ValidationRuleResult.of(ruleId(), ruleVersion(), RuleVerdict.PASS, message, candidate.candidateKey(), null);
    }

    private ValidationRuleResult fail(ValidationCandidate candidate, String message) {
        return ValidationRuleResult.of(ruleId(), ruleVersion(), RuleVerdict.FAIL, message, candidate.candidateKey(), null);
    }
}
