import { identityAuthHeaders } from "@/api/client";
import { resolvePortalAuthMode } from "@/auth/portalAuthMode";

type AuthHeaderProvider = () => Promise<Record<string, string>>;

let msalHeaderProvider: AuthHeaderProvider | null = null;

export function setMsalAuthHeaderProvider(provider: AuthHeaderProvider | null): void {
  msalHeaderProvider = provider;
}

export async function resolveAuthHeaders(env?: Partial<ImportMetaEnv>): Promise<Record<string, string>> {
  const mode = resolvePortalAuthMode(env);
  if (mode === "msal") {
    if (!msalHeaderProvider) return {};
    return msalHeaderProvider();
  }
  return identityAuthHeaders(env);
}
