package com.nanobaseai.actenora.security.portal;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * FAZ 33 — maps backend {@code Permission} codes to portal UI permission strings
 * used by {@code apps/web-portal} nav/gates.
 */
public final class PortalPermissionMapper {

    private PortalPermissionMapper() {
    }

    public static List<String> toPortalPermissions(Set<String> backendPermissionCodes) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        if (backendPermissionCodes == null || backendPermissionCodes.isEmpty()) {
            return List.of();
        }
        Set<String> codes = normalize(backendPermissionCodes);
        if (codes.contains("MEETING_READ")) {
            out.add("meetings:read");
        }
        if (codes.contains("MEETING_WRITE")) {
            out.add("meetings:edit");
            out.add("notes:private:read_own");
        }
        if (codes.contains("APPROVAL_DECIDE")) {
            out.add("approvals:decide");
        }
        if (codes.contains("MODEL_CONTROL")) {
            out.add("models:view");
            out.add("models:admin");
            out.add("models:routing_detail");
        }
        if (codes.contains("OPERATIONS_MANAGE")) {
            out.add("operations:view");
        }
        if (codes.contains("AUDIT_READ")) {
            out.add("audit:view");
        }
        if (codes.contains("TEMPLATE_MANAGE")) {
            out.add("templates:edit");
        }
        if (codes.contains("TENANT_ADMINISTER")
                || codes.contains("DELIVERY_MANAGE")
                || codes.contains("POLICY_ADMINISTER")) {
            out.add("teams:settings");
        }
        if (codes.contains("USER_ADMINISTER") || codes.contains("TENANT_ADMINISTER")) {
            out.add("notes:private:read_any");
        }
        // SUPER_ADMIN has every backend permission; ensure full portal surface.
        if (codes.containsAll(Set.of(
                "MEETING_READ", "MEETING_WRITE", "APPROVAL_DECIDE", "MODEL_CONTROL",
                "OPERATIONS_MANAGE", "AUDIT_READ", "TEMPLATE_MANAGE"
        ))) {
            out.add("notes:private:read_any");
            out.add("teams:settings");
        }
        return List.copyOf(out);
    }

    public static String toPortalRole(Set<String> backendRoleCodes) {
        if (backendRoleCodes == null || backendRoleCodes.isEmpty()) {
            return "VIEWER";
        }
        Set<String> roles = normalize(backendRoleCodes);
        if (roles.contains("SUPER_ADMIN") || roles.contains("TENANT_ADMIN")) {
            return "ADMIN";
        }
        if (roles.contains("OPERATIONS")) {
            return "OPERATIONS";
        }
        if (roles.contains("APPROVER")) {
            return "APPROVER";
        }
        if (roles.contains("MEETING_OWNER") || roles.contains("PARTICIPANT")) {
            return "MEMBER";
        }
        if (roles.contains("AUDITOR")) {
            return "VIEWER";
        }
        return "VIEWER";
    }

    private static Set<String> normalize(Set<String> codes) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (String code : codes) {
            if (code != null && !code.isBlank()) {
                out.add(code.trim().toUpperCase(Locale.ROOT));
            }
        }
        return out;
    }

    /** Visible for tests — keep empty collections immutable. */
    static List<String> copy(List<String> in) {
        return new ArrayList<>(in);
    }
}
