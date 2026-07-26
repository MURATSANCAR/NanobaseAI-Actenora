import type { Configuration } from "@azure/msal-browser";
import { resolvePortalAuthMode } from "@/auth/portalAuthMode";

function portalBasePath(): string {
  const base = import.meta.env.BASE ?? "/";
  return base.endsWith("/") ? base : `${base}/`;
}

export function msalRedirectUri(): string {
  const base = portalBasePath();
  const origin = typeof window !== "undefined" ? window.location.origin : "";
  return `${origin}${base}`.replace(/\/$/, "") || origin;
}

export function isMsalAuthEnabled(env?: Partial<ImportMetaEnv>): boolean {
  return resolvePortalAuthMode(env) === "msal";
}

export function buildMsalConfig(env?: Partial<ImportMetaEnv>): Configuration | null {
  const meta = env ?? (import.meta.env as ImportMetaEnv);
  if (!isMsalAuthEnabled(meta)) return null;

  const clientId = meta.VITE_ENTRA_CLIENT_ID;
  const tenantId = meta.VITE_ENTRA_TENANT_ID ?? "common";
  if (!clientId) {
    console.warn("[actenora] VITE_ENTRA_CLIENT_ID is required when VITE_PORTAL_AUTH_MODE=msal");
    return null;
  }

  return {
    auth: {
      clientId,
      authority: `https://login.microsoftonline.com/${tenantId}`,
      redirectUri: msalRedirectUri(),
      postLogoutRedirectUri: msalRedirectUri(),
      navigateToLoginRequestUrl: true,
    },
    cache: {
      cacheLocation: "localStorage",
      storeAuthStateInCookie: false,
    },
  };
}

export function msalApiScopes(env?: Partial<ImportMetaEnv>): string[] {
  const meta = env ?? (import.meta.env as ImportMetaEnv);
  const scope = meta.VITE_ENTRA_API_SCOPE;
  if (!scope) {
    console.warn("[actenora] VITE_ENTRA_API_SCOPE is required when VITE_PORTAL_AUTH_MODE=msal");
    return [];
  }
  return [scope];
}
