import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState, type ReactNode } from "react";
import { FileText, ListChecks, ShieldAlert, Target, Zap } from "lucide-react";
import { MeetingNoteEditor } from "@/components/meeting/MeetingNoteEditor";
import { PendingApprovalsPanel } from "@/components/meeting/PendingApprovalsPanel";
import { StatusBadge } from "@/components/qa/StatusBadge";
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
import { deriveMeetingPipelineStages } from "@/lib/meetingPipeline";

type InsightTab = "decisions" | "actions" | "risks" | "commitments";

export function MeetingCenterPanel({
  detail,
  onEvidence,
  selectedSegmentId,
  hasTranscript,
}: {
  detail: MeetingDetailResponse;
  onEvidence: (ref: EvidenceRef) => void;
  selectedSegmentId: string | null;
  hasTranscript: boolean;
}) {
  const auth = useAuth();
  const api = useApi();
  const apiMode = useApiMode();
  const { t, tb } = useI18n();
  const qc = useQueryClient();
  const meetingId = detail.meeting.id;
  const [noteDrafts, setNoteDrafts] = useState<Record<string, string>>({});
  const [activeTab, setActiveTab] = useState<InsightTab>("decisions");
  const mutationsEnabled = portalMutationsEnabled(apiMode, resolvePortalAuthMode());

  const stages = deriveMeetingPipelineStages(detail, hasTranscript);
  const notesStageReady = stages.find((s) => s.id === "NOTES")?.state === "done" || detail.notes.length > 0;
  const showNotes = notesStageReady || detail.notes.length > 0;

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

  const tabs: { id: InsightTab; label: string; count: number; icon: typeof FileText }[] = [
    { id: "decisions", label: t("meeting.decisions"), count: detail.decisions.length, icon: Target },
    { id: "actions", label: t("meeting.actions"), count: detail.actions.length, icon: ListChecks },
    { id: "risks", label: t("meeting.risks"), count: detail.risks.length, icon: ShieldAlert },
    { id: "commitments", label: t("meeting.commitments"), count: detail.commitments.length, icon: Zap },
  ];

  return (
    <div className="space-y-5">
      {selectedSegmentId ? (
        <p className="rounded-xl border border-violet-200/70 bg-violet-50/50 px-4 py-2.5 text-sm text-violet-900">
          {t("evidence.linkedFromTranscript")}
        </p>
      ) : null}

      <section aria-label={t("meeting.notes")}>
        <div className="mb-3 flex flex-wrap items-center justify-between gap-2">
          <h2 className="flex items-center gap-2 text-sm font-bold uppercase tracking-wide text-violet-700">
            <FileText className="h-4 w-4" aria-hidden />
            {t("meeting.notes")}
          </h2>
          <span className="rounded-full bg-violet-50 px-2.5 py-1 text-[11px] font-semibold text-violet-700">
            {auth.can("meetings:edit") ? t("meeting.editEnabled") : t("meeting.readOnly")}
          </span>
        </div>

        {!showNotes ? (
          <div className="rounded-2xl border border-dashed border-violet-200/80 bg-violet-50/30 px-6 py-10 text-center">
            <div className="mx-auto mb-3 flex h-12 w-12 items-center justify-center rounded-2xl bg-violet-100 text-violet-600">
              <FileText className="h-6 w-6" aria-hidden />
            </div>
            <p className="font-semibold text-slate-800">{t("meeting.notesPendingTitle")}</p>
            <p className="mt-1 text-sm text-slate-600">{t("meeting.notesPendingHint")}</p>
          </div>
        ) : editableNotes.length ? (
          <div className="space-y-4">
            {editableNotes.map((n) => (
              <MeetingNoteEditor
                key={n.id}
                meetingId={meetingId}
                note={n}
                draft={noteDrafts[n.id] ?? n.body}
                canEdit={canEditNotes}
                publishedTemplates={publishedTemplates}
                meetingTitle={detail.meeting.title}
                onChange={(body) => setNoteDrafts((d) => ({ ...d, [n.id]: body }))}
                onSave={() =>
                  noteMutation.mutate({ noteId: n.id, body: noteDrafts[n.id] ?? n.body })
                }
                saving={noteMutation.isPending}
              />
            ))}
          </div>
        ) : (
          <p className="text-sm text-slate-500">{t("meeting.noNotesEditable")}</p>
        )}
      </section>

      <section className="card-static p-4 sm:p-5" aria-label={t("meeting.intelligence")}>
        <h2 className="mb-3 text-sm font-bold uppercase tracking-wide text-violet-700">
          {t("meeting.insightsTitle")}
        </h2>

        <div className="mb-4 flex flex-wrap gap-1.5 border-b border-white/60 pb-3">
          {tabs.map((tab) => {
            const Icon = tab.icon;
            const active = activeTab === tab.id;
            return (
              <button
                key={tab.id}
                type="button"
                onClick={() => setActiveTab(tab.id)}
                className={[
                  "inline-flex items-center gap-1.5 rounded-xl px-3 py-2 text-xs font-semibold transition",
                  active
                    ? "bg-violet-600 text-white shadow-md shadow-violet-200"
                    : "bg-white/60 text-slate-600 hover:bg-white hover:text-violet-800",
                ].join(" ")}
              >
                <Icon className="h-3.5 w-3.5" aria-hidden />
                {tab.label}
                <span
                  className={[
                    "rounded-full px-1.5 py-0.5 text-[10px]",
                    active ? "bg-white/20 text-white" : "bg-violet-100 text-violet-700",
                  ].join(" ")}
                >
                  {tab.count}
                </span>
              </button>
            );
          })}
        </div>

        <InsightPanel
          tab={activeTab}
          detail={detail}
          selectedSegmentId={selectedSegmentId}
          onEvidence={onEvidence}
          canCompleteActions={canCompleteActions}
          onCompleteAction={(id) => completeMutation.mutate(id)}
        />
      </section>

      {hasPendingApprovals ? (
        <PendingApprovalsPanel
          meetingId={meetingId}
          items={detail.approvalHistory}
          canDecide={canDecideApproval}
        />
      ) : null}
    </div>
  );
}

function InsightPanel({
  tab,
  detail,
  selectedSegmentId,
  onEvidence,
  canCompleteActions,
  onCompleteAction,
}: {
  tab: InsightTab;
  detail: MeetingDetailResponse;
  selectedSegmentId: string | null;
  onEvidence: (ref: EvidenceRef) => void;
  canCompleteActions: boolean;
  onCompleteAction: (id: string) => void;
}) {
  const { t, tb } = useI18n();

  if (tab === "decisions") {
    return (
      <ArtifactList empty={t("meeting.noDecisions")} hasItems={detail.decisions.length > 0}>
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
      </ArtifactList>
    );
  }

  if (tab === "actions") {
    return (
      <ArtifactList empty={t("meeting.noActions")} hasItems={detail.actions.length > 0}>
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
            onComplete={() => onCompleteAction(a.id)}
          />
        ))}
      </ArtifactList>
    );
  }

  if (tab === "risks") {
    return (
      <ArtifactList empty={t("meeting.noRisks")} hasItems={detail.risks.length > 0}>
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
      </ArtifactList>
    );
  }

  return (
    <ArtifactList empty={t("meeting.noCommitments")} hasItems={detail.commitments.length > 0}>
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
    </ArtifactList>
  );
}

function ArtifactList({
  empty,
  hasItems,
  children,
}: {
  empty: string;
  hasItems: boolean;
  children: ReactNode;
}) {
  if (!hasItems) return <p className="text-sm text-slate-500">{empty}</p>;
  return <div className="space-y-2">{children}</div>;
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
