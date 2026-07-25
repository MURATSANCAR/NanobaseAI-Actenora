/**
 * Platform orchestration package (not a product BC).
 * Hosts extraction dual-publish adapters that talk to BC public APIs only.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Platform",
        allowedDependencies = {"transcript :: api", "sharedkernel"}
)
package com.nanobaseai.actenora.platform;
