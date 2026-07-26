import type { ApprovalRecord, MeetingDetailResponse, MeetingNote } from "@/api/types";
import { useAuth } from "@/auth/AuthProvider";
import { StatusBadge } from "@/components/qa/StatusBadge";
import { useI18n } from "@/i18n";
import { formatMeetingVersionLabel } from "@/lib/brandSanitize";

export function MeetingLeftPanel({ detail }: { detail: MeetingDetailResponse }) {
  const auth = useAuth();
  const { t, tb } = useI18n();
  const m = detail.meeting;

  return (
    <section className="card-static flex max-h-[calc(100dvh-12rem)] flex-col gap-4 overflow-y-auto p-4 sm:p-5" aria-label={t("meeting.metadata")}>
      <h2 className="text-sm font-bold uppercase tracking-wide text-slate-500">{t("meeting.metadata")}</h2>
      <dl className="grid gap-3 text-sm">
        <div>
          <dt className="label-text">{t("meeting.status")}</dt>
          <dd><StatusBadge label={tb("meetingStatus", m.status)} status={m.status} /></dd>
        </div>
        <div>
          <dt className="label-text">{t("meeting.scheduled")}</dt>
          <dd className="font-medium text-slate-800">{new Date(m.scheduledStartAt).toLocaleString()}</dd>
        </div>
        <div>
          <dt className="label-text">{t("meeting.series")}</dt>
          <dd className="text-slate-700">{detail.seriesTitle ?? "—"}</dd>
        </div>
        <div>
          <dt className="label-text">{t("meeting.businessContext")}</dt>
          <dd className="text-slate-700">{detail.businessContext ?? "—"}</dd>
        </div>
      </dl>

      <div>
        <h3 className="mb-2 text-xs font-bold uppercase tracking-wide text-violet-700">{t("meeting.participants")}</h3>
        <ul className="space-y-2 text-sm">
          {detail.participants.map((p) => (
            <li key={p.id} className="rounded-xl bg-white/50 px-3 py-2">
              <div className="font-medium text-slate-800">{p.displayName}</div>
              <div className="text-xs text-slate-500">
                {tb("participantType", p.participantType)}
                {p.external ? ` · ${t("meeting.external")}` : ""}
              </div>
            </li>
          ))}
        </ul>
      </div>

      <div>
        <h3 className="mb-2 text-xs font-bold uppercase tracking-wide text-violet-700">{t("meeting.versions")}</h3>
        <ul className="space-y-2 text-sm">
          {detail.versions.map((v) => (
            <li key={v.version} className="rounded-xl bg-white/50 px-3 py-2 text-slate-700">
              {formatMeetingVersionLabel(v.version, v.label)}
              <span className="block text-xs text-slate-500">{new Date(v.createdAt).toLocaleString()}</span>
            </li>
          ))}
        </ul>
      </div>

      <div>
        <h3 className="mb-2 text-xs font-bold uppercase tracking-wide text-violet-700">{t("meeting.approvalHistory")}</h3>
        <ApprovalList items={detail.approvalHistory} />
      </div>

      <div>
        <h3 className="mb-2 text-xs font-bold uppercase tracking-wide text-violet-700">{t("meeting.visibleNotes")}</h3>
        <NoteList notes={detail.notes} canSeePrivate={auth.canSeePrivateNote} />
      </div>
    </section>
  );
}

function ApprovalList({ items }: { items: ApprovalRecord[] }) {
  const { t, tb } = useI18n();
  if (!items.length) return <p className="text-sm text-slate-500">{t("meeting.noApprovals")}</p>;
  return (
    <ul className="space-y-2 text-sm">
      {items.map((a) => (
        <li key={a.id} className="rounded-xl bg-white/50 px-3 py-2">
          {tb("artifactType", a.artifactType)} · {tb("approvalStatus", a.status)}
          {a.decidedBy ? <span className="text-slate-500"> · {a.decidedBy}</span> : null}
        </li>
      ))}
    </ul>
  );
}

function NoteList({
  notes,
  canSeePrivate,
}: {
  notes: MeetingNote[];
  canSeePrivate: (authorId: string) => boolean;
}) {
  const { t, tb } = useI18n();
  const visible = notes.filter(
    (n) => n.visibility === "SHARED" || canSeePrivate(n.authorId),
  );
  if (!visible.length) return <p className="text-sm text-slate-500">{t("meeting.noAccessibleNotes")}</p>;
  return (
    <ul className="space-y-2 text-sm">
      {visible.map((n) => (
        <li key={n.id} className="rounded-xl bg-white/50 px-3 py-2">
          <StatusBadge label={tb("noteVisibility", n.visibility)} status={n.visibility} />
          <p className="mt-1 text-slate-700">{n.body}</p>
        </li>
      ))}
    </ul>
  );
}
