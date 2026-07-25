import type { ApprovalRecord, MeetingDetailResponse, MeetingNote } from "../../api/types";
import { useAuth } from "../../auth/AuthProvider";

export function MeetingLeftPanel({ detail }: { detail: MeetingDetailResponse }) {
  const auth = useAuth();
  const m = detail.meeting;

  return (
    <section className="panel left-panel" aria-label="Meeting metadata">
      <header className="panel-head">
        <h2>Metadata</h2>
      </header>
      <dl className="meta-list">
        <div>
          <dt>Status</dt>
          <dd>{m.status}</dd>
        </div>
        <div>
          <dt>Scheduled</dt>
          <dd>{new Date(m.scheduledStartAt).toLocaleString()}</dd>
        </div>
        <div>
          <dt>Series</dt>
          <dd>{detail.seriesTitle ?? "—"}</dd>
        </div>
        <div>
          <dt>Business context</dt>
          <dd>{detail.businessContext ?? "—"}</dd>
        </div>
      </dl>

      <h3 className="panel-sub">Participants</h3>
      <ul className="plain-list">
        {detail.participants.map((p) => (
          <li key={p.id}>
            {p.displayName}
            <span className="muted">
              {" "}
              · {p.participantType}
              {p.external ? " · external" : ""}
            </span>
          </li>
        ))}
      </ul>

      <h3 className="panel-sub">Versions</h3>
      <ul className="plain-list">
        {detail.versions.map((v) => (
          <li key={v.version}>
            v{v.version} — {v.label}
            <span className="muted"> · {new Date(v.createdAt).toLocaleString()}</span>
          </li>
        ))}
      </ul>

      <h3 className="panel-sub">Approval history</h3>
      <ApprovalList items={detail.approvalHistory} />

      <h3 className="panel-sub">Visible notes</h3>
      <NoteList notes={detail.notes} canSeePrivate={auth.canSeePrivateNote} />
    </section>
  );
}

function ApprovalList({ items }: { items: ApprovalRecord[] }) {
  if (!items.length) return <p className="muted">No approvals yet.</p>;
  return (
    <ul className="plain-list">
      {items.map((a) => (
        <li key={a.id}>
          {a.artifactType} · {a.status}
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
  const visible = notes.filter(
    (n) => n.visibility === "SHARED" || canSeePrivate(n.authorId),
  );
  if (!visible.length) return <p className="muted">No accessible notes.</p>;
  return (
    <ul className="plain-list">
      {visible.map((n) => (
        <li key={n.id}>
          <span className="marker">{n.visibility}</span> {n.body}
        </li>
      ))}
    </ul>
  );
}
