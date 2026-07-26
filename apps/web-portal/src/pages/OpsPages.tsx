import { useQuery } from "@tanstack/react-query";
import { useMemo, useState } from "react";
import { Navigate } from "react-router-dom";
import { useApi } from "@/api/ApiProvider";
import { queryKeys } from "@/api/client";
import { useAuth } from "@/auth/AuthProvider";
import { MetricCard } from "@/components/qa/MetricCard";
import { PageShell } from "@/components/qa/PageShell";
import { StatusBadge } from "@/components/qa/StatusBadge";
import { AsyncState, DataTable, PaginationBar } from "@/components/ui/AsyncState";
import { useI18n } from "@/i18n";
import { AlertTriangle, Layers } from "lucide-react";

export function TemplateStudioPage() {
  const api = useApi();
  const { t, tb } = useI18n();
  const q = useQuery({ queryKey: queryKeys.templates, queryFn: () => api.listTemplates() });
  const status =
    q.isLoading ? "loading" : q.isError ? "error" : !q.data?.items.length ? "empty" : "ready";

  return (
    <PageShell titleKey="templates.title" subtitleKey="templates.description" maxWidth="max-w-7xl">
      <AsyncState status={status} error={q.error} emptyTitle={t("async.empty")} emptyDescription={t("templates.description")}>
        <DataTable
          headers={["Name", "Locale", t("filter.status")]}
          rows={
            q.data?.items.map((item) => [
              item.name,
              item.locale,
              <StatusBadge key={item.id} label={tb("artifactStatus", item.status)} status={item.status} />,
            ]) ?? []
          }
        />
      </AsyncState>
    </PageShell>
  );
}

