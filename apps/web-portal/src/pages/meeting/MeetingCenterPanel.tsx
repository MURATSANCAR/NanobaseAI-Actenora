import { useMutation, useQueryClient } from "@tanstack/react-query";
import { Children, useState, type ReactNode } from "react";
import { portalMutationsEnabled } from "../../api/client";
import { useApi, useApiMode } from "../../api/ApiProvider";
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
import { useI18n } from "../../i18n";
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
  const apiMode = useApiMode();
  const { t, tb } = useI18n();
  const qc = useQueryClient();
  const meetingId = detail.meeting.id;
  const [noteDrafts, setNoteDrafts] = useState<Record<string, string>>({});
  const mutationsEnabled = portalMutationsEnabled(apiMode);

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
  const canEditNotes = auth.can("meetings:edit") && mutationsEnabled;
  const canCompleteActions = auth.can("meetings:edit") && mutationsEnabled;
  const canDecideApproval = auth.canApprove && mutationsEnabled;

  return (
    <section className="panel center-panel" aria-label={t("meeting.intelligence")}>
      <header className="panel-head">
        <h2>{detail.meeting.title}</h2>
        {auth.can("meetings:edit") ? (
          <span className="muted">{t("meeting.editEnabled")}</span>
        ) : (
          <span className="muted">{t("meeting.readOnly")}</span>
        )}
      </header>

      <ArtifactBlock title={t("meeting.notes")} emptyMessage={t("meeting.noNotesEditable")}>
        {editableNotes.map((n) => (
          <NoteEditor
            key={n.id}
            note={n}
            draft={noteDrafts[n.id] ?? n.body}
            visibilityLabel={tb("noteVisibility", n.visibility)}
            canEdit={canEditNotes}
            saveLabel={t("meeting.saveNote")}
            onChange={(body) => setNoteDrafts((d) => ({ ...d, [n.id]: body }))}
            onSave={() =>
              noteMutation.mutate({ noteId: n.id, body: noteDrafts[n.id] ?? n.body })
            }
            saving={noteMutation.isPending}
          />
        ))}
      </ArtifactBlock>

      <ArtifactBlock title={t("meeting.decisions")} emptyMessage={t("meeting.noDecisions")}>
        {detail.decisions.map((d) => (
          <DecisionRow
            key={d.id}
            item={d}
            onEvidence={onEvidence}
            statusLabel={tb("artifactStatus", d.status)}
            jumpLabel={t("meeting.jumpEvidence")}
          />
        ))}
      </ArtifactBlock>

      <ArtifactBlock title={t("meeting.actions")} emptyMessage={t("meeting.noActions")}>
        {detail.actions.map((a) => (
          <ActionRow
            key={a.id}
            item={a}
            onEvidence={onEvidence}
            statusLabel={tb("artifactStatus", a.status)}
            jumpLabel={t("meeting.jumpEvidence")}
            completeLabel={t("meeting.markComplete")}
            canComplete={canCompleteActions}
            onComplete={() => completeMutation.mutate(a.id)}
          />
        ))}
      </ArtifactBlock>

      <ArtifactBlock title={t("meeting.risks")} emptyMessage={t("meeting.noRisks")}>
        {detail.risks.map((r) => (
          <RiskRow
            key={r.id}
            item={r}
            onEvidence={onEvidence}
            severityLabel={tb("riskSeverity", r.severity)}
            jumpLabel={t("meeting.jumpEvidence")}
          />
        ))}
      </ArtifactBlock>

      <ArtifactBlock title={t("meeting.commitments")} emptyMessage={t("meeting.noCommitments")}>
        {detail.commitments.map((c) => (
          <CommitmentRow
            key={c.id}
            item={c}
            onEvidence={onEvidence}
            statusLabel={tb("artifactStatus", c.status)}
            jumpLabel={t("meeting.jumpEvidence")}
          />
        ))}
      </ArtifactBlock>

      {pending && canDecideApproval ? (
        <div className="approve-bar" role="group" aria-label={t("meeting.approve")}>
          <p>{t("meeting.pendingApproval", { type: pending.artifactType })}</p>
          <button
            type="button"
            className="btn"
            onClick={() =>
              approveMutation.mutate({ approvalId: pending.id, decision: "APPROVE" })
            }
            disabled={approveMutation.isPending}
          >
            {t("meeting.approve")}
          </button>
          <button
            type="button"
            className="btn ghost"
            onClick={() =>
              approveMutation.mutate({ approvalId: pending.id, decision: "REJECT" })
            }
            disabled={approveMutation.isPending}
          >
            {t("meeting.reject")}
          </button>
          {approveMutation.isError ? (
            <span role="alert">{(approveMutation.error as Error).message}</span>
          ) : null}
        </div>
      ) : null}
    </section>
  );
}

