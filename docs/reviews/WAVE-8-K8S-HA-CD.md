# Wave 8 — Kubernetes HA + CD

## Scope

Production-shaped Kubernetes manifests for `platform-backend` (HA, probes, resources, read-only root FS) and a CD workflow skeleton for image build, scan, SBOM, and digest push.

## Kubernetes manifests

| File | Purpose |
|------|---------|
| `infrastructure/kubernetes/platform-backend-deployment.yaml` | 2 replicas, startup/liveness/readiness probes, resource requests/limits, `readOnlyRootFilesystem` |
| `infrastructure/kubernetes/platform-backend-service.yaml` | ClusterIP :8080 |
| `infrastructure/kubernetes/platform-backend-hpa.yaml` | CPU 70% / memory 80%, min 2 max 10 |
| `infrastructure/kubernetes/platform-backend-pdb.yaml` | `minAvailable: 1` |

Apply (example):

```bash
kubectl apply -f infrastructure/kubernetes/platform-backend-service.yaml
kubectl apply -f infrastructure/kubernetes/platform-backend-deployment.yaml
kubectl apply -f infrastructure/kubernetes/platform-backend-hpa.yaml
kubectl apply -f infrastructure/kubernetes/platform-backend-pdb.yaml
```

Secrets/config: deployment expects `Secret`/`ConfigMap` `actenora-platform-backend` (not committed). Mirror keys from `application-prod.yml` env vars.

## NetworkPolicy wiring

Deployment pods carry label `app.kubernetes.io/name: platform-backend`, matching existing policies:

| Policy | Path |
|--------|------|
| Graph + data-plane egress | `infrastructure/k8s/network-policies/platform-graph-egress.yaml` |
| Security context reference | `infrastructure/k8s/security-context-readonly.yaml` |

Apply network policies in the same namespace:

```bash
kubectl apply -f infrastructure/k8s/network-policies/platform-graph-egress.yaml
```

Pod annotation `actenora.io/network-policy: platform-backend-graph-egress` documents the pairing for operators.

## CD workflow

`.github/workflows/cd-images.yml`:

| Step | Status |
|------|--------|
| Build `apps/platform-backend/Dockerfile` | Active |
| Trivy scan (non-blocking) | Placeholder (`exit-code: 0`, `continue-on-error`) |
| CycloneDX SBOM via syft | Active (artifact upload) |
| Registry push | **Disabled** (`if: false`) until secrets configured |

### Required secrets (when enabling push)

| Secret | Purpose |
|--------|---------|
| `REGISTRY_USERNAME` | Registry user (or `github.actor` for ghcr.io) |
| `REGISTRY_PASSWORD` | PAT / `GITHUB_TOKEN` with `packages:write` |

Enable push: set `if: true` on the push steps and configure secrets. Record promoted digests for rollback.

## Rollback

Follow [`docs/operations/ROLLBACK-RUNBOOK.md`](../operations/ROLLBACK-RUNBOOK.md):

1. Redeploy last known-good image digest on `platform-backend` Deployment.
2. Verify `/actuator/health/readiness` and `/api/health`.
3. Watch outbox lag and DLQ depth (see [`SLO-ALERTS.md`](../operations/SLO-ALERTS.md)).

HPA/PDB remain in place during rollback; scale events should not drop below PDB `minAvailable`.

## Exit criteria

- [x] Deployment with 2 replicas, probes, resources, read-only root FS
- [x] Service, HPA, PDB manifests
- [x] NetworkPolicy reference documented
- [x] `cd-images.yml` build + Trivy placeholder + CycloneDX + dry-run push
- [x] Rollback runbook cross-link

## Deferred

- Helm/Kustomize overlay per environment
- ai-orchestrator / web-portal CD jobs
- Blocking Trivy gate on CRITICAL (enable after baseline scan)
- SealedSecrets / ExternalSecrets manifests for `actenora-platform-backend`
