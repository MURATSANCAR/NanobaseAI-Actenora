/**
 * Bounded context: Meeting.
 * Public API package: com.nanobaseai.actenora.meeting.api (named interface "api").
 * Internal packages are not accessible from other modules.
 */
@org.springframework.modulith.ApplicationModule(
    displayName = "Meeting",
    allowedDependencies = {
        "sharedkernel",
        "tenant :: api",
        "microsoftconnection :: api"
    }
)
package com.nanobaseai.actenora.meeting;
