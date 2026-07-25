/**
 * Bounded context: Transcript.
 * Public API package: com.nanobaseai.actenora.transcript.api (named interface "api").
 * Internal packages are not accessible from other modules.
 */
@org.springframework.modulith.ApplicationModule(
    displayName = "Transcript"
    allowedDependencies = {
        "sharedkernel",
        "tenant :: api",
        "meeting :: api"
    }
)
package com.nanobaseai.actenora.transcript;
