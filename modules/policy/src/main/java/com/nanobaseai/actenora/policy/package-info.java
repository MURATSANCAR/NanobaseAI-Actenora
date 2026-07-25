/**
 * Bounded context: Policy.
 * Public API package: {@code com.nanobaseai.actenora.policy.api}.
 * Owns tenant policy defaults/overrides, quotas, SLA levels, and model allowlists.
 */
@org.springframework.modulith.ApplicationModule(
    displayName = "Policy",
    allowedDependencies = {
        "sharedkernel"
    }
)
package com.nanobaseai.actenora.policy;
