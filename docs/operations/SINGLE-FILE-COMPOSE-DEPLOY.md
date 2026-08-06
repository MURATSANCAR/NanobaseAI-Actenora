# SINGLE-FILE-COMPOSE-DEPLOY

Deploy the full Actenora stack on one host with a single compose file:
[`infrastructure/compose/docker-compose.prod.yml`](../../infrastructure/compose/docker-compose.prod.yml).

This is the "everything in one Docker, cleanly" path — one file, one env-file, one
command. It runs each component as its own hardened container (not a fat image).
The 35B LLM stays **external** by default; opt into an in-compose vLLM with `--profile gpu`
only on a GPU host.

> Alternative: the layered [`docker-compose.prod-like.yml`](../../infrastructure/compose/docker-compose.prod-like.yml)
> + [`docker-compose.portal-server.override.yml`](../../infrastructure/compose/docker-compose.portal-server.override.yml)
> stack still works. Pick one model as primary; don't run both against the same volumes.

## 0. Prerequisites

- Docker Engine + compose plugin on the host.
- DNS `portal.nanobase.ai` → host; nginx + certbot installed.
- A running OpenAI-compatible LLM endpoint (llama-server / vLLM) reachable from the
  backend container — default `http://host.docker.internal:8010`. See
  [`MODEL-POOL-RUNBOOK.md`](MODEL-POOL-RUNBOOK.md).
- An OpenAI-compatible **embedding** endpoint (hash embeddings are forbidden in prod).
- Entra app registrations (API + SPA) and Graph app permissions with admin consent —
  see [`PORTAL-MSAL-RUNBOOK.md`](PORTAL-MSAL-RUNBOOK.md) and [`GRAPH-SANDBOX-RUNBOOK.md`](GRAPH-SANDBOX-RUNBOOK.md).

## 1. Secrets & env

```bash
cp infrastructure/compose/.env.prod.example /etc/nanobaseai/actenora.prod.env
chmod 600 /etc/nanobaseai/actenora.prod.env
```

Fill every `(REQUIRED)` value. Cross-check against [`PROD-SECRETS-CHECKLIST.md`](PROD-SECRETS-CHECKLIST.md).
No `*_change_me` / `actenora_local` values — `ProductionSecretGuard` refuses to boot with them.

**Graph certificate (CERTIFICATE auth mode):** place the PEM pair where the backend
mounts them read-only:

```bash
mkdir -p infrastructure/compose/secrets/graph
cp cert.pem key.pem infrastructure/compose/secrets/graph/   # 0600
```

(For `CLIENT_SECRET` mode instead: set `ACTENORA_MICROSOFT_GRAPH_AUTH_MODE=CLIENT_SECRET`
and `ACTENORA_MICROSOFT_GRAPH_CLIENT_SECRET`; the cert dir can stay empty.)

## 2. Dry-run validation (no build, no start)

```bash
docker compose -f infrastructure/compose/docker-compose.prod.yml \
  --env-file /etc/nanobaseai/actenora.prod.env config >/dev/null && echo OK
```

This fails loudly if any `(REQUIRED)` var is empty or the file is malformed. Do this first.

## 3. Bring up

```bash
make prod-up ENV_FILE=/etc/nanobaseai/actenora.prod.env
# with AI health sidecar + in-compose GPU LLM:
make prod-up ENV_FILE=/etc/nanobaseai/actenora.prod.env PROFILES="--profile ai --profile gpu"
```

`make prod-up` = `docker compose -f docker-compose.prod.yml --env-file <ENV_FILE> up -d --build`.
Flyway migrations run automatically on backend start (`ddl-auto: none`).

If you enable `--profile gpu`, also set `ACTENORA_AI_PROVIDER_BASE_URL=http://vllm:8000`
and `VLLM_MODEL` in the env-file.

## 4. Verify

```bash
make prod-ps  ENV_FILE=/etc/nanobaseai/actenora.prod.env    # all healthy?
make prod-logs ENV_FILE=/etc/nanobaseai/actenora.prod.env   # watch backend boot

curl -fsS http://127.0.0.1:8088/actuator/health | grep -q UP && echo backend-UP
curl -fsS http://127.0.0.1:3000/ | grep -q html && echo portal-UP
```

## 5. nginx + TLS

```bash
sudo cp infrastructure/compose/nginx/actenora-prod.conf /etc/nginx/sites-available/actenora
sudo ln -sf /etc/nginx/sites-available/actenora /etc/nginx/sites-enabled/actenora
sudo certbot --nginx -d portal.nanobase.ai     # if no cert yet
sudo nginx -t && sudo systemctl reload nginx
```

The config proxies `/api/` → backend (`:8088`) and `/` → portal (`:3000`), sets
`client_max_body_size 32m` for VTT upload, and long proxy timeouts for AI paths.

## 6. Smoke test (product loop)

1. Open `https://portal.nanobase.ai`, sign in via Entra (MSAL redirect).
2. Confirm dashboard / meeting list load (no CORS errors in console).
3. Trigger one meeting → transcript → AI draft → approve → delivery, or run the
   staging burn-in per [`GRAPH-SANDBOX-RUNBOOK.md`](GRAPH-SANDBOX-RUNBOOK.md).
4. Register the Graph webhook subscription (operator API under `/api/v1/microsoft/subscriptions`).

## 7. Update / rollback

```bash
# update: rebuild + restart
make prod-up ENV_FILE=/etc/nanobaseai/actenora.prod.env
# stop (keeps volumes/data)
make prod-down ENV_FILE=/etc/nanobaseai/actenora.prod.env
```

Data lives in named volumes (`actenora-prod-*`); `prod-down` does **not** delete them.
For image rollback and DB restore drills see [`ROLLBACK-RUNBOOK.md`](ROLLBACK-RUNBOOK.md)
and [`BACKUP-RESTORE-RUNBOOK.md`](BACKUP-RESTORE-RUNBOOK.md).

## Notes / gotchas

- **Portal env is baked at build time.** Changing any `VITE_*` requires rebuilding the
  web-portal image (`make prod-up` rebuilds).
- **No MailHog.** Delivery goes through Graph Mail.Send; the SMTP health probe is disabled
  (`MANAGEMENT_HEALTH_MAIL_ENABLED=false`).
- **Data services are not exposed to the host** (internal networks only); backend and
  portal bind to `127.0.0.1` behind nginx.
- **Provenance:** ensure the running images are built from the intended commit — verify
  digests after `up`; a stale image is the classic "my fix didn't apply" trap.
