import { useQuery } from "@tanstack/react-query";
import { Link } from "react-router-dom";
import {
  Activity,
  ArrowRight,
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
import { StatusBadge } from "@/components/qa/StatusBadge";
import { AsyncState } from "@/components/ui/AsyncState";
import { PRODUCT_BRAND } from "@/config/brand";
import { useI18n } from "@/i18n";
import { OnboardingBanner } from "@/pages/OnboardingPage";

export function DashboardPage() {
  const api = useApi();
  const { t, tb } = useI18n();
  const auth = useAuth();
  const q = useQuery({ queryKey: queryKeys.dashboard, queryFn: () => api.getDashboard() });

  const status =
    q.isLoading ? "loading" : q.isError ? "error" : !q.data ? "empty" : "ready";

  const firstName = auth.user?.displayName?.trim().split(/\s+/)[0];
  const greetingName = firstName ? `, ${firstName}` : "";

  return (
    <div className="dashboard-stage mobile-page mx-auto flex w-full min-w-0 max-w-7xl flex-1 flex-col gap-5 animate-fade-in sm:gap-6">
      <section className="dashboard-hero" aria-labelledby="dashboard-hero-title">
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
            <RecentMeetings
              meetings={q.data.recentMeetings}
              title={t("dashboard.recentMeetings")}
              empty={t("dashboard.recentEmpty")}
              viewAll={t("dashboard.viewAllMeetings")}
              statusLabel={(s) => tb("meetingStatus", s)}
            />
          </>
        ) : null}
      </AsyncState>
    </div>
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
      label: t("dashboard.openCommitments"),
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
    <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
      {items.map((item, index) => (
        <Link
          key={item.to}
          to={item.to}
          className={clsx("dashboard-metric", item.accent)}
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
  statusLabel,
}: {
  meetings: MeetingSummary[];
  title: string;
  empty: string;
  viewAll: string;
  statusLabel: (status: string) => string;
}) {
  const { t } = useI18n();
  return (
    <section className="dashboard-meetings" aria-labelledby="dashboard-meetings-title">
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

      {meetings.length === 0 ? (
        <p className="rounded-2xl border border-dashed border-teal-200/80 bg-teal-50/40 px-4 py-8 text-center text-sm text-slate-600">
          {empty}
        </p>
      ) : (
        <ul className="dashboard-meeting-list">
          {meetings.map((m, index) => (
            <li key={m.id} style={{ animationDelay: `${120 + index * 50}ms` }}>
              <Link to={`/meetings/${m.id}`} className="dashboard-meeting-row">
                <span className="dashboard-meeting-index" aria-hidden>
                  {String(index + 1).padStart(2, "0")}
                </span>
                <div className="min-w-0 flex-1">
                  <p className="truncate font-semibold text-slate-900">{m.title}</p>
                  <p className="mt-0.5 text-xs text-slate-500">
                    {new Date(m.scheduledStartAt).toLocaleString()}
                    {m.participantCount > 0
                      ? ` · ${m.participantCount} ${t("common.people")}`
                      : null}
                  </p>
                </div>
                <StatusBadge label={statusLabel(m.status)} status={m.status} />
              </Link>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}
