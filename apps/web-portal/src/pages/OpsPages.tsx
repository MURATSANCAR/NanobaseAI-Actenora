import { useQuery } from "@tanstack/react-query";
import { useMemo, useState } from "react";
import { Navigate } from "react-router-dom";
import { useApi } from "../api/ApiProvider";
import { queryKeys } from "../api/client";
import { useAuth } from "../auth/AuthProvider";
import { AsyncState, PageHeader, PaginationBar } from "../components/ui/AsyncState";

export function TemplateStudioPage() {
  const api = useApi();
  const q = useQuery({ queryKey: queryKeys.templates, queryFn: () => api.listTemplates() });
  const status =
    q.isLoading ? "loading" : q.isError ? "error" : !q.data?.items.length ? "empty" : "ready";

  return (
    <section className="page">
      <PageHeader title="Template Studio" description="Delivery and note templates." />
      <AsyncState status={status} error={q.error}>
        <ul className="data-table">
          {q.data?.items.map((t) => (
            <li key={t.id} className="data-row">
              <span>{t.name}</span>
              <span>{t.locale}</span>
              <span>
                v{t.version} · {t.status}
              </span>
            </li>
          ))}
        </ul>
      </AsyncState>
    </section>
  );
}

export function TeamsSettingsPage() {
  const auth = useAuth();
  const api = useApi();
  const q = useQuery({
    queryKey: queryKeys.teams,
    queryFn: () => api.getTeamsSettings(),
    enabled: auth.nav("teams"),
  });

  if (!auth.isLoading && !auth.nav("teams")) {
    return <Navigate to="/" replace />;
  }

  const status = q.isLoading ? "loading" : q.isError ? "error" : !q.data ? "empty" : "ready";

  return (
    <section className="page">
      <PageHeader title="Teams Settings" description="Graph connection and webhook health." />
      <AsyncState status={status} error={q.error}>
        {q.data ? (
          <dl className="meta-list">
            <div>
              <dt>Tenant connected</dt>
              <dd>{q.data.tenantConnected ? "Yes" : "No"}</dd>
            </div>
            <div>
              <dt>Graph app</dt>
              <dd>{q.data.graphAppId}</dd>
            </div>
            <div>
              <dt>Webhook</dt>
              <dd>{q.data.webhookStatus}</dd>
            </div>
            <div>
              <dt>Auto-join</dt>
              <dd>{q.data.autoJoinEnabled ? "Enabled" : "Disabled"}</dd>
            </div>
          </dl>
        ) : null}
      </AsyncState>
    </section>
  );
}

export function ModelManagementPage() {
  const auth = useAuth();
  const api = useApi();
  const q = useQuery({
    queryKey: queryKeys.models,
    queryFn: () => api.getModelHealth(),
    enabled: auth.nav("models"),
  });

  if (!auth.isLoading && !auth.nav("models")) {
    return <Navigate to="/" replace />;
  }

  const status = q.isLoading ? "loading" : q.isError ? "error" : !q.data ? "empty" : "ready";

  return (
    <section className="page">
      <PageHeader
        title="Model Management"
        description="Registry health. Routing strategy is admin-only."
      />
      <AsyncState status={status} error={q.error}>
        {q.data ? (
          <>
            <h2 className="section-title">Models</h2>
            <ul className="data-table">
              {q.data.models.map((m) => (
                <li key={m.modelKey} className="data-row">
                  <span>{m.displayName}</span>
                  <span>{m.modelKey}</span>
                  <span>
                    {m.enabled ? "enabled" : "disabled"} · {m.status}
                  </span>
                </li>
              ))}
            </ul>
            <h2 className="section-title">Deployments</h2>
            <ul className="data-table">
              {q.data.deployments.map((d) => (
                <li key={d.deploymentKey} className="data-row">
                  <span>{d.deploymentKey}</span>
                  <span>{d.nodeName}</span>
                  <span>{d.healthy ? "healthy" : "unhealthy"}</span>
                </li>
              ))}
            </ul>
            {auth.canSeeRouting ? (
              <div data-testid="routing-detail">
                <h2 className="section-title">Routing detail</h2>
                <p className="muted">Strategy: {q.data.routing.strategy}</p>
                <ul className="data-table">
                  {q.data.routing.roles.map((r) => (
                    <li key={r.role} className="data-row">
                      <span>{r.role}</span>
                      <span>{r.primaryModel}</span>
                      <span className="muted">fallback {r.fallbackModel}</span>
                    </li>
                  ))}
                </ul>
                {auth.canAdminModels ? (
                  <p className="muted">Admin controls available for enable / drain / register.</p>
                ) : null}
              </div>
            ) : (
              <p className="muted" data-testid="routing-hidden">
                Model routing detail is restricted to admin / operations.
              </p>
            )}
          </>
        ) : null}
      </AsyncState>
    </section>
  );
}

