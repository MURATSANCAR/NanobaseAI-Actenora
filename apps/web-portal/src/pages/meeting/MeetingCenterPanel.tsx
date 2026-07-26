import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Children, useState, type ReactNode } from "react";
import { StatusBadge } from "@/components/qa/StatusBadge";
import { MeetingNoteEditor } from "@/components/meeting/MeetingNoteEditor";
import { PendingApprovalsPanel } from "@/components/meeting/PendingApprovalsPanel";
import { DueDateBadge } from "@/components/ui/DueDateBadge";
import { portalMutationsEnabled, queryKeys, resolvePortalAuthMode } from "@/api/client";
import { useApi, useApiMode } from "@/api/ApiProvider";
import type {
  ActionItem,
  CommitmentItem,
  DecisionItem,
  EvidenceRef,
  MeetingDetailResponse,
  RiskItem,
} from "@/api/types";
import { useAuth } from "@/auth/AuthProvider";
import { useI18n } from "@/i18n";
import { isOptimisticSafe } from "@/lib/approval";
import { evidenceMatchesSegment, formatEvidenceRange } from "@/lib/evidence";

export function MeetingCenterPanel({
  detail,
  onEvidence,
  selectedSegmentId,
}: {
  detail: MeetingDetailResponse;
  onEvidence: (ref: EvidenceRef) => void;
  selectedSegmentId: string | null;
}) {
  const auth = useAuth();
  const api = useApi();
  const apiMode = useApiMode();
  const { t, tb } = useI18n();
  const qc = useQueryClient();
  const meetingId = detail.meeting.id;
  const [noteDrafts, setNoteDrafts] = useState<Record<string, string>>({});
  const mutationsEnabled = portalMutationsEnabled(apiMode, resolvePortalAuthMode());

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
      void qc.invalidateQueries({ queryKey: queryKeys.dashboard });
      void qc.invalidateQueries({ queryKey: ["actions"] });
    },
  });

  const editableNotes = detail.notes.filter(
    (n) =>
      n.visibility === "SHARED" ||
      (n.visibility === "PRIVATE" && auth.canSeePrivateNote(n.authorId)),
  );

  const canEditNotes = auth.can("meetings:edit") && mutationsEnabled;
  const templatesQuery = useQuery({
    queryKey: queryKeys.templates,
    queryFn: () => api.listTemplates(),
    enabled: canEditNotes,
  });
  const publishedTemplates =
    templatesQuery.data?.items.filter((item) => item.status === "PUBLISHED") ?? [];
  const canCompleteActions = auth.can("meetings:edit") && mutationsEnabled;
  const canDecideApproval = auth.canApprove && mutationsEnabled;
  const hasPendingApprovals = detail.approvalHistory.some((a) => a.status === "PENDING");

  return (
    <section className="card-static flex max-h-[calc(100dvh-12rem)] flex-col gap-4 overflow-y-auto p-4 sm:p-5" aria-label={t("meeting.intelligence")}>
      <header className="flex flex-wrap items-start justify-between gap-2 border-b border-white/60 pb-3">
        <h2 className="text-lg font-bold text-slate-900">{detail.meeting.title}</h2>
        <span className="rounded-full bg-violet-50 px-2.5 py-1 text-[11px] font-semibold text-violet-700">
          {auth.can("meetings:edit") ? t("meeting.editEnabled") : t("meeting.readOnly")}
        </span>
      </header>

      {selectedSegmentId ? (
        <p className="rounded-xl border border-violet-200/70 bg-violet-50/50 px-3 py-2 text-xs text-violet-900">
          {t("evidence.linkedFromTranscript")}
        </p>
      ) : null}

      <ArtifactBlock title={t("meeting.notes")} emptyMessage={t("meeting.noNotesEditable")}>
        {editableNotes.map((n) => (
          <MeetingNoteEditor
            key={n.id}
            meetingId={meetingId}
            note={n}
            draft={noteDrafts[n.id] ?? n.body}
            canEdit={canEditNotes}
            publishedTemplates={publishedTemplates}
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
            linked={isArtifactLinked(d.evidence, selectedSegmentId)}
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
            linked={isArtifactLinked(a.evidence, selectedSegmentId)}
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
            linked={isArtifactLinked(r.evidence, selectedSegmentId)}
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
            linked={isArtifactLinked(c.evidence, selectedSegmentId)}
            onEvidence={onEvidence}
            statusLabel={tb("artifactStatus", c.status)}
            jumpLabel={t("meeting.jumpEvidence")}
          />
        ))}
      </ArtifactBlock>

      {hasPendingApprovals ? (
        <PendingApprovalsPanel
          meetingId={meetingId}
          items={detail.approvalHistory}
          canDecide={canDecideApproval}
        />
      ) : null}
    </section>
  );
}

function isArtifactLinked(evidence: EvidenceRef[], selectedSegmentId: string | null): boolean {
  if (!selectedSegmentId) return false;
  return evidence.some((e) => evidenceMatchesSegment(e, selectedSegmentId));
}

