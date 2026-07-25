package com.nanobaseai.actenora.identity.domain;

/**
 * Fine-grained permissions. Roles map to permission sets via {@link RolePermissionCatalog}.
 */
public enum Permission {
    TENANT_READ,
    TENANT_ADMINISTER,
    USER_READ,
    USER_ADMINISTER,
    MEETING_READ,
    MEETING_WRITE,
    MEETING_OWN,
    APPROVAL_DECIDE,
    AUDIT_READ,
    OPERATIONS_MANAGE,
    MODEL_CONTROL,
    TEMPLATE_MANAGE,
    DELIVERY_MANAGE,
    POLICY_ADMINISTER;

    public String code() {
        return name();
    }

    public static Permission fromCode(String code) {
        return Permission.valueOf(code);
    }
}
