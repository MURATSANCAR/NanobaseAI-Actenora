import type { Permission, PortalRole } from "../api/types";

const ROLE_PERMISSIONS: Record<PortalRole, Permission[]> = {
  VIEWER: ["meetings:read", "models:view"],
  MEMBER: [
    "meetings:read",
    "meetings:edit",
    "notes:private:read_own",
    "models:view",
    "templates:edit",
  ],
  APPROVER: [
    "meetings:read",
    "meetings:edit",
    "notes:private:read_own",
    "approvals:decide",
    "models:view",
    "templates:edit",
  ],
  ADMIN: [
    "meetings:read",
    "meetings:edit",
    "notes:private:read_own",
    "notes:private:read_any",
    "approvals:decide",
    "models:view",
    "models:admin",
    "models:routing_detail",
    "operations:view",
    "audit:view",
    "templates:edit",
    "teams:settings",
  ],
  OPERATIONS: [
    "meetings:read",
    "models:view",
    "models:admin",
    "models:routing_detail",
    "operations:view",
    "audit:view",
    "teams:settings",
  ],
};

export function permissionsForRole(role: PortalRole): Permission[] {
  return [...ROLE_PERMISSIONS[role]];
}

export function hasPermission(
  permissions: readonly Permission[],
  required: Permission,
): boolean {
  return permissions.includes(required);
}

export function canAccessPrivateNote(
  permissions: readonly Permission[],
  noteAuthorId: string,
  currentUserId: string,
): boolean {
  if (hasPermission(permissions, "notes:private:read_any")) {
    return true;
  }
  if (hasPermission(permissions, "notes:private:read_own")) {
    return noteAuthorId === currentUserId;
  }
  return false;
}

export function canViewModelRouting(permissions: readonly Permission[]): boolean {
  return hasPermission(permissions, "models:routing_detail");
}

export function canManageModels(permissions: readonly Permission[]): boolean {
  return hasPermission(permissions, "models:admin");
}

export function canDecideApprovals(permissions: readonly Permission[]): boolean {
  return hasPermission(permissions, "approvals:decide");
}

export function navVisible(
  permissions: readonly Permission[],
  item: "models" | "operations" | "audit" | "teams" | "templates" | "approvals",
): boolean {
  switch (item) {
    case "approvals":
      return canDecideApprovals(permissions);
    case "models":
      return hasPermission(permissions, "models:view");
    case "operations":
      return hasPermission(permissions, "operations:view");
    case "audit":
      return hasPermission(permissions, "audit:view");
    case "teams":
      return hasPermission(permissions, "teams:settings");
    case "templates":
      return hasPermission(permissions, "templates:edit") || hasPermission(permissions, "meetings:read");
    default: {
      const _exhaustive: never = item;
      return _exhaustive;
    }
  }
}
