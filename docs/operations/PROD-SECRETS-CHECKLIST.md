# Production secrets checklist

Required before Gate 11 / live staging with `prod` profile (without `prod-fixture`). `ProductionSecretGuard` refuses empty or local-default values.

## Must set (guarded at boot)

| Variable / property | Notes |
|---------------------|--------|
| `POSTGRES_PASSWORD` | Not `actenora_local` / `changeme` |
| `RABBITMQ_PASSWORD` | Same |
| `OBJECT_STORAGE_SECRET_KEY` | Not `actenora_minio` |
| `ACTENORA_MICROSOFT_GRAPH_CLIENT_STATE` | Not `local-graph-client-state` |
| `ACTENORA_DELIVERY_WEBHOOK_SECRET` | Not `local-delivery-webhook-secret` |
| `ACTENORA_PORTAL_LINK_SECRET` | Not `actenora-local-portal-secret` |
| `spring.mail.host` | No localhost / mailhog on strict prod |

## Graph CERTIFICATE path (staging burn-in / prod)

| Variable | Notes |
|----------|--------|
| `ACTENORA_MICROSOFT_GRAPH_AUTH_MODE` | `CERTIFICATE` on prod |
| `ACTENORA_MICROSOFT_GRAPH_TENANT_ID` | Entra tenant |
| `ACTENORA_MICROSOFT_GRAPH_CLIENT_ID` | Graph app |
| `ACTENORA_MICROSOFT_GRAPH_CERTIFICATE_PEM_PATH` | Mounted PEM |
| `ACTENORA_MICROSOFT_GRAPH_PRIVATE_KEY_PEM_PATH` | Mounted PEM |
| `ACTENORA_MICROSOFT_GRAPH_DEFAULT_MAILBOX_USER_ID` | Transcript/calendar caller |

## Auth / AI (not all in ProductionSecretGuard)

| Variable | Required value |
|----------|----------------|
| `ACTENORA_AUTH_MODE` | `entra` |
| `ACTENORA_PORTAL_AUTH_MODE` | `msal` |
| `ACTENORA_ENTRA_ISSUER_URI` / `ACTENORA_ENTRA_AUDIENCE` | Live tenant |
| `ACTENORA_CORS_ALLOWED_ORIGINS` | Portal origin(s) |
| `ACTENORA_AI_PROVIDER_KIND` | **≠** `mock` (`LocalProviderFactory` refuses mock on strict prod) |
| `ACTENORA_AI_PROVIDER_BASE_URL` | NanobaseAI local intelligence endpoint |

## Verify

```bash
# Boot with prod profile; expect "Production secret guard passed"
# and PortalAuthModeGuard silent success.
```

Fixture CI may use `prod,prod-fixture` with mock auth and MailHog — that path is intentionally exempt from strict mail/auth rules.
