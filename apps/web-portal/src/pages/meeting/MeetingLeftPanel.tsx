import type { ApprovalRecord, MeetingDetailResponse, MeetingNote } from "../../api/types";
import { useAuth } from "../../auth/AuthProvider";
import { useI18n } from "../../i18n";

export function MeetingLeftPanel({ detail }: { detail: MeetingDetailResponse }) {
  const auth = useAuth();
  const { t, tb } = useI18n();
  const m = detail.meeting;

  return (
    <section className="panel left-panel" aria-label={t("meeting.metadata")}>
      <header className="panel-head">
        <h2>{t("meeting.metadata")}</h2>
      </header>
      <dl className="meta-list">
        <div>
          <dt>{t("meeting.status")}</dt>
          <dd>{tb("meetingStatus", m.status)}</dd>
        </div>
        <div>
          <dt>{t("meeting.scheduled")}</dt>
          <dd>{new Date(m.scheduledStartAt).toLocaleString()}</dd>
        </div>
        <div>
          <dt>{t("meeting.series")}</dt>
          <dd>{detail.seriesTitle ?? "—"}</dd>
        </div>
        <div>
          <dt>{t("meeting.businessContext")}</dt>
          <dd>{detail.businessContext ?? "—"}</dd>
        </div>
      </dl>

      <h3 className="panel-sub">{t("meeting.participants")}</h3>
      <ul className="plain-list">
        {detail.participants.map((p) => (
          <li key={p.id}>
            {p.displayName}
            <span className="muted">
              {" "}
              · {tb("participantType", p.participantType)}
              {p.external ? ` · ${t("meeting.external")}` : ""}
            </span>
          </li>
        ))}
      </ul>

      <h3 className="panel-sub">{t("meeting.versions")}</h3>
      <ul className="plain-list">
        {detail.versions.map((v) => (
          <li key={v.version}>
            v{v.version} — {v.label}
            <span className="muted"> · {new Date(v.createdAt).toLocaleString()}</span>
          </li>
        ))}
      </ul>

      <h3 className="panel-sub">{t("meeting.approvalHistory")}</h3>
      <ApprovalList items={detail.approvalHistory} />

      <h3 className="panel-sub">{t("meeting.visibleNotes")}</h3>
      <NoteList notes={detail.notes} canSeePrivate={auth.canSeePrivateNote} />
    </section>
  );
}

function ApprovalList({ items }: { items: ApprovalRecord[] }) {
  const { t, tb } = useI18n();
  if (!items.length) return <p className="muted">{t("meeting.noApprovals")}</p>;
  return (
    <ul className="plain-list">
      {items.map((a) => (
        <li key={a.id}>
          {a.artifactType} · {tb("approvalStatus", a.status)}
          {a.decidedBy ? <span className="muted"> · {a.decidedBy}</span> : null}
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
  if (!visible.length) return <p className="muted">{t("meeting.noAccessibleNotes")}</p>;
  return (
    <ul className="plain-list">
      {visible.map((n) => (
        <li key={n.id}>
          <span className="marker">{tb("noteVisibility", n.visibility)}</span> {n.body}
        </li>
      ))}
    </ul>
  );
}
