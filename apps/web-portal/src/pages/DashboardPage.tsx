import { useQuery } from "@tanstack/react-query";
import { Link } from "react-router-dom";
import { Activity, CheckSquare, Clock, Handshake } from "lucide-react";
import { useApi } from "@/api/ApiProvider";
import { queryKeys } from "@/api/client";
import { MetricCard } from "@/components/qa/MetricCard";
import { PageShell } from "@/components/qa/PageShell";
import { StatusBadge } from "@/components/qa/StatusBadge";
import { AsyncState } from "@/components/ui/AsyncState";
import { useI18n } from "@/i18n";
import { OnboardingBanner } from "@/pages/OnboardingPage";

export function DashboardPage() {
  const api = useApi();
  const { t, tb } = useI18n();
  const q = useQuery({ queryKey: queryKeys.dashboard, queryFn: () => api.getDashboard() });

  const status =
    q.isLoading ? "loading" : q.isError ? "error" : !q.data ? "empty" : "ready";

  return (
    <PageShell
      titleKey="dashboard.title"
      subtitleKey="dashboard.description"
      heroSize="dashboard"
      maxWidth="max-w-7xl"
    >
      <AsyncState status={status} error={q.error}>
        {q.data ? (
          <>
            <OnboardingBanner />
            <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
              <MetricCard
                label={t("dashboard.pendingApprovals")}
                value={q.data.pendingApprovals}
                icon={Clock}
                tone="text-status-warn"
                delay={0}
                to="/approvals"
              />
              <MetricCard
                label={t("dashboard.openActions")}
                value={q.data.openActions}
                icon={CheckSquare}
                delay={60}
                to="/actions"
              />
              <MetricCard
                label={t("dashboard.openCommitments")}
                value={q.data.overdueCommitments}
                icon={Handshake}
                tone="text-teal-600"
                delay={120}
                to="/commitments"
              />
              <MetricCard
                label={t("dashboard.runningJobs")}
                value={q.data.runningJobs}
                icon={Activity}
                tone="text-status-run"
                delay={180}
                to="/jobs"
              />
            </div>

            <div className="card-static p-4 sm:p-5">
              <h2 className="mb-4 text-sm font-bold uppercase tracking-wide text-slate-500">
                {t("dashboard.recentMeetings")}
              </h2>
              <ul className="space-y-2">
                {q.data.recentMeetings.map((m) => (
                  <li
                    key={m.id}
                    className="flex flex-wrap items-center justify-between gap-2 rounded-xl border border-white/70 bg-white/50 px-3 py-2.5 transition hover:border-violet-200 hover:bg-violet-50/40"
                  >
                    <Link to={`/meetings/${m.id}`} className="font-medium text-violet-800 hover:underline">
                      {m.title}
                    </Link>
                    <StatusBadge label={tb("meetingStatus", m.status)} status={m.status} />
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
