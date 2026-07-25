import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useState, type ReactNode } from "react";
import { useApi } from "../../api/ApiProvider";
import { queryKeys } from "../../api/client";
import type {
  ActionItem,
  CommitmentItem,
  DecisionItem,
  EvidenceRef,
  MeetingDetailResponse,
  MeetingNote,
  RiskItem,
} from "../../api/types";
import { useAuth } from "../../auth/AuthProvider";
import { isOptimisticSafe } from "../../lib/approval";

export function MeetingCenterPanel({
  detail,
  onEvidence,
}: {
  detail: MeetingDetailResponse;
  onEvidence: (ref: EvidenceRef) => void;
}) {
  const auth = useAuth();
  const api = useApi();
  const qc = useQueryClient();
  const meetingId = detail.meeting.id;
  const [noteDrafts, setNoteDrafts] = useState<Record<string, string>>({});

  const noteMutation = useMutation({
    mutationFn: ({ noteId, body }: { noteId: string; body: string }) =>
      api.updateMeetingNote(meetingId, noteId, body),
    onMutate: async ({ noteId, body }) => {
      if (!isOptimisticSafe("updateMeetingNote")) return;
      await qc.cancelQueries({ queryKey: queryKeys.meetingDetail(meetingId) });
      const prev = qc.getQueryData<MeetingDetailResponse>(queryKeys.meetingDetail(meetingId));
      if (prev) {
        qc.setQueryData<MeetingDetailResponse>(queryKeys.meetingDetail(meetingId), {
          ...prev,
          notes: prev.notes.map((n) => (n.id === noteId ? { ...n, body } : n)),
        });
      }
      return { prev };
    },
    onError: (_err, _vars, ctx) => {
      if (ctx?.prev) qc.setQueryData(queryKeys.meetingDetail(meetingId), ctx.prev);
    },
    onSettled: () => {
      void qc.invalidateQueries({ queryKey: queryKeys.meetingDetail(meetingId) });
    },
  });

  const approveMutation = useMutation({
    mutationFn: ({
      approvalId,
      decision,
    }: {
      approvalId: string;
      decision: "APPROVE" | "REJECT";
    }) => api.decideApproval(approvalId, decision),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: queryKeys.meetingDetail(meetingId) });
      void qc.invalidateQueries({ queryKey: ["decisions"] });
    },
  });

  const completeMutation = useMutation({
    mutationFn: (actionId: string) => api.completeAction(actionId),
    onMutate: async (actionId) => {
      if (!isOptimisticSafe("completeAction")) return;
      await qc.cancelQueries({ queryKey: queryKeys.meetingDetail(meetingId) });
      const prev = qc.getQueryData<MeetingDetailResponse>(queryKeys.meetingDetail(meetingId));
      if (prev) {
        qc.setQueryData<MeetingDetailResponse>(queryKeys.meetingDetail(meetingId), {
          ...prev,
          actions: prev.actions.map((a) =>
            a.id === actionId ? { ...a, status: "COMPLETED" } : a,
          ),
        });
      }
      return { prev };
    },
    onError: (_e, _id, ctx) => {
      if (ctx?.prev) qc.setQueryData(queryKeys.meetingDetail(meetingId), ctx.prev);
    },
    onSettled: () => {
      void qc.invalidateQueries({ queryKey: queryKeys.meetingDetail(meetingId) });
      void qc.invalidateQueries({ queryKey: ["actions"] });
    },
  });

  const editableNotes = detail.notes.filter(
    (n) =>
      n.visibility === "SHARED" ||
      (n.visibility === "PRIVATE" && auth.canSeePrivateNote(n.authorId)),
  );

  const pending = detail.approvalHistory.find((a) => a.status === "PENDING");

  return (
    <section className="panel center-panel" aria-label="Meeting intelligence">
      <header className="panel-head">
        <h2>{detail.meeting.title}</h2>
        {auth.can("meetings:edit") ? (
          <span className="muted">Edit enabled</span>
        ) : (
          <span className="muted">Read only</span>
        )}
      </header>

      <ArtifactBlock title="Notes">
        {editableNotes.map((n) => (
          <NoteEditor
            key={n.id}
            note={n}
            draft={noteDrafts[n.id] ?? n.body}
            canEdit={auth.can("meetings:edit")}
            onChange={(body) => setNoteDrafts((d) => ({ ...d, [n.id]: body }))}
            onSave={() =>
              noteMutation.mutate({ noteId: n.id, body: noteDrafts[n.id] ?? n.body })
            }
            saving={noteMutation.isPending}
          />
        ))}
      </ArtifactBlock>

      <ArtifactBlock title="Decisions">
        {detail.decisions.map((d) => (
          <DecisionRow key={d.id} item={d} onEvidence={onEvidence} />
        ))}
      </ArtifactBlock>

      <ArtifactBlock title="Actions">
        {detail.actions.map((a) => (
          <ActionRow
            key={a.id}
            item={a}
            onEvidence={onEvidence}
            canComplete={auth.can("meetings:edit")}
            onComplete={() => completeMutation.mutate(a.id)}
          />
        ))}
      </ArtifactBlock>

      <ArtifactBlock title="Risks">
        {detail.risks.map((r) => (
          <RiskRow key={r.id} item={r} onEvidence={onEvidence} />
        ))}
      </ArtifactBlock>

      <ArtifactBlock title="Commitments">
        {detail.commitments.map((c) => (
          <CommitmentRow key={c.id} item={c} onEvidence={onEvidence} />
        ))}
      </ArtifactBlock>

      {pending && auth.canApprove ? (
        <div className="approve-bar" role="group" aria-label="Approval actions">
          <p>
            Pending {pending.artifactType} approval
          </p>
          <button
            type="button"
            className="btn"
            onClick={() =>
              approveMutation.mutate({ approvalId: pending.id, decision: "APPROVE" })
            }
            disabled={approveMutation.isPending}
          >
            Approve
          </button>
          <button
            type="button"
            className="btn ghost"
            onClick={() =>
              approveMutation.mutate({ approvalId: pending.id, decision: "REJECT" })
            }
            disabled={approveMutation.isPending}
          >
            Reject
          </button>
          {approveMutation.isError ? (
            <span role="alert">{(approveMutation.error as Error).message}</span>
          ) : null}
        </div>
      ) : null}
    </section>
  );
}

