import { useQuery } from "@tanstack/react-query";
import { createContext, useContext, type ReactNode } from "react";
import { queryKeys } from "../api/client";
import { useApi } from "../api/ApiProvider";
import type { PortalUser } from "../api/types";
import {
  canAccessPrivateNote,
  canDecideApprovals,
  canManageModels,
  canViewModelRouting,
  hasPermission,
  navVisible,
} from "./permissions";
import type { Permission } from "../api/types";

interface AuthContextValue {
  user: PortalUser | undefined;
  isLoading: boolean;
  error: Error | null;
  can: (permission: Permission) => boolean;
  canSeePrivateNote: (authorId: string) => boolean;
  canApprove: boolean;
  canAdminModels: boolean;
  canSeeRouting: boolean;
  nav: (item: "models" | "operations" | "audit" | "teams" | "templates") => boolean;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const api = useApi();
  const query = useQuery({
    queryKey: queryKeys.me,
    queryFn: () => api.getCurrentUser(),
  });

  const user = query.data;
  const permissions = user?.permissions ?? [];

  const value: AuthContextValue = {
    user,
    isLoading: query.isLoading,
    error: query.error,
    can: (p) => hasPermission(permissions, p),
    canSeePrivateNote: (authorId) =>
      user ? canAccessPrivateNote(permissions, authorId, user.id) : false,
    canApprove: canDecideApprovals(permissions),
    canAdminModels: canManageModels(permissions),
    canSeeRouting: canViewModelRouting(permissions),
    nav: (item) => navVisible(permissions, item),
  };

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error("useAuth requires AuthProvider");
  return ctx;
}
