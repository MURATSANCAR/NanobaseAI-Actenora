import type { Configuration } from "@azure/msal-browser";
import { resolvePortalAuthMode } from "@/auth/portalAuthMode";
import { PRODUCT_BRAND } from "@/config/brand";

function portalBasePath(env?: Partial<ImportMetaEnv>): string {
  const meta = env ?? ((typeof import.meta !== "undefined" ? import.meta.env : {}) as ImportMetaEnv);
  const base = meta.BASE ?? "/";
  return base.endsWith("/") ? base : `${base}/`;
}

export function msalRedirectUri(env?: Partial<ImportMetaEnv>): string {
  const base = portalBasePath(env);
  const origin = typeof window !== "undefined" ? window.location.origin : "http://localhost";
  return `${origin}${base}`.replace(/\/$/, "") || origin;
}

export function isMsalAuthEnabled(env?: Partial<ImportMetaEnv>): boolean {
  return resolvePortalAuthMode(env) === "msal";
}

export class MsalConfigError extends Error {
  constructor(message: string) {
    super(message);
    this.name = "MsalConfigError";
  }
}

/**
 * Fail-closed MSAL config. When portal auth mode is msal, missing Entra
 * client ID / API scope is a hard error (no silent degrade to mock).
 */
export function buildMsalConfig(env?: Partial<ImportMetaEnv>): Configuration | null {
  const meta = env ?? (import.meta.env as ImportMetaEnv);
  if (!isMsalAuthEnabled(meta)) return null;

  const clientId = meta.VITE_ENTRA_CLIENT_ID?.trim();
  const tenantId = meta.VITE_ENTRA_TENANT_ID?.trim() || "common";
  if (!clientId) {
    throw new MsalConfigError(
      `${PRODUCT_BRAND} sign-in is misconfigured: VITE_ENTRA_CLIENT_ID is required when VITE_PORTAL_AUTH_MODE=msal`,
    );
  }

  return {
    auth: {
      clientId,
      authority: `https://login.microsoftonline.com/${tenantId}`,
      redirectUri: msalRedirectUri(meta),
      postLogoutRedirectUri: msalRedirectUri(meta),
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
  if (!isMsalAuthEnabled(meta)) return [];

  const scope = meta.VITE_ENTRA_API_SCOPE?.trim();
  if (!scope) {
    throw new MsalConfigError(
      `${PRODUCT_BRAND} sign-in is misconfigured: VITE_ENTRA_API_SCOPE is required when VITE_PORTAL_AUTH_MODE=msal`,
    );
  }
  return [scope];
}
