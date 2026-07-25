/**
 * Bounded context: Audit.
 * Public API package: {@code com.nanobaseai.actenora.audit.api}.
 * Append-only audit trail; must not store transcript, private note, or raw prompt content.
 */
@org.springframework.modulith.ApplicationModule(
    displayName = "Audit",
    allowedDependencies = {
        "sharedkernel"
    }
)
package com.nanobaseai.actenora.audit;
