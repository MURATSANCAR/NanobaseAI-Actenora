/**
 * Bounded context: Template.
 * Public API package: com.nanobaseai.actenora.template.api (named interface "api").
 * Internal packages are not accessible from other modules.
 */
@org.springframework.modulith.ApplicationModule(
    displayName = "Template"
    allowedDependencies = {
        "sharedkernel",
        "tenant :: api"
    }
)
package com.nanobaseai.actenora.template;
