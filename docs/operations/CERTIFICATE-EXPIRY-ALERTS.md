# CERTIFICATE-EXPIRY-ALERTS

**Status:** Active (FAZ 27)

## What to monitor

| Certificate | Typical location | Alert window |
|-------------|------------------|--------------|
| Ingress / public TLS | Load balancer / cert-manager | 30d, 14d, 7d, 3d, 1d |
| Microsoft Graph app (certificate auth) | Entra app registration / K8s secret | 45d, 30d, 14d |
| Internal service mTLS (if enabled) | Mesh / SPIFFE | 14d, 7d |
| Object storage TLS (custom CA) | MinIO / gateway | 30d, 14d |

## Probe (example)

```bash
# Expiry epoch for a host:port
echo | openssl s_client -servername "$HOST" -connect "$HOST:443" 2>/dev/null \
  | openssl x509 -noout -enddate
```

Automate via:

- cert-manager `Certificate` Ready condition + Prometheus `certmanager_certificate_expiration_timestamp_seconds`
- Synthetic blackbox TLS probe
- Weekly ops job writing to `artifacts/security/cert-expiry.txt` in CI for non-prod hosts

## Alert routing

| Severity | Condition | Action |
|----------|-----------|--------|
| Warning | ≤ 30 days | Ticket to platform team |
| High | ≤ 14 days | Page secondary on-call |
| Critical | ≤ 3 days or failed handshake | Page primary; emergency rotate |

## Rotation checklist (Graph client cert)

1. Generate new keypair offline; store private key only in Vault/K8s secret.
2. Upload public cert to Entra app registration **before** removing old.
3. Roll platform-backend pods to pick up new secret.
4. Confirm Graph token acquisition succeeds.
5. Revoke old certificate after overlap window (≥ 24h).

## Non-goals

- Do not commit certificates or private keys to git (secret scan enforces).
- Do not extend signed URL TTL as a substitute for TLS health.
