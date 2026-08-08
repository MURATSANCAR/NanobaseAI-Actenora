# CUSTOMER-DOCKER-BUNDLE

Ship EasyMeeting (Actenora) to a customer as **one Docker bundle** — pre-built
images + a single compose file + install script. The customer never builds from
source (no JDK/Maven/Node), which also removes the build-provenance trap where a
stale locally-built image silently differs from the intended commit.

The **LLM stays external** (customer-provided OpenAI-compatible endpoint). Everything
else runs in-bundle: data plane (postgres/redis/rabbitmq/minio) + platform-backend +
web-portal + teams-meeting-app, and the optional `ai` orchestrator sidecar.

> `transcript-worker` is NOT a separate container — transcript extraction runs
> **embedded inside platform-backend** (`ACTENORA_TRANSCRIPT_MODE=embedded`).

## Files

| Path | Role |
|------|------|
| [`infrastructure/compose/docker-compose.customer.yml`](../../infrastructure/compose/docker-compose.customer.yml) | image-based compose (no `build:`) |
| [`scripts/package/build-bundle.sh`](../../scripts/package/build-bundle.sh) | build images + assemble the shippable tar |
| [`scripts/package/templates/install.sh`](../../scripts/package/templates/install.sh) | customer-side: `docker load` + `up` |
| [`scripts/package/templates/actenora.env.example`](../../scripts/package/templates/actenora.env.example) | customer env template |
| [`scripts/package/templates/README-INSTALL.md`](../../scripts/package/templates/README-INSTALL.md) | customer install guide (TR) |

## A. Build the bundle (our side)

Requires a working Docker daemon on the build host.

```bash
# Offline tar bundle (primary). Bake the customer's SPA config via --env-file.
make bundle BUNDLE_TAG=v1.0.0 BUNDLE_ENV=/path/to/customer.env
# → dist/actenora-bundle-v1.0.0.tar.gz  (app images + base images + compose + install.sh)
```

What it does:
1. Builds 4 app images from source: `platform-backend`, `web-portal`
   (with the customer's `VITE_*` baked in), `teams-meeting-app`, `ai-orchestrator`.
2. Pulls the pinned third-party images (postgres/redis/rabbitmq/minio/curl/mc).
3. `docker save`s everything into `images/actenora-images-<tag>.tar`.
4. Stages the compose, the mounted config trees (`infrastructure/{postgres,rabbitmq,redis,minio,nginx}`),
   the env template, `install.sh`, and a `BUNDLE-INFO.txt` provenance stamp.
5. Packs `dist/actenora-bundle-<tag>.tar.gz`.

### Registry mode (instead of offline tar)

```bash
scripts/package/build-bundle.sh --tag v1.0.0 --registry your.registry/nanobaseai/ --push
```
Ship only the (small) bundle; the customer sets `ACTENORA_IMAGE_REGISTRY` +
`ACTENORA_IMAGE_TAG` in `actenora.env` and runs `./install.sh --pull`.

> **web-portal is customer-specific.** Its Entra/API config (`VITE_*`) is baked at
> build time. Build one bundle per customer with their `--env-file`. (A future
> runtime-config change would make one image serve all customers.)

## B. Install (customer side)

Shipped `README-INSTALL.md` has the full TR walkthrough. Short version:

```bash
tar -xzf actenora-bundle-v1.0.0.tar.gz -C easymeeting/ && cd easymeeting/
cp actenora.env.example actenora.env && chmod 600 actenora.env   # fill (REQUIRED)
# CERTIFICATE mode: drop cert.pem + key.pem into compose/secrets/graph/
./install.sh                 # docker load + validate + up
./install.sh --profile ai    # + orchestrator sidecar
```

Verify: `./install.sh ps`, then `curl -fsS http://127.0.0.1:8088/actuator/health`.

Put nginx + TLS in front (see [`SINGLE-FILE-COMPOSE-DEPLOY.md`](SINGLE-FILE-COMPOSE-DEPLOY.md) §5;
the same nginx config ships under `compose/infrastructure/nginx`).

## C. Connections & test (in-portal)

After boot, the customer configures the external LLM / embedding / Graph endpoints
from **Settings → Connections** in the web-portal and clicks **Test** on each. See
the connection-settings feature (Phase 2) for the backend probe endpoints.

## Update / rollback

- **Update:** ship a new bundle tag → extract → bump `ACTENORA_IMAGE_TAG` → `./install.sh`.
- **Rollback:** keep the previous tag's images loaded; set `ACTENORA_IMAGE_TAG` back and re-run.
- Data lives in named `actenora-*` volumes; `./install.sh down` does not delete them.

## Gotchas

- **Air-gapped:** the offline tar includes base images too; nothing is pulled at
  install time. Use `--skip-thirdparty` only if the customer host can pull them.
- **Provenance:** `BUNDLE-INFO.txt` records the git commit + tag the images came from.
  Verify it matches the intended release before shipping.
- **First boot** ~2 min while Flyway migrations run (`ddl-auto: none`).
