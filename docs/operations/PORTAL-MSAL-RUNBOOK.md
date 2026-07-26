# Portal Entra MSAL Runbook

End-to-end NanobaseAI portal sign-in: SPA MSAL Bearer → platform API with `ACTENORA_AUTH_MODE=entra`. **No `X-Mock-*` headers in staging/prod.**

## App registrations (Entra)

1. **API app** (platform-backend resource server)
   - Expose an application ID URI / scope, e.g. `api://actenora/access_as_user`
   - Audience = `ACTENORA_ENTRA_AUDIENCE`
   - Issuer = `ACTENORA_ENTRA_ISSUER_URI` (`https://login.microsoftonline.com/{tenant}/v2.0`)

2. **SPA app** (web-portal)
   - Platform: Single-page application
   - Redirect URI: portal origin + base path (must match `msalRedirectUri()`)
   - API permission: delegated scope from the API app
   - Client ID → `VITE_ENTRA_CLIENT_ID`
   - Tenant ID → `VITE_ENTRA_TENANT_ID` (prefer single-tenant over `common` in prod)

## Backend env (staging/prod)

```bash
ACTENORA_AUTH_MODE=entra
ACTENORA_PORTAL_AUTH_MODE=msal
ACTENORA_ENTRA_ISSUER_URI=https://login.microsoftonline.com/{tenant-id}/v2.0
ACTENORA_ENTRA_AUDIENCE=api://actenora
ACTENORA_CORS_ALLOWED_ORIGINS=https://portal.example.com
```

Guards:

- `PlatformSecurityConfiguration` refuses `auth.mode=mock` on strict prod
- `PortalAuthModeGuard` refuses `portal.auth.mode=msal` without `security.auth.mode=entra`, and requires both on strict prod
- CORS allowlists `X-Mock-*` **only** when `auth.mode=mock`

## Portal build args

```bash
VITE_PORTAL_AUTH_MODE=msal
VITE_ENTRA_CLIENT_ID=...
VITE_ENTRA_TENANT_ID=...
VITE_ENTRA_API_SCOPE=api://actenora/access_as_user
VITE_API_MODE=http
VITE_API_BASE_URL=https://api.example.com
```

Dockerfile fails the build if `msal` is set without client ID / API scope. Do not bake `VITE_MOCK_*` into staging/prod images.

## Smoke checklist

1. Open portal → misconfig screen if Entra vars missing; otherwise Microsoft login
2. After login, Network tab: `Authorization: Bearer …` on `/api/v1/portal/me` — **no** `X-Mock-*`
3. Backend `/api/v1/me` returns mapped Actenora user (tenant bound via Entra `tid`)
4. Cross-tenant meeting access → 403

## Compose note

`docker-compose.prod-like.yml` defaults to mock for CI fixture. For a live MSAL stack override `ACTENORA_AUTH_MODE`, `ACTENORA_PORTAL_AUTH_MODE`, Entra issuer/audience, and portal `VITE_*` build args.
