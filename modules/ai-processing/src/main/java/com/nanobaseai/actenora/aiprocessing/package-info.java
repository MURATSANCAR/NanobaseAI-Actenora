/**
 * Bounded context: AI Processing.
 * Public API package: com.nanobaseai.actenora.aiprocessing.api (named interface "api").
 * Internal packages are not accessible from other modules.
 */
@org.springframework.modulith.ApplicationModule(
    displayName = "AI Processing",
    allowedDependencies = {"sharedkernel","tenant :: api","modelmanagement :: api","transcript :: api"}
)
package com.nanobaseai.actenora.aiprocessing;
