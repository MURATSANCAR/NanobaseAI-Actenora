import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Link } from "react-router-dom";
import {
  Activity,
  ArrowRight,
  Check,
  CheckSquare,
  Clock,
  Handshake,
  Sparkles,
  type LucideIcon,
} from "lucide-react";
import clsx from "clsx";
import { useApi } from "@/api/ApiProvider";
import { queryKeys } from "@/api/client";
import type { DashboardResponse, MeetingSummary } from "@/api/types";
import { useAuth } from "@/auth/AuthProvider";
import { MeetingSummaryList } from "@/components/meeting/MeetingSummaryList";
import { MeetingPrepCard } from "@/components/meeting/MeetingPrepCard";
import { AsyncState } from "@/components/ui/AsyncState";
import { PRODUCT_BRAND } from "@/config/brand";
import { useI18n } from "@/i18n";
import { OnboardingBanner } from "@/pages/OnboardingPage";

export function DashboardPage() {
  const api = useApi();
  const { t } = useI18n();
  const auth = useAuth();
  const q = useQuery({ queryKey: queryKeys.dashboard, queryFn: () => api.getDashboard() });

  const status =
    q.isLoading ? "loading" : q.isError ? "error" : !q.data ? "empty" : "ready";

  const firstName = auth.user?.displayName?.trim().split(/\s+/)[0];
  const greetingName = firstName ? `, ${firstName}` : "";

  return (
    <div className="dashboard-stage mobile-page mx-auto flex w-full min-w-0 max-w-7xl shrink-0 flex-col gap-5 animate-fade-in sm:gap-6">
      <section className="dashboard-hero shrink-0" aria-labelledby="dashboard-hero-title">
        <div className="dashboard-hero-aurora" aria-hidden />
        <div className="dashboard-hero-grid" aria-hidden />
        <div className="dashboard-hero-orb dashboard-hero-orb--a" aria-hidden />
        <div className="dashboard-hero-orb dashboard-hero-orb--b" aria-hidden />

        <div className="relative z-10 flex flex-col gap-6 p-5 sm:p-7 lg:p-8">
          <div className="flex flex-wrap items-start justify-between gap-4">
            <div className="min-w-0 max-w-2xl">
              <p className="dashboard-brand-mark">{PRODUCT_BRAND}</p>
              <h1 id="dashboard-hero-title" className="dashboard-hero-title">
                {t("dashboard.greeting", { name: greetingName })}
              </h1>
              <p className="mt-3 max-w-xl text-sm leading-relaxed text-slate-600 sm:text-base">
                {t("dashboard.heroLead")}
              </p>
            </div>
            <div className="flex flex-wrap items-center gap-2">
              <span className="dashboard-live-pill">
                <span className="module-hero-live-dot" aria-hidden />
                {t("dashboard.livePulse")}
              </span>
              <Link to="/meetings" className="btn-primary dashboard-cta">
                {t("dashboard.openMeetings")}
                <ArrowRight className="h-4 w-4" aria-hidden />
              </Link>
            </div>
          </div>

          <div className="dashboard-hero-rule" aria-hidden />

          <p className="text-[11px] font-semibold uppercase tracking-[0.18em] text-teal-800/80">
            {t("dashboard.metricsHint")}
          </p>
        </div>
      </section>

      <AsyncState status={status} error={q.error}>
        {q.data ? (
          <>
            <OnboardingBanner />
            <MetricStrip data={q.data} />
            <MyWorkDashboardCard />
            <UpcomingMeetingPrep />
            <RecentMeetings
              meetings={q.data.recentMeetings}
              title={t("dashboard.recentMeetings")}
              empty={t("dashboard.recentEmpty")}
              viewAll={t("dashboard.viewAllMeetings")}
            />
          </>
        ) : null}
      </AsyncState>
    </div>
  );
}

function MyWorkDashboardCard() {
  const api = useApi();
  const auth = useAuth();
  const queryClient = useQueryClient();
  const { t } = useI18n();
  const query = useQuery({
    queryKey: queryKeys.myWork,
    queryFn: () => api.getMyWork(),
  });
  const complete = useMutation({
    mutationFn: (actionId: string) => api.completeAction(actionId),
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: queryKeys.myWork }),
        queryClient.invalidateQueries({ queryKey: queryKeys.dashboard }),
        queryClient.invalidateQueries({ queryKey: ["actions"] }),
      ]);
    },
  });

  if (query.isLoading || query.isError || !query.data) return null;
  const attention = [...query.data.overdueActions, ...query.data.dueSoonActions]
    .filter((action, index, all) => all.findIndex((item) => item.id === action.id) === index)
    .slice(0, 5);

  return (
    <section className="card-static overflow-hidden" aria-labelledby="dashboard-my-work-title">
      <div className="flex flex-wrap items-center justify-between gap-3 border-b border-slate-100 px-4 py-3 sm:px-5">
        <div>
          <p className="text-[10px] font-bold uppercase tracking-[0.18em] text-violet-700">
            {t("dashboard.metricsHint")}
          </p>
          <h2 id="dashboard-my-work-title" className="mt-1 font-semibold text-slate-900">
            {t("myWork.title")}
          </h2>
        </div>
        <Link to="/my-work" className="inline-flex items-center gap-1 text-xs font-semibold text-violet-700 hover:text-violet-900">
          {t("myWork.openCenter")}
          <ArrowRight className="h-3.5 w-3.5" />
        </Link>
      </div>
      {attention.length ? (
        <ul className="divide-y divide-slate-100">
          {attention.map((action) => (
            <li key={action.id} className="flex flex-wrap items-center gap-3 px-4 py-3 sm:px-5">
              <Link
                to={`/meetings/${action.meetingId}`}
                className="min-w-0 flex-1 text-sm font-medium text-slate-900 hover:text-violet-800"
              >
                {action.title}
              </Link>
              {action.dueAt ? (
                <span className="text-xs text-slate-500">{new Date(action.dueAt).toLocaleString()}</span>
              ) : null}
              {auth.can("meetings:edit") ? (
                <button
                  type="button"
                  className="btn-primary px-2.5 py-1.5 text-xs"
                  disabled={complete.isPending && complete.variables === action.id}
                  onClick={() => complete.mutate(action.id)}
                >
                  <Check className="h-3.5 w-3.5" />
                  {t("myWork.complete")}
                </button>
              ) : null}
            </li>
          ))}
        </ul>
      ) : (
        <p className="px-4 py-5 text-sm text-slate-500 sm:px-5">{t("myWork.noAttention")}</p>
      )}
    </section>
  );
}

