package com.nanobaseai.actenora.meetingintelligence.domain.validation;

import com.nanobaseai.actenora.meetingintelligence.domain.validation.rules.AmountInTranscriptRule;
import com.nanobaseai.actenora.meetingintelligence.domain.validation.rules.ConfidenceThresholdRule;
import com.nanobaseai.actenora.meetingintelligence.domain.validation.rules.DueDateInTranscriptRule;
import com.nanobaseai.actenora.meetingintelligence.domain.validation.rules.DuplicateCandidateRule;
import com.nanobaseai.actenora.meetingintelligence.domain.validation.rules.EvidenceSegmentExistsRule;
import com.nanobaseai.actenora.meetingintelligence.domain.validation.rules.MarkerProximityRule;
import com.nanobaseai.actenora.meetingintelligence.domain.validation.rules.OffsetValidityRule;
import com.nanobaseai.actenora.meetingintelligence.domain.validation.rules.OwnerIsParticipantRule;
import com.nanobaseai.actenora.meetingintelligence.domain.validation.rules.SpeakerAttributionSafetyRule;
import com.nanobaseai.actenora.meetingintelligence.domain.validation.rules.SuggestionNotDecisionRule;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Runs all registered validation rules and collects per-rule results.
 */
public final class ValidationRuleEngine {

    public static final String ENGINE_VERSION = "1.0.0";

    private final List<ValidationRule> rules;

    public ValidationRuleEngine(List<ValidationRule> rules) {
        this.rules = List.copyOf(Objects.requireNonNull(rules, "rules"));
        if (this.rules.isEmpty()) {
            throw new IllegalArgumentException("at least one validation rule is required");
        }
    }

    public static ValidationRuleEngine withDefaults() {
        return new ValidationRuleEngine(List.of(
                new EvidenceSegmentExistsRule(),
                new OffsetValidityRule(),
                new OwnerIsParticipantRule(),
                new DueDateInTranscriptRule(),
                new AmountInTranscriptRule(),
                new SuggestionNotDecisionRule(),
                new DuplicateCandidateRule(),
                new MarkerProximityRule(),
                new SpeakerAttributionSafetyRule(),
                new ConfidenceThresholdRule()
        ));
    }

    public List<ValidationRule> rules() {
        return rules;
    }

    public List<ValidationRuleResult> evaluate(ValidationContext context) {
        Objects.requireNonNull(context, "context");
        List<ValidationRuleResult> results = new ArrayList<>();
        for (ValidationRule rule : rules) {
            results.addAll(rule.evaluate(context));
        }
        return List.copyOf(results);
    }
}
