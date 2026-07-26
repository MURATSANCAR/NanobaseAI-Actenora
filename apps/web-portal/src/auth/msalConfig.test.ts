import assert from "node:assert/strict";
import test from "node:test";
import { buildMsalConfig, isMsalAuthEnabled, msalApiScopes, MsalConfigError } from "./msalConfig.ts";

test("msal mode requires client id", () => {
  assert.equal(isMsalAuthEnabled({ VITE_PORTAL_AUTH_MODE: "msal" }), true);
  assert.throws(
    () =>
      buildMsalConfig({
        VITE_PORTAL_AUTH_MODE: "msal",
        VITE_ENTRA_CLIENT_ID: "",
      }),
    (err: unknown) => err instanceof MsalConfigError,
  );
});

test("msal mode requires api scope", () => {
  assert.throws(
    () =>
      msalApiScopes({
        VITE_PORTAL_AUTH_MODE: "msal",
        VITE_ENTRA_API_SCOPE: "",
      }),
    (err: unknown) => err instanceof MsalConfigError,
  );
});

test("mock mode does not require entra settings", () => {
  assert.equal(isMsalAuthEnabled({ VITE_PORTAL_AUTH_MODE: "headers" }), false);
  assert.equal(buildMsalConfig({ VITE_PORTAL_AUTH_MODE: "headers" }), null);
  assert.deepEqual(msalApiScopes({ VITE_PORTAL_AUTH_MODE: "headers" }), []);
});

test("msal config builds when complete", () => {
  const config = buildMsalConfig({
    VITE_PORTAL_AUTH_MODE: "msal",
    VITE_ENTRA_CLIENT_ID: "11111111-1111-1111-1111-111111111111",
    VITE_ENTRA_TENANT_ID: "22222222-2222-2222-2222-222222222222",
  });
  assert.ok(config);
  assert.equal(config.auth.clientId, "11111111-1111-1111-1111-111111111111");
  assert.match(config.auth.authority!, /22222222-2222-2222-2222-222222222222/);
  assert.deepEqual(
    msalApiScopes({
      VITE_PORTAL_AUTH_MODE: "msal",
      VITE_ENTRA_API_SCOPE: "api://actenora/access_as_user",
    }),
    ["api://actenora/access_as_user"],
  );
});
