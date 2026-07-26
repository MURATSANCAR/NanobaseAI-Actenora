export type PortalAuthMode = "headers" | "msal";

export function resolvePortalAuthMode(env?: Partial<ImportMetaEnv>): PortalAuthMode {
  const meta = env ?? ((typeof import.meta !== "undefined" ? import.meta.env : undefined) as
    | ImportMetaEnv
    | undefined);
  const raw = (meta?.VITE_PORTAL_AUTH_MODE ?? "headers").trim().toLowerCase();
  if (raw === "msal") return "msal";
  // "mock" accepted as legacy alias for headers IdP (real operator identity, not fake data)
  return "headers";
}
