import { useQuery } from "@tanstack/react-query";
import { useMemo, useState } from "react";
import { Navigate } from "react-router-dom";
import { useApi } from "../api/ApiProvider";
import { queryKeys } from "../api/client";
import { useAuth } from "../auth/AuthProvider";
import { AsyncState, PageHeader, PaginationBar } from "../components/ui/AsyncState";
import { useI18n } from "../i18n";

export function TemplateStudioPage() {
  const api = useApi();
  const { t, tb } = useI18n();
  const q = useQuery({ queryKey: queryKeys.templates, queryFn: () => api.listTemplates() });
  const status =
    q.isLoading ? "loading" : q.isError ? "error" : !q.data?.items.length ? "empty" : "ready";

  return (
    <section className="page">
      <PageHeader title={t("templates.title")} description={t("templates.description")} />
      <AsyncState status={status} error={q.error}>
        <ul className="data-table">
          {q.data?.items.map((item) => (
            <li key={item.id} className="data-row">
              <span>{item.name}</span>
              <span>{item.locale}</span>
              <span>
                v{item.version} · {tb("artifactStatus", item.status)}
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
  const { t, tb } = useI18n();
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
      <PageHeader title={t("teams.title")} description={t("teams.description")} />
      <AsyncState status={status} error={q.error}>
        {q.data ? (
          <dl className="meta-list">
            <div>
              <dt>{t("teams.tenantConnected")}</dt>
              <dd>{q.data.tenantConnected ? t("common.yes") : t("common.no")}</dd>
            </div>
            <div>
              <dt>{t("teams.graphApp")}</dt>
              <dd>{q.data.graphAppId}</dd>
            </div>
            <div>
              <dt>{t("teams.webhook")}</dt>
              <dd>{tb("webhookStatus", q.data.webhookStatus)}</dd>
            </div>
            <div>
              <dt>{t("teams.autoJoin")}</dt>
              <dd>{q.data.autoJoinEnabled ? t("common.enabled") : t("common.disabled")}</dd>
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
  const { t, tb } = useI18n();
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
      <PageHeader title={t("models.title")} description={t("models.description")} />
      <AsyncState status={status} error={q.error}>
        {q.data ? (
          <>
            <h2 className="section-title">{t("models.sectionModels")}</h2>
            <ul className="data-table">
              {q.data.models.map((m) => (
                <li key={m.modelKey} className="data-row">
                  <span>{m.displayName}</span>
                  <span>{m.modelKey}</span>
                  <span>
                    {m.enabled ? t("models.enabled") : t("models.disabled")} ·{" "}
                    {tb("artifactStatus", m.status)}
                  </span>
                </li>
              ))}
            </ul>
            <h2 className="section-title">{t("models.sectionDeployments")}</h2>
            <ul className="data-table">
              {q.data.deployments.map((d) => (
                <li key={d.deploymentKey} className="data-row">
                  <span>{d.deploymentKey}</span>
                  <span>{d.nodeName}</span>
                  <span>{d.healthy ? t("common.healthy") : t("common.unhealthy")}</span>
                </li>
              ))}
            </ul>
            {auth.canSeeRouting ? (
              <div data-testid="routing-detail">
                <h2 className="section-title">{t("models.sectionRouting")}</h2>
                <p className="muted">
                  {t("models.strategy")}: {q.data.routing.strategy}
                </p>
                <ul className="data-table">
                  {q.data.routing.roles.map((r) => (
                    <li key={r.role} className="data-row">
                      <span>{r.role}</span>
                      <span>{r.primaryModel}</span>
                      <span className="muted">
                        {t("models.fallback")} {r.fallbackModel}
                      </span>
                    </li>
                  ))}
                </ul>
              </div>
            ) : (
              <p className="muted" data-testid="routing-hidden">
                {t("models.routingHidden")}
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
  const { t, tb } = useI18n();
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
      <PageHeader title={t("jobs.title")} description={t("jobs.description")} />
      <AsyncState status={status} error={q.error}>
        <ol className="timeline">
          {q.data?.items.map((j) => (
            <li key={j.id}>
              <strong>{j.stage}</strong> · {tb("artifactStatus", j.status)}
              <span className="muted">
                {" "}
                · {t("jobs.started")} {new Date(j.startedAt).toLocaleString()}
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
  const { t } = useI18n();
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
      <PageHeader title={t("operations.title")} description={t("operations.description")} />
      <AsyncState status={status} error={q.error}>
        {q.data ? (
          <>
            <div className="metric-row">
              <div className="metric">
                <span className="metric-value">{q.data.queueDepth}</span>
                <span className="metric-label">{t("operations.queueDepth")}</span>
              </div>
              <div className="metric">
                <span className="metric-value">{q.data.failedJobs}</span>
                <span className="metric-label">{t("operations.failedJobs")}</span>
              </div>
            </div>
            <h2 className="section-title">{t("operations.circuitBreakers")}</h2>
            <ul className="plain-list">
              {q.data.circuitBreakers.map((c) => (
                <li key={c.name}>
                  {c.name}: {c.state}
                </li>
              ))}
            </ul>
            <h2 className="section-title">{t("operations.workers")}</h2>
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
  const { t } = useI18n();
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
      <PageHeader title={t("audit.title")} description={t("audit.description")} />
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
