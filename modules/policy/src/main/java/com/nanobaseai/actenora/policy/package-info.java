/**
 * Bounded context: Policy.
 * Public API package: com.nanobaseai.actenora.policy.api (named interface "api").
 * Internal packages are not accessible from other modules.
 */
@org.springframework.modulith.ApplicationModule(
    displayName = "Policy"
    allowedDependencies = {
        "sharedkernel",
        "tenant :: api"
    }
)
package com.nanobaseai.actenora.policy;
