import { useQuery } from "@tanstack/react-query";
import { Link } from "react-router-dom";
import { useApi } from "../api/ApiProvider";
import { queryKeys } from "../api/client";
import { AsyncState, PageHeader } from "../components/ui/AsyncState";
import { useI18n } from "../i18n";

export function DashboardPage() {
  const api = useApi();
  const { t, tb } = useI18n();
  const q = useQuery({ queryKey: queryKeys.dashboard, queryFn: () => api.getDashboard() });

  const status =
    q.isLoading ? "loading" : q.isError ? "error" : !q.data ? "empty" : "ready";

  return (
    <section className="page">
      <PageHeader title={t("dashboard.title")} description={t("dashboard.description")} />
      <AsyncState status={status} error={q.error}>
        {q.data ? (
          <>
            <div className="metric-row" role="list">
              <Metric
                label={t("dashboard.pendingApprovals")}
                value={q.data.pendingApprovals}
                to="/decisions"
              />
              <Metric label={t("dashboard.openActions")} value={q.data.openActions} to="/actions" />
              <Metric
                label={t("dashboard.openCommitments")}
                value={q.data.overdueCommitments}
                to="/commitments"
              />
              <Metric label={t("dashboard.runningJobs")} value={q.data.runningJobs} to="/jobs" />
            </div>
            <h2 className="section-title">{t("dashboard.recentMeetings")}</h2>
            <ul className="plain-list">
              {q.data.recentMeetings.map((m) => (
                <li key={m.id}>
                  <Link to={`/meetings/${m.id}`}>{m.title}</Link>
                  <span className="muted"> · {tb("meetingStatus", m.status)}</span>
                </li>
              ))}
            </ul>
          </>
        ) : null}
      </AsyncState>
    </section>
  );
}

function Metric({ label, value, to }: { label: string; value: number; to: string }) {
  const inner = (
    <>
      <span className="metric-value">{value}</span>
      <span className="metric-label">{label}</span>
    </>
  );

  if (to) {
    return (
      <Link to={to} className="metric metric-link" role="listitem">
        {inner}
      </Link>
    );
  }

  return (
    <div className="metric" role="listitem">
      {inner}
    </div>
  );
}
