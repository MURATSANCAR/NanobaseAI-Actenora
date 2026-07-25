package com.nanobaseai.actenora.identity.domain;

/**
 * System roles. Permissions are mapped via {@link RolePermissionCatalog}, not 1:1 with roles.
 */
public enum SystemRole {
    SUPER_ADMIN,
    TENANT_ADMIN,
    MEETING_OWNER,
    APPROVER,
    PARTICIPANT,
    AUDITOR,
    OPERATIONS;

    public String code() {
        return name();
    }

    public static SystemRole fromCode(String code) {
        return SystemRole.valueOf(code);
    }
}
