export type PortalAuthMode = "mock" | "msal";

export function resolvePortalAuthMode(env?: Partial<ImportMetaEnv>): PortalAuthMode {
  const meta = env ?? ((typeof import.meta !== "undefined" ? import.meta.env : undefined) as
    | ImportMetaEnv
    | undefined);
  const raw = meta?.VITE_PORTAL_AUTH_MODE ?? "mock";
  return raw === "msal" ? "msal" : "mock";
}
