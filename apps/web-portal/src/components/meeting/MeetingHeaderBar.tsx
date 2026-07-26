import { useState, type ReactNode } from "react";
import { Calendar, ChevronDown, Users } from "lucide-react";
import type { ApprovalRecord, MeetingDetailResponse } from "@/api/types";
import { ParticipantAttendanceRow } from "@/components/meeting/ParticipantAttendanceRow";
import { StatusBadge } from "@/components/qa/StatusBadge";
import { useI18n } from "@/i18n";
import { formatMeetingVersionLabel } from "@/lib/brandSanitize";

export function MeetingHeaderBar({ detail }: { detail: MeetingDetailResponse }) {
  const { t, tb } = useI18n();
  const [expanded, setExpanded] = useState(false);
  const m = detail.meeting;

  return (
    <header className="card-static overflow-hidden">
      <div className="bg-gradient-to-r from-violet-600/10 via-indigo-500/5 to-sky-500/10 px-4 py-4 sm:px-5">
        <div className="flex flex-wrap items-start justify-between gap-3">
          <div className="min-w-0 flex-1">
            <div className="flex flex-wrap items-center gap-2">
              <h1 className="text-xl font-bold tracking-tight text-slate-900 sm:text-2xl">{m.title}</h1>
              <StatusBadge label={tb("meetingStatus", m.status)} status={m.status} />
            </div>
            <div className="mt-2 flex flex-wrap items-center gap-3 text-sm text-slate-600">
              <span className="inline-flex items-center gap-1.5">
                <Calendar className="h-4 w-4 text-violet-500" aria-hidden />
                {new Date(m.scheduledStartAt).toLocaleString()}
              </span>
              <span className="inline-flex items-center gap-1.5">
                <Users className="h-4 w-4 text-violet-500" aria-hidden />
                {detail.participants.length} {t("meeting.participants").toLowerCase()}
              </span>
              {detail.seriesTitle ? (
                <span className="rounded-full bg-white/70 px-2.5 py-0.5 text-xs font-medium text-violet-800 ring-1 ring-violet-200/80">
                  {detail.seriesTitle}
                </span>
              ) : null}
              {detail.businessContext ? (
                <span className="rounded-full bg-white/70 px-2.5 py-0.5 text-xs font-medium text-slate-700 ring-1 ring-slate-200/80">
                  {detail.businessContext}
                </span>
              ) : null}
            </div>
          </div>

          <button
            type="button"
            className="btn-secondary shrink-0 px-3 py-2 text-xs"
            aria-expanded={expanded}
            onClick={() => setExpanded((v) => !v)}
          >
            {t("meeting.detailsToggle")}
            <ChevronDown className={["h-4 w-4 transition", expanded ? "rotate-180" : ""].join(" ")} aria-hidden />
          </button>
        </div>

        {expanded ? (
          <div className="mt-4 grid gap-4 border-t border-white/60 pt-4 sm:grid-cols-2 lg:grid-cols-3">
            <DetailBlock title={t("meeting.participants")}>
              <ul className="space-y-1.5 text-sm">
                {detail.participants.map((p) => (
                  <ParticipantAttendanceRow key={p.id} participant={p} meetingStatus={m.status} />
                ))}
              </ul>
            </DetailBlock>

            <DetailBlock title={t("meeting.versions")}>
              {detail.versions.length ? (
                <ul className="space-y-1.5 text-sm text-slate-700">
                  {detail.versions.map((v) => (
                    <li key={v.version} className="rounded-lg bg-white/50 px-2.5 py-1.5">
                      {formatMeetingVersionLabel(v.version, v.label)}
                      <span className="block text-xs text-slate-500">{new Date(v.createdAt).toLocaleString()}</span>
                    </li>
                  ))}
                </ul>
              ) : (
                <p className="text-sm text-slate-500">—</p>
              )}
            </DetailBlock>

            <DetailBlock title={t("meeting.approvalHistory")}>
              <ApprovalList items={detail.approvalHistory} />
            </DetailBlock>
          </div>
        ) : null}
      </div>
    </header>
  );
}

function DetailBlock({ title, children }: { title: string; children: ReactNode }) {
  return (
    <div>
      <h2 className="mb-2 text-xs font-bold uppercase tracking-wide text-violet-700">{title}</h2>
      {children}
    </div>
  );
}

function ApprovalList({ items }: { items: ApprovalRecord[] }) {
  const { t, tb } = useI18n();
  if (!items.length) return <p className="text-sm text-slate-500">{t("meeting.noApprovals")}</p>;
  return (
    <ul className="space-y-1.5 text-sm">
      {items.map((a) => (
        <li key={a.id} className="rounded-lg bg-white/50 px-2.5 py-1.5">
          {tb("artifactType", a.artifactType)} · {tb("approvalStatus", a.status)}
          {a.decidedBy ? <span className="text-slate-500"> · {a.decidedBy}</span> : null}
        </li>
      ))}
    </ul>
  );
}
