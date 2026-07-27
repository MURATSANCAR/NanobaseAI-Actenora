import { useState, type ReactNode } from "react";
import {
  Calendar,
  ChevronDown,
  GitBranch,
  ShieldCheck,
  Sparkles,
  Users,
} from "lucide-react";
import type { ApprovalRecord, MeetingDetailResponse } from "@/api/types";
import { ParticipantAttendanceRow } from "@/components/meeting/ParticipantAttendanceRow";
import { StatusBadge } from "@/components/qa/StatusBadge";
import { PRODUCT_BRAND } from "@/config/brand";
import { useI18n } from "@/i18n";
import { formatMeetingVersionLabel } from "@/lib/brandSanitize";

export function MeetingHeaderBar({ detail }: { detail: MeetingDetailResponse }) {
  const { t, tb } = useI18n();
  const [expanded, setExpanded] = useState(true);
  const m = detail.meeting;
  const attendedCount = detail.participants.filter((p) =>
    ["JOINED", "LEFT"].includes((p.attendanceStatus || "").toUpperCase()),
  ).length;

  return (
    <header className="meeting-detail-shell">
      <div className="meeting-detail-aurora" aria-hidden />
      <div className="meeting-detail-orb meeting-detail-orb--a" aria-hidden />
      <div className="meeting-detail-orb meeting-detail-orb--b" aria-hidden />

      <div className="relative z-10 space-y-5 p-4 sm:p-6">
        <div className="flex flex-wrap items-start justify-between gap-4">
          <div className="min-w-0 flex-1">
            <p className="meeting-detail-brand">
              <span className="meeting-detail-brand-mark" aria-hidden>
                <Sparkles className="h-3.5 w-3.5" />
              </span>
              {PRODUCT_BRAND}
              <span className="meeting-detail-live-pill">
                <span className="module-hero-live-dot" aria-hidden />
                {t("meeting.detailLivePulse")}
              </span>
            </p>
            <h1 className="meeting-detail-title">{m.title}</h1>
            <div className="mt-3 flex flex-wrap items-center gap-2">
              <StatusBadge label={tb("meetingStatus", m.status)} status={m.status} />
              <span className="inline-flex items-center gap-1.5 rounded-full bg-white/75 px-2.5 py-1 text-xs font-medium text-slate-700 ring-1 ring-violet-200/70">
                <Calendar className="h-3.5 w-3.5 text-violet-500" aria-hidden />
                {new Date(m.scheduledStartAt).toLocaleString()}
              </span>
              {detail.seriesTitle ? (
                <span className="rounded-full bg-violet-100/90 px-2.5 py-1 text-xs font-semibold text-violet-800 ring-1 ring-violet-200/80">
                  {detail.seriesTitle}
                </span>
              ) : null}
              {detail.businessContext ? (
                <span className="rounded-full bg-sky-100/90 px-2.5 py-1 text-xs font-semibold text-sky-900 ring-1 ring-sky-200/80">
                  {detail.businessContext}
                </span>
              ) : null}
            </div>
          </div>

          <button
            type="button"
            className="btn-secondary shrink-0 px-3 py-2 text-xs shadow-sm"
            aria-expanded={expanded}
            onClick={() => setExpanded((v) => !v)}
          >
            {expanded ? t("meeting.detailsHide") : t("meeting.detailsToggle")}
            <ChevronDown
              className={["h-4 w-4 transition", expanded ? "rotate-180" : ""].join(" ")}
              aria-hidden
            />
          </button>
        </div>

        <div className="meeting-detail-stat-grid">
          <StatChip
            tone="violet"
            icon={Users}
            label={t("meeting.participants")}
            value={String(detail.participants.length)}
            hint={t("meeting.attendedCount", { count: attendedCount })}
            pulse
          />
          <StatChip
            tone="emerald"
            icon={GitBranch}
            label={t("meeting.versions")}
            value={String(detail.versions.length)}
            hint={
              detail.versions[0]
                ? formatMeetingVersionLabel(detail.versions[0].version, detail.versions[0].label)
                : "—"
            }
          />
          <StatChip
            tone="amber"
            icon={ShieldCheck}
            label={t("meeting.approvalHistory")}
            value={String(detail.approvalHistory.length)}
            hint={
              detail.approvalHistory[0]
                ? tb("approvalStatus", detail.approvalHistory[0].status)
                : t("meeting.noApprovals")
            }
            pulse={detail.approvalHistory.some((a) => a.status === "PENDING")}
          />
        </div>

        {expanded ? (
          <div className="meeting-detail-mosaic">
            <DetailCard
              tone="violet"
              title={t("meeting.participants")}
              icon={Users}
              count={detail.participants.length}
              pulse
            >
              {detail.participants.length ? (
                <ul className="space-y-1.5 text-sm">
                  {detail.participants.map((p) => (
                    <ParticipantAttendanceRow key={p.id} participant={p} meetingStatus={m.status} />
                  ))}
                </ul>
              ) : (
                <p className="text-sm text-slate-500">—</p>
              )}
            </DetailCard>

            <DetailCard
              tone="emerald"
              title={t("meeting.versions")}
              icon={GitBranch}
              count={detail.versions.length}
            >
              {detail.versions.length ? (
                <ul className="space-y-1.5 text-sm text-slate-700">
                  {detail.versions.map((v) => (
                    <li
                      key={v.version}
                      className="rounded-xl border border-emerald-100/80 bg-white/80 px-3 py-2 shadow-sm"
                    >
                      <span className="font-semibold text-emerald-900">
                        {formatMeetingVersionLabel(v.version, v.label)}
                      </span>
                      <span className="mt-0.5 block text-xs text-slate-500">
                        {new Date(v.createdAt).toLocaleString()}
                      </span>
                    </li>
                  ))}
                </ul>
              ) : (
                <p className="text-sm text-slate-500">—</p>
              )}
            </DetailCard>

            <DetailCard
              tone="amber"
              title={t("meeting.approvalHistory")}
              icon={ShieldCheck}
              count={detail.approvalHistory.length}
              pulse={detail.approvalHistory.some((a) => a.status === "PENDING")}
            >
              <ApprovalList items={detail.approvalHistory} />
            </DetailCard>
          </div>
        ) : null}
      </div>
    </header>
  );
}