function ArtifactBlock({ title, children }: { title: string; children: ReactNode }) {
  return (
    <div className="artifact-block">
      <h3 className="panel-sub">{title}</h3>
      <div className="artifact-stack">{children}</div>
    </div>
  );
}

function NoteEditor({
  note,
  draft,
  canEdit,
  onChange,
  onSave,
  saving,
}: {
  note: MeetingNote;
  draft: string;
  canEdit: boolean;
  onChange: (body: string) => void;
  onSave: () => void;
  saving: boolean;
}) {
  return (
    <div className="artifact-card">
      <div className="artifact-head">
        <span className="marker">{note.visibility}</span>
      </div>
      <textarea
        value={draft}
        onChange={(e) => onChange(e.target.value)}
        disabled={!canEdit}
        aria-label={`${note.visibility} note`}
        rows={3}
      />
      {canEdit ? (
        <button type="button" className="btn" onClick={onSave} disabled={saving}>
          Save note
        </button>
      ) : null}
    </div>
  );
}

function EvidenceButtons({
  evidence,
  onEvidence,
}: {
  evidence: EvidenceRef[];
  onEvidence: (ref: EvidenceRef) => void;
}) {
  if (!evidence.length) return null;
  return (
    <div className="evidence-actions">
      {evidence.map((e) => (
        <button
          key={`${e.segmentId}-${e.startMs}`}
          type="button"
          className="btn ghost"
          onClick={() => onEvidence(e)}
        >
          Jump to evidence
        </button>
      ))}
    </div>
  );
}

function DecisionRow({
  item,
  onEvidence,
}: {
  item: DecisionItem;
  onEvidence: (ref: EvidenceRef) => void;
}) {
  return (
    <div className="artifact-card">
      <strong>{item.title}</strong>
      <span className="muted">{item.status}</span>
      <EvidenceButtons evidence={item.evidence} onEvidence={onEvidence} />
    </div>
  );
}

function ActionRow({
  item,
  onEvidence,
  canComplete,
  onComplete,
}: {
  item: ActionItem;
  onEvidence: (ref: EvidenceRef) => void;
  canComplete: boolean;
  onComplete: () => void;
}) {
  return (
    <div className="artifact-card">
      <strong>{item.title}</strong>
      <span className="muted">
        {item.status} · {item.ownerDisplayName}
      </span>
      <EvidenceButtons evidence={item.evidence} onEvidence={onEvidence} />
      {canComplete && item.status !== "COMPLETED" ? (
        <button type="button" className="btn" onClick={onComplete}>
          Mark complete
        </button>
      ) : null}
    </div>
  );
}

function RiskRow({
  item,
  onEvidence,
}: {
  item: RiskItem;
  onEvidence: (ref: EvidenceRef) => void;
}) {
  return (
    <div className="artifact-card">
      <strong>{item.title}</strong>
      <span className="muted">{item.severity}</span>
      <EvidenceButtons evidence={item.evidence} onEvidence={onEvidence} />
    </div>
  );
}

function CommitmentRow({
  item,
  onEvidence,
}: {
  item: CommitmentItem;
  onEvidence: (ref: EvidenceRef) => void;
}) {
  return (
    <div className="artifact-card">
      <strong>{item.statement}</strong>
      <span className="muted">
        {item.status} · {item.ownerDisplayName}
      </span>
      <EvidenceButtons evidence={item.evidence} onEvidence={onEvidence} />
    </div>
  );
}
