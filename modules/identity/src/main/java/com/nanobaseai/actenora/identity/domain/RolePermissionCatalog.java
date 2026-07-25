package com.nanobaseai.actenora.identity.domain;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Role → permission matrix. Roles are not 1:1 with a single permission.
 */
public final class RolePermissionCatalog {

    private static final Map<SystemRole, Set<Permission>> MATRIX = new EnumMap<>(SystemRole.class);

    static {
        MATRIX.put(SystemRole.SUPER_ADMIN, EnumSet.allOf(Permission.class));
        MATRIX.put(SystemRole.TENANT_ADMIN, EnumSet.of(
                Permission.TENANT_READ,
                Permission.TENANT_ADMINISTER,
                Permission.USER_READ,
                Permission.USER_ADMINISTER,
                Permission.MEETING_READ,
                Permission.MEETING_WRITE,
                Permission.MEETING_OWN,
                Permission.APPROVAL_DECIDE,
                Permission.AUDIT_READ,
                Permission.TEMPLATE_MANAGE,
                Permission.DELIVERY_MANAGE,
                Permission.MODEL_CONTROL,
                Permission.POLICY_ADMINISTER
        ));
        MATRIX.put(SystemRole.MEETING_OWNER, EnumSet.of(
                Permission.TENANT_READ,
                Permission.USER_READ,
                Permission.MEETING_READ,
                Permission.MEETING_WRITE,
                Permission.MEETING_OWN,
                Permission.APPROVAL_DECIDE,
                Permission.TEMPLATE_MANAGE
        ));
        MATRIX.put(SystemRole.APPROVER, EnumSet.of(
                Permission.TENANT_READ,
                Permission.MEETING_READ,
                Permission.APPROVAL_DECIDE
        ));
        MATRIX.put(SystemRole.PARTICIPANT, EnumSet.of(
                Permission.TENANT_READ,
                Permission.MEETING_READ,
                Permission.MEETING_WRITE
        ));
        MATRIX.put(SystemRole.AUDITOR, EnumSet.of(
                Permission.TENANT_READ,
                Permission.USER_READ,
                Permission.MEETING_READ,
                Permission.AUDIT_READ
        ));
        MATRIX.put(SystemRole.OPERATIONS, EnumSet.of(
                Permission.TENANT_READ,
                Permission.MEETING_READ,
                Permission.OPERATIONS_MANAGE,
                Permission.MODEL_CONTROL,
                Permission.AUDIT_READ,
                Permission.POLICY_ADMINISTER
        ));
    }

    private RolePermissionCatalog() {
    }

    public static Set<Permission> permissionsFor(SystemRole role) {
        return EnumSet.copyOf(MATRIX.getOrDefault(role, EnumSet.noneOf(Permission.class)));
    }

    public static Set<Permission> permissionsFor(Set<SystemRole> roles) {
        EnumSet<Permission> permissions = EnumSet.noneOf(Permission.class);
        for (SystemRole role : roles) {
            permissions.addAll(permissionsFor(role));
        }
        return permissions;
    }

    public static Map<SystemRole, Set<Permission>> matrix() {
        Map<SystemRole, Set<Permission>> copy = new EnumMap<>(SystemRole.class);
        MATRIX.forEach((role, perms) -> copy.put(role, EnumSet.copyOf(perms)));
        return copy;
    }
}