function StatChip({
  tone,
  icon: Icon,
  label,
  value,
  hint,
  pulse,
}: {
  tone: "violet" | "emerald" | "amber";
  icon: typeof Users;
  label: string;
  value: string;
  hint: string;
  pulse?: boolean;
}) {
  return (
    <div className={["meeting-detail-stat", `meeting-detail-stat--${tone}`].join(" ")}>
      <span className={["meeting-detail-stat-icon", pulse ? "meeting-detail-pulse" : ""].join(" ")}>
        <Icon className="h-4 w-4" aria-hidden />
      </span>
      <div className="min-w-0">
        <p className="text-[10px] font-bold uppercase tracking-[0.14em] text-slate-500">{label}</p>
        <p className="font-display text-2xl font-semibold tracking-tight text-slate-950">{value}</p>
        <p className="truncate text-[11px] text-slate-600">{hint}</p>
      </div>
    </div>
  );
}

function DetailCard({
  tone,
  title,
  icon: Icon,
  count,
  pulse,
  children,
}: {
  tone: "violet" | "emerald" | "amber";
  title: string;
  icon: typeof Users;
  count: number;
  pulse?: boolean;
  children: ReactNode;
}) {
  return (
    <section className={["meeting-detail-card", `meeting-detail-card--${tone}`].join(" ")}>
      <div className="mb-3 flex items-center gap-2">
        <span className={["meeting-detail-card-icon", pulse ? "meeting-detail-pulse" : ""].join(" ")}>
          <Icon className="h-4 w-4" aria-hidden />
        </span>
        <h2 className="font-display text-sm font-semibold tracking-tight text-slate-900">{title}</h2>
        <span className="ml-auto rounded-full bg-white/80 px-2 py-0.5 text-[11px] font-bold text-slate-700 ring-1 ring-black/5">
          {count}
        </span>
        {pulse ? <span className="module-hero-live-dot" aria-hidden /> : null}
      </div>
      {children}
    </section>
  );
}

function ApprovalList({ items }: { items: ApprovalRecord[] }) {
  const { t, tb } = useI18n();
  if (!items.length) return <p className="text-sm text-slate-500">{t("meeting.noApprovals")}</p>;
  return (
    <ul className="space-y-1.5 text-sm">
      {items.map((a) => {
        const pending = a.status === "PENDING";
        return (
          <li
            key={a.id}
            className={[
              "rounded-xl border px-3 py-2",
              pending
                ? "border-amber-300/90 bg-amber-50/90 shadow-sm meeting-detail-pulse-border"
                : "border-amber-100/80 bg-white/80",
            ].join(" ")}
          >
            <span className="font-medium text-slate-800">
              {tb("artifactType", a.artifactType)} · {tb("approvalStatus", a.status)}
            </span>
            {a.decidedBy ? <span className="text-slate-500"> · {a.decidedBy}</span> : null}
          </li>
        );
      })}
    </ul>
  );
}