function asyncStatusFromQuery(loading: boolean, error: boolean, empty: boolean) {
  return loading ? "loading" : error ? "error" : empty ? "empty" : "ready";
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
    <PageShell titleKey="teams.title" subtitleKey="teams.description" maxWidth="max-w-4xl">
      <AsyncState status={status} error={q.error}>
        {q.data ? (
          <div className="card-static grid gap-4 p-5 sm:grid-cols-2">
            <div>
              <p className="label-text">{t("teams.tenantConnected")}</p>
              <p className="text-sm font-semibold text-slate-800">
                {q.data.tenantConnected ? t("common.yes") : t("common.no")}
              </p>
            </div>
            <div>
              <p className="label-text">{t("teams.graphApp")}</p>
              <p className="break-all text-sm font-semibold text-slate-800">{q.data.graphAppId}</p>
            </div>
            <div>
              <p className="label-text">{t("teams.webhook")}</p>
              <StatusBadge label={tb("webhookStatus", q.data.webhookStatus)} status={q.data.webhookStatus} />
            </div>
            <div>
              <p className="label-text">{t("teams.autoJoin")}</p>
              <p className="text-sm font-semibold text-slate-800">
                {q.data.autoJoinEnabled ? t("common.enabled") : t("common.disabled")}
              </p>
            </div>
          </div>
        ) : null}
      </AsyncState>
    </PageShell>
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
    <PageShell titleKey="models.title" subtitleKey="models.description" maxWidth="max-w-7xl">
      <AsyncState status={status} error={q.error}>
        {q.data ? (
          <>
            <div className="space-y-3">
              <h2 className="text-sm font-bold uppercase tracking-wide text-slate-500">{t("models.sectionModels")}</h2>
              <DataTable
                headers={["Name", "Key", t("filter.status")]}
                rows={q.data.models.map((m) => [
                  m.displayName,
                  <span key={m.modelKey} className="font-mono text-xs text-slate-500">{m.modelKey}</span>,
                  <StatusBadge
                    key={`${m.modelKey}-s`}
                    label={`${m.enabled ? t("models.enabled") : t("models.disabled")} · ${tb("artifactStatus", m.status)}`}
                    status={m.status}
                  />,
                ])}
              />
            </div>
            <div className="space-y-3">
              <h2 className="text-sm font-bold uppercase tracking-wide text-slate-500">{t("models.sectionDeployments")}</h2>
              <DataTable
                headers={["Deployment", "Node", "Health"]}
                rows={q.data.deployments.map((d) => [
                  d.deploymentKey,
                  d.nodeName,
                  <StatusBadge
                    key={d.deploymentKey}
                    label={d.healthy ? t("common.healthy") : t("common.unhealthy")}
                    status={d.healthy ? "healthy" : "failed"}
                  />,
                ])}
              />
            </div>
            {auth.canSeeRouting ? (
              <div className="card-static space-y-3 p-5" data-testid="routing-detail">
                <h2 className="text-sm font-bold uppercase tracking-wide text-slate-500">{t("models.sectionRouting")}</h2>
                <p className="text-sm text-slate-600">
                  {t("models.strategy")}: <strong>{q.data.routing.strategy}</strong>
                </p>
                <DataTable
                  headers={["Role", "Primary", t("models.fallback")]}
                  rows={q.data.routing.roles.map((r) => [r.role, r.primaryModel, r.fallbackModel])}
                />
              </div>
            ) : (
              <p className="card-static p-4 text-sm text-slate-500" data-testid="routing-hidden">
                {t("models.routingHidden")}
              </p>
            )}
          </>
        ) : null}
      </AsyncState>
    </PageShell>
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
  const status = asyncStatusFromQuery(q.isLoading, q.isError, !q.data?.items.length);

  return (
    <PageShell titleKey="jobs.title" subtitleKey="jobs.description" maxWidth="max-w-7xl">
      <AsyncState status={status} error={q.error} emptyTitle={t("async.empty")} emptyDescription={t("jobs.description")}>
        <div className="card-static divide-y divide-white/60">
          {q.data?.items.map((j) => (
            <div key={j.id} className="flex flex-wrap items-center gap-2 px-4 py-3 text-sm">
              <strong className="text-slate-800">{j.stage}</strong>
              <StatusBadge label={tb("artifactStatus", j.status)} status={j.status} />
              <span className="text-slate-500">
                {t("jobs.started")} {new Date(j.startedAt).toLocaleString()}
              </span>
            </div>
          ))}
        </div>
        <PaginationBar
          nextCursor={q.data?.nextCursor}
          onNext={() => setCursor(q.data?.nextCursor ?? undefined)}
          onReset={() => setCursor(undefined)}
        />
      </AsyncState>
    </PageShell>
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
    <PageShell titleKey="operations.title" subtitleKey="operations.description" maxWidth="max-w-7xl">
      <AsyncState status={status} error={q.error}>
        {q.data ? (
          <>
            <div className="grid gap-4 sm:grid-cols-2">
              <MetricCard label={t("operations.queueDepth")} value={q.data.queueDepth} icon={Layers} />
              <MetricCard
                label={t("operations.failedJobs")}
                value={q.data.failedJobs}
                icon={AlertTriangle}
                tone="text-status-fail"
              />
            </div>
            <div className="card-static p-5">
              <h2 className="mb-3 text-sm font-bold uppercase tracking-wide text-slate-500">
                {t("operations.circuitBreakers")}
              </h2>
              <ul className="space-y-2 text-sm">
                {q.data.circuitBreakers.map((c) => (
                  <li key={c.name} className="rounded-xl bg-white/50 px-3 py-2">
                    {c.name}: <span className="font-medium">{c.state}</span>
                  </li>
                ))}
              </ul>
            </div>
            <div className="card-static p-5">
              <h2 className="mb-3 text-sm font-bold uppercase tracking-wide text-slate-500">
                {t("operations.workers")}
              </h2>
              <ul className="space-y-2 text-sm">
                {q.data.workers.map((w) => (
                  <li key={w.name} className="rounded-xl bg-white/50 px-3 py-2">
                    {w.name}: <span className="font-medium">{w.status}</span>
                  </li>
                ))}
              </ul>
            </div>
          </>
        ) : null}
      </AsyncState>
    </PageShell>
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

  const status = asyncStatusFromQuery(q.isLoading, q.isError, !q.data?.items.length);

  return (
    <PageShell titleKey="audit.title" subtitleKey="audit.description" maxWidth="max-w-7xl">
      <AsyncState status={status} error={q.error} emptyTitle={t("async.empty")} emptyDescription={t("audit.description")}>
        <DataTable
          headers={["Action", "Actor", "Resource", "At"]}
          rows={
            q.data?.items.map((e) => [
              e.action,
              e.actor,
              <span key={`${e.id}-r`} className="font-mono text-xs text-slate-500">
                {e.resourceType}/{e.resourceId}
              </span>,
              <span key={`${e.id}-a`} className="text-slate-500">
                {new Date(e.at).toLocaleString()}
              </span>,
            ]) ?? []
          }
        />
        <PaginationBar
          nextCursor={q.data?.nextCursor}
          onNext={() => setCursor(q.data?.nextCursor ?? undefined)}
          onReset={() => setCursor(undefined)}
        />
      </AsyncState>
    </PageShell>
  );
}
