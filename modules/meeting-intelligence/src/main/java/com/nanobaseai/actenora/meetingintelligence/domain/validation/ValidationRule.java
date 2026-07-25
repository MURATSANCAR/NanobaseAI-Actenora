package com.nanobaseai.actenora.meetingintelligence.domain.validation;

import java.util.List;

/**
 * Single deterministic validation rule. Implementations must be side-effect free.
 */
public interface ValidationRule {

    String ruleId();

    String ruleVersion();

    List<ValidationRuleResult> evaluate(ValidationContext context);
}
