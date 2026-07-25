/**
 * Bounded context: Meeting Intelligence.
 * Public API package: com.nanobaseai.actenora.meetingintelligence.api (named interface "api").
 * Internal packages are not accessible from other modules.
 */
@org.springframework.modulith.ApplicationModule(
    displayName = "Meeting Intelligence",
    allowedDependencies = {
        "sharedkernel",
        "tenant :: api",
        "meeting :: api",
        "transcript :: api",
        "aiprocessing :: api",
        "approval :: api"
    }
)
package com.nanobaseai.actenora.meetingintelligence;