export function AiJobTimelinePage() {
  const api = useApi();
  const [cursor, setCursor] = useState<string | undefined>();
  const params = useMemo(() => ({ cursor, limit: 25 }), [cursor]);
  const q = useQuery({
    queryKey: queryKeys.jobs(params),
    queryFn: () => api.listAiJobs(params),
  });
  const status =
    q.isLoading ? "loading" : q.isError ? "error" : !q.data?.items.length ? "empty" : "ready";

  return (
    <section className="page">
      <PageHeader title="AI Job Timeline" description="Pipeline stages for meeting intelligence jobs." />
      <AsyncState status={status} error={q.error}>
        <ol className="timeline">
          {q.data?.items.map((j) => (
            <li key={j.id}>
              <strong>{j.stage}</strong> · {j.status}
              <span className="muted">
                {" "}
                · started {new Date(j.startedAt).toLocaleString()}
              </span>
            </li>
          ))}
        </ol>
        <PaginationBar
          nextCursor={q.data?.nextCursor}
          onNext={() => setCursor(q.data?.nextCursor ?? undefined)}
          onReset={() => setCursor(undefined)}
        />
      </AsyncState>
    </section>
  );
}

export function OperationsCenterPage() {
  const auth = useAuth();
  const api = useApi();
  const q = useQuery({
    queryKey: queryKeys.operations,
    queryFn: () => api.getOperationsOverview(),
    enabled: auth.nav("operations"),
  });

  if (!auth.isLoading && !auth.nav("operations")) {
    return <Navigate to="/" replace />;
  }

  const status = q.isLoading ? "loading" : q.isError ? "error" : !q.data ? "empty" : "ready";

  return (
    <section className="page">
      <PageHeader title="Operations Center" description="Queues, breakers, and workers." />
      <AsyncState status={status} error={q.error}>
        {q.data ? (
          <>
            <div className="metric-row">
              <div className="metric">
                <span className="metric-value">{q.data.queueDepth}</span>
                <span className="metric-label">Queue depth</span>
              </div>
              <div className="metric">
                <span className="metric-value">{q.data.failedJobs}</span>
                <span className="metric-label">Failed jobs</span>
              </div>
            </div>
            <h2 className="section-title">Circuit breakers</h2>
            <ul className="plain-list">
              {q.data.circuitBreakers.map((c) => (
                <li key={c.name}>
                  {c.name}: {c.state}
                </li>
              ))}
            </ul>
            <h2 className="section-title">Workers</h2>
            <ul className="plain-list">
              {q.data.workers.map((w) => (
                <li key={w.name}>
                  {w.name}: {w.status}
                </li>
              ))}
            </ul>
          </>
        ) : null}
      </AsyncState>
    </section>
  );
}

export function AuditViewerPage() {
  const auth = useAuth();
  const api = useApi();
  const [cursor, setCursor] = useState<string | undefined>();
  const params = useMemo(() => ({ cursor, limit: 25 }), [cursor]);
  const q = useQuery({
    queryKey: queryKeys.audit(params),
    queryFn: () => api.listAuditEvents(params),
    enabled: auth.nav("audit"),
  });

  if (!auth.isLoading && !auth.nav("audit")) {
    return <Navigate to="/" replace />;
  }

  const status =
    q.isLoading ? "loading" : q.isError ? "error" : !q.data?.items.length ? "empty" : "ready";

  return (
    <section className="page">
      <PageHeader title="Audit Viewer" description="Append-only operational audit trail." />
      <AsyncState status={status} error={q.error}>
        <ul className="data-table">
          {q.data?.items.map((e) => (
            <li key={e.id} className="data-row">
              <span>{e.action}</span>
              <span>{e.actor}</span>
              <span className="muted">
                {e.resourceType}/{e.resourceId}
              </span>
              <span className="muted">{new Date(e.at).toLocaleString()}</span>
            </li>
          ))}
        </ul>
        <PaginationBar
          nextCursor={q.data?.nextCursor}
          onNext={() => setCursor(q.data?.nextCursor ?? undefined)}
          onReset={() => setCursor(undefined)}
        />
      </AsyncState>
    </section>
  );
}
