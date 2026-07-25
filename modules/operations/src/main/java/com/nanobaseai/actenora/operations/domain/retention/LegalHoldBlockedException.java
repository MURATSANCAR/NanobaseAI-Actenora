package com.nanobaseai.actenora.operations.domain.retention;

/** Raised when retention deletion is blocked by an active legal hold. */
public final class LegalHoldBlockedException extends RuntimeException {

    private final String resourceType;
    private final String resourceId;

    public LegalHoldBlockedException(String resourceType, String resourceId) {
        super("Legal hold blocks deletion of " + resourceType + "/" + resourceId);
        this.resourceType = resourceType;
        this.resourceId = resourceId;
    }

    public String resourceType() {
        return resourceType;
    }

    public String resourceId() {
        return resourceId;
    }

    public String code() {
        return "LEGAL_HOLD_ACTIVE";
    }
}
