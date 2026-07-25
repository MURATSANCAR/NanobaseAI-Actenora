package com.nanobaseai.actenora.identity.domain;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RolePermissionCatalogTest {

    @Test
    void rolesAreNotOneToOneWithPermissions() {
        Set<Permission> tenantAdmin = RolePermissionCatalog.permissionsFor(SystemRole.TENANT_ADMIN);
        Set<Permission> participant = RolePermissionCatalog.permissionsFor(SystemRole.PARTICIPANT);

        assertTrue(tenantAdmin.contains(Permission.USER_ADMINISTER));
        assertFalse(participant.contains(Permission.USER_ADMINISTER));
        assertTrue(participant.contains(Permission.MEETING_WRITE));
        assertTrue(tenantAdmin.containsAll(EnumSet.of(Permission.TENANT_READ, Permission.APPROVAL_DECIDE)));
    }

    @Test
    void operationsCannotAdministerUsers() {
        Set<Permission> ops = RolePermissionCatalog.permissionsFor(SystemRole.OPERATIONS);
        assertTrue(ops.contains(Permission.OPERATIONS_MANAGE));
        assertFalse(ops.contains(Permission.USER_ADMINISTER));
    }
}