function ArtifactBlock({
  title,
  emptyMessage,
  children,
}: {
  title: string;
  emptyMessage: string;
  children: ReactNode;
}) {
  const hasItems = Children.count(children) > 0;

  return (
    <div className="artifact-block">
      <h3 className="panel-sub">{title}</h3>
      {hasItems ? (
        <div className="artifact-stack">{children}</div>
      ) : (
        <p className="muted">{emptyMessage}</p>
      )}
    </div>
  );
}

function NoteEditor({
  note,
  draft,
  visibilityLabel,
  canEdit,
  saveLabel,
  onChange,
  onSave,
  saving,
}: {
  note: MeetingNote;
  draft: string;
  visibilityLabel: string;
  canEdit: boolean;
  saveLabel: string;
  onChange: (body: string) => void;
  onSave: () => void;
  saving: boolean;
}) {
  return (
    <div className="artifact-card">
      <div className="artifact-head">
        <span className="marker">{visibilityLabel}</span>
      </div>
      <textarea
        value={draft}
        onChange={(e) => onChange(e.target.value)}
        disabled={!canEdit}
        aria-label={visibilityLabel}
        rows={3}
      />
      {canEdit ? (
        <button type="button" className="btn" onClick={onSave} disabled={saving}>
          {saveLabel}
        </button>
      ) : null}
    </div>
  );
}

function EvidenceButtons({
  evidence,
  onEvidence,
  jumpLabel,
}: {
  evidence: EvidenceRef[];
  onEvidence: (ref: EvidenceRef) => void;
  jumpLabel: string;
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
          {jumpLabel}
        </button>
      ))}
    </div>
  );
}

function DecisionRow({
  item,
  onEvidence,
  statusLabel,
  jumpLabel,
}: {
  item: DecisionItem;
  onEvidence: (ref: EvidenceRef) => void;
  statusLabel: string;
  jumpLabel: string;
}) {
  return (
    <div className="artifact-card">
      <strong>{item.title}</strong>
      <span className="muted">{statusLabel}</span>
      <EvidenceButtons evidence={item.evidence} onEvidence={onEvidence} jumpLabel={jumpLabel} />
    </div>
  );
}

function ActionRow({
  item,
  onEvidence,
  statusLabel,
  jumpLabel,
  completeLabel,
  canComplete,
  onComplete,
}: {
  item: ActionItem;
  onEvidence: (ref: EvidenceRef) => void;
  statusLabel: string;
  jumpLabel: string;
  completeLabel: string;
  canComplete: boolean;
  onComplete: () => void;
}) {
  return (
    <div className="artifact-card">
      <strong>{item.title}</strong>
      <span className="muted">
        {statusLabel} · {item.ownerDisplayName}
      </span>
      <EvidenceButtons evidence={item.evidence} onEvidence={onEvidence} jumpLabel={jumpLabel} />
      {canComplete && item.status !== "COMPLETED" ? (
        <button type="button" className="btn" onClick={onComplete}>
          {completeLabel}
        </button>
      ) : null}
    </div>
  );
}

function RiskRow({
  item,
  onEvidence,
  severityLabel,
  jumpLabel,
}: {
  item: RiskItem;
  onEvidence: (ref: EvidenceRef) => void;
  severityLabel: string;
  jumpLabel: string;
}) {
  return (
    <div className="artifact-card">
      <strong>{item.title}</strong>
      <span className="muted">{severityLabel}</span>
      <EvidenceButtons evidence={item.evidence} onEvidence={onEvidence} jumpLabel={jumpLabel} />
    </div>
  );
}

function CommitmentRow({
  item,
  onEvidence,
  statusLabel,
  jumpLabel,
}: {
  item: CommitmentItem;
  onEvidence: (ref: EvidenceRef) => void;
  statusLabel: string;
  jumpLabel: string;
}) {
  return (
    <div className="artifact-card">
      <strong>{item.statement}</strong>
      <span className="muted">
        {statusLabel} · {item.ownerDisplayName}
      </span>
      <EvidenceButtons evidence={item.evidence} onEvidence={onEvidence} jumpLabel={jumpLabel} />
    </div>
  );
}
