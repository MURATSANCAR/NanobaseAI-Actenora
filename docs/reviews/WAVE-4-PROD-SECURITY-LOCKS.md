# Wave 4 — Production security locks

## Scope

Fail-closed production boot and runtime guards for secrets, mock providers, Graph webhook auth, and portal mock headers.

## Components

| Component | Change |
|-----------|--------|
| `ProductionSecretGuard` | Also checks Graph `client-state`, delivery webhook secret, portal HMAC secret (when configured), and `spring.mail.host` (no localhost/mailhog on prod) |
| `MicrosoftGraphWebhookController` | Constant-time `clientState` compare (`MessageDigest.isEqual`); blank expected state denies all notifications on prod |
| `DeliveryModuleConfiguration` | MailHog only when `actenora.delivery.mail.provider=mailhog`; Graph mail port when `microsoft-graph` |
| `LocalProviderFactory` | Already refuses `mock` provider on prod (unchanged) |
| `PlatformSecurityConfiguration` | Already refuses `actenora.security.auth.mode=mock` on prod (unchanged) |
| `TenantSecurityContextFilter` | Mock headers extracted only when `AuthMode.MOCK` (unchanged) |

## Forbidden production values

Exact local defaults blocked on `prod` / `production` profiles:

- `local-graph-client-state`
- `local-delivery-webhook-secret`
- `actenora-local-portal-secret`

Also blocked: empty secrets, `_change_me`, `changeme`, `actenora_local`, and mail hosts `localhost`, `127.0.0.1`, `mailhog`, `::1`.

Override for emergency local prod debugging only: `actenora.allow-default-secrets=true` (never in committed config).

## Configuration

```yaml
actenora:
  delivery:
    webhook:
      secret: ${ACTENORA_DELIVERY_WEBHOOK_SECRET}
    portal-link:
      secret: ${ACTENORA_PORTAL_LINK_SECRET}
    mail:
      provider: microsoft-graph   # prod
  microsoft-graph:
    webhook:
      client-state: ${ACTENORA_MICROSOFT_GRAPH_CLIENT_STATE}
spring:
  mail:
    host: ${MAIL_HOST}   # must not be localhost/mailhog on prod
```

## Frontend / docs note

- `apps/web-portal/src/api/client.ts` — `mockAuthHeaders()` are for **non-prod only**; production SPA must use MSAL Bearer tokens (`actenora.portal.auth.mode=msal`).
- Do not send `X-Mock-*` headers against production backends.

## Tests

```bash
mvn -pl apps/platform-backend test -Dtest=ProductionSecretGuardTest,MockAuthProductionGuardTest,MicrosoftGraphWebhookBindingTest
```

## Exit criteria

- [x] `ProductionSecretGuard` expanded for Graph, delivery, portal, mail host
- [x] Graph webhook constant-time compare + prod blank-state deny
- [x] MailHog gated behind `mail.provider=mailhog`
- [x] Mock LLM provider refused on prod (pre-existing)
- [x] Mock auth headers gated to non-prod auth mode
- [x] Unit test for secret guard defaults