function UpcomingMeetingPrep() {
  const api = useApi();
  const { t } = useI18n();
  const params = { status: "SCHEDULED" as const, limit: 20 };
  const query = useQuery({
    queryKey: queryKeys.meetings(params),
    queryFn: () => api.listMeetings(params),
  });
  const next = query.data?.items
    .filter((meeting) => meeting.status === "SCHEDULED")
    .filter((meeting) => new Date(meeting.scheduledStartAt).getTime() >= Date.now())
    .sort(
      (left, right) =>
        new Date(left.scheduledStartAt).getTime() - new Date(right.scheduledStartAt).getTime(),
    )[0];
  if (!next) return null;

  return (
    <section className="space-y-3" aria-label={t("meetingPrep.upcoming")}>
      <div className="flex flex-wrap items-end justify-between gap-2">
        <div>
          <p className="text-[10px] font-bold uppercase tracking-[0.18em] text-violet-700">
            {t("meetingPrep.upcoming")}
          </p>
          <Link to={`/meetings/${next.id}`} className="font-semibold text-slate-900 hover:text-violet-800">
            {next.title}
          </Link>
        </div>
        <span className="text-xs text-slate-500">{new Date(next.scheduledStartAt).toLocaleString()}</span>
      </div>
      <MeetingPrepCard meetingId={next.id} compact />
    </section>
  );
}

function MetricStrip({ data }: { data: DashboardResponse }) {
  const { t } = useI18n();
  const items: Array<{
    label: string;
    value: number;
    icon: LucideIcon;
    to: string;
    accent: string;
  }> = [
    {
      label: t("dashboard.pendingApprovals"),
      value: data.pendingApprovals,
      icon: Clock,
      to: "/approvals",
      accent: "dashboard-metric--warn",
    },
    {
      label: t("dashboard.openActions"),
      value: data.openActions,
      icon: CheckSquare,
      to: "/actions",
      accent: "dashboard-metric--violet",
    },
    {
      label: t("dashboard.overdueCommitments"),
      value: data.overdueCommitments,
      icon: Handshake,
      to: "/commitments",
      accent: "dashboard-metric--teal",
    },
    {
      label: t("dashboard.runningJobs"),
      value: data.runningJobs,
      icon: Activity,
      to: "/models",
      accent: "dashboard-metric--sky",
    },
  ];

  return (
    <div className="grid shrink-0 gap-3 sm:grid-cols-2 xl:grid-cols-4">
      {items.map((item, index) => (
        <Link
          key={item.to}
          to={item.to}
          className={clsx("group dashboard-metric", item.accent)}
          style={{ animationDelay: `${80 + index * 70}ms` }}
        >
          <div className="flex items-start justify-between gap-3">
            <span className="text-[11px] font-semibold uppercase tracking-wider text-slate-500">
              {item.label}
            </span>
            <span className="dashboard-metric-icon" aria-hidden>
              <item.icon className="h-4 w-4" />
            </span>
          </div>
          <div className="dashboard-metric-value">{item.value}</div>
          <span className="dashboard-metric-go">
            <Sparkles className="h-3.5 w-3.5" aria-hidden />
            <ArrowRight className="h-3.5 w-3.5 transition group-hover:translate-x-0.5" aria-hidden />
          </span>
        </Link>
      ))}
    </div>
  );
}

function RecentMeetings({
  meetings,
  title,
  empty,
  viewAll,
}: {
  meetings: MeetingSummary[];
  title: string;
  empty: string;
  viewAll: string;
}) {
  return (
    <section className="dashboard-meetings shrink-0" aria-labelledby="dashboard-meetings-title">
      <div className="mb-4 flex flex-wrap items-end justify-between gap-3">
        <div>
          <h2 id="dashboard-meetings-title" className="font-display text-lg font-semibold tracking-tight text-slate-900">
            {title}
          </h2>
        </div>
        <Link to="/meetings" className="inline-flex items-center gap-1 text-sm font-semibold text-teal-800 hover:text-teal-950">
          {viewAll}
          <ArrowRight className="h-3.5 w-3.5" aria-hidden />
        </Link>
      </div>

      <MeetingSummaryList meetings={meetings} ariaLabel={title} empty={empty} />
    </section>
  );
}