function artifactRowClass(linked: boolean): string {
  return [
    "rounded-xl border p-3 transition",
    linked
      ? "border-violet-400 bg-violet-100/60 ring-2 ring-violet-300/70"
      : "border-white/70 bg-white/50",
  ].join(" ");
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
    <div className="artifact-block space-y-2">
      <h3 className="text-xs font-bold uppercase tracking-wide text-violet-700">{title}</h3>
      {hasItems ? (
        <div className="space-y-2">{children}</div>
      ) : (
        <p className="text-sm text-slate-500">{emptyMessage}</p>
      )}
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
  const { t } = useI18n();
  if (!evidence.length) return null;
  return (
    <div className="evidence-actions mt-2 space-y-2">
      {evidence.map((e) => (
        <button
          key={`${e.segmentId}-${e.startMs}`}
          type="button"
          className="btn-secondary w-full px-3 py-2 text-left text-xs"
          onClick={() => onEvidence(e)}
        >
          <span className="block font-semibold text-violet-800">{jumpLabel}</span>
          <span className="mt-0.5 block font-mono text-[10px] text-slate-500">
            {formatEvidenceRange(e.startMs, e.endMs)}
          </span>
          {e.quote ? (
            <span className="mt-1 block line-clamp-2 text-slate-600">
              {t("evidence.quotePreview", { quote: e.quote })}
            </span>
          ) : null}
        </button>
      ))}
    </div>
  );
}

function DecisionRow({
  item,
  linked,
  onEvidence,
  statusLabel,
  jumpLabel,
}: {
  item: DecisionItem;
  linked: boolean;
  onEvidence: (ref: EvidenceRef) => void;
  statusLabel: string;
  jumpLabel: string;
}) {
  return (
    <div id={`artifact-decision-${item.id}`} className={artifactRowClass(linked)}>
      <strong className="block text-slate-900">{item.title}</strong>
      <div className="mt-1"><StatusBadge label={statusLabel} status={item.status} /></div>
      <EvidenceButtons evidence={item.evidence} onEvidence={onEvidence} jumpLabel={jumpLabel} />
    </div>
  );
}

function ActionRow({
  item,
  linked,
  onEvidence,
  statusLabel,
  jumpLabel,
  completeLabel,
  canComplete,
  onComplete,
}: {
  item: ActionItem;
  linked: boolean;
  onEvidence: (ref: EvidenceRef) => void;
  statusLabel: string;
  jumpLabel: string;
  completeLabel: string;
  canComplete: boolean;
  onComplete: () => void;
}) {
  return (
    <div id={`artifact-action-${item.id}`} className={artifactRowClass(linked)}>
      <strong className="block text-slate-900">{item.title}</strong>
      <div className="mt-1 flex flex-wrap items-center gap-2 text-sm text-slate-600">
        <StatusBadge label={statusLabel} status={item.status} />
        <span>{item.ownerDisplayName}</span>
        <DueDateBadge dueAt={item.dueAt} />
      </div>
      <EvidenceButtons evidence={item.evidence} onEvidence={onEvidence} jumpLabel={jumpLabel} />
      {canComplete && item.status !== "COMPLETED" ? (
        <button type="button" className="btn-primary mt-2" onClick={onComplete}>
          {completeLabel}
        </button>
      ) : null}
    </div>
  );
}

function RiskRow({
  item,
  linked,
  onEvidence,
  severityLabel,
  jumpLabel,
}: {
  item: RiskItem;
  linked: boolean;
  onEvidence: (ref: EvidenceRef) => void;
  severityLabel: string;
  jumpLabel: string;
}) {
  return (
    <div id={`artifact-risk-${item.id}`} className={artifactRowClass(linked)}>
      <strong className="block text-slate-900">{item.title}</strong>
      <div className="mt-1"><StatusBadge label={severityLabel} status={item.severity} /></div>
      <EvidenceButtons evidence={item.evidence} onEvidence={onEvidence} jumpLabel={jumpLabel} />
    </div>
  );
}

function CommitmentRow({
  item,
  linked,
  onEvidence,
  statusLabel,
  jumpLabel,
}: {
  item: CommitmentItem;
  linked: boolean;
  onEvidence: (ref: EvidenceRef) => void;
  statusLabel: string;
  jumpLabel: string;
}) {
  return (
    <div id={`artifact-commitment-${item.id}`} className={artifactRowClass(linked)}>
      <strong className="block text-slate-900">{item.statement}</strong>
      <div className="mt-1 flex flex-wrap items-center gap-2 text-sm text-slate-600">
        <StatusBadge label={statusLabel} status={item.status} />
        <span>{item.ownerDisplayName}</span>
        <DueDateBadge dueAt={item.dueAt} />
      </div>
      <EvidenceButtons evidence={item.evidence} onEvidence={onEvidence} jumpLabel={jumpLabel} />
    </div>
  );
}
