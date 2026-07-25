import { useQuery } from "@tanstack/react-query";
import { Link } from "react-router-dom";
import { useApi } from "../api/ApiProvider";
import { queryKeys } from "../api/client";
import { AsyncState, PageHeader } from "../components/ui/AsyncState";

export function DashboardPage() {
  const api = useApi();
  const q = useQuery({ queryKey: queryKeys.dashboard, queryFn: () => api.getDashboard() });

  const status =
    q.isLoading ? "loading" : q.isError ? "error" : !q.data ? "empty" : "ready";

  return (
    <section className="page">
      <PageHeader
        title="Dashboard"
        description="Approvals, actions, commitments, and recent meetings at a glance."
      />
      <AsyncState status={status} error={q.error}>
        {q.data ? (
          <>
            <div className="metric-row" role="list">
              <Metric label="Pending approvals" value={q.data.pendingApprovals} />
              <Metric label="Open actions" value={q.data.openActions} />
              <Metric label="Open commitments" value={q.data.overdueCommitments} />
              <Metric label="Running AI jobs" value={q.data.runningJobs} />
            </div>
            <h2 className="section-title">Recent meetings</h2>
            <ul className="plain-list">
              {q.data.recentMeetings.map((m) => (
                <li key={m.id}>
                  <Link to={`/meetings/${m.id}`}>{m.title}</Link>
                  <span className="muted"> · {m.status}</span>
                </li>
              ))}
            </ul>
          </>
        ) : null}
      </AsyncState>
    </section>
  );
}

function Metric({ label, value }: { label: string; value: number }) {
  return (
    <div className="metric" role="listitem">
      <span className="metric-value">{value}</span>
      <span className="metric-label">{label}</span>
    </div>
  );
}
