import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useMemo, useState, type ReactNode } from "react";
import { FileText, ListChecks, ShieldAlert, Target, Zap } from "lucide-react";
import { HighlightPersonNames } from "@/components/meeting/HighlightPersonNames";
import { MeetingNoteEditor } from "@/components/meeting/MeetingNoteEditor";
import { MeetingReviewPanel } from "@/components/meeting/MeetingReviewPanel";
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
import { evidenceMatchesSegment, formatEvidenceRange, isSeekableEvidence } from "@/lib/evidence";
import { deriveMeetingPipelineStages } from "@/lib/meetingPipeline";
import { collectPersonNames } from "@/lib/personNames";

type InsightTab = "decisions" | "actions" | "risks" | "commitments";

export function MeetingCenterPanel({
  detail,
  onEvidence,
  selectedSegmentId,
  hasConversation,
}: {
  detail: MeetingDetailResponse;
  onEvidence: (ref: EvidenceRef) => void;
  selectedSegmentId: string | null;
  hasConversation: boolean;
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

  const stages = deriveMeetingPipelineStages(detail, hasConversation);
  const notesStageReady = stages.find((s) => s.id === "NOTES")?.state === "done" || detail.notes.length > 0;
  const showNotes = notesStageReady || detail.notes.length > 0;

  const deliveryQuery = useQuery({
    queryKey: queryKeys.meetingDelivery(meetingId),
    queryFn: () => api.getMeetingDelivery(meetingId),
  });

  const deliveryBadge = (() => {
    const items = deliveryQuery.data ?? [];
    if (items.length === 0) return null;
    const draft = items.find((d) => d.intent === "DRAFT_ORGANIZER");
    const finalReq = items.find((d) => d.intent === "FINAL_EXTERNAL");
    const pick = finalReq ?? draft;
    if (!pick) return null;
    const status = pick.status;
    const isFinal = pick.intent === "FINAL_EXTERNAL";
    let label: string;
    if (status === "DELIVERED") {
      label = t(isFinal ? "meeting.deliveryFinalSent" : "meeting.deliveryDraftSent");
    } else if (status === "PROVIDER_ACCEPTED") {
      label = t("meeting.deliveryAccepted");
    } else if (status === "FAILED" || status === "DEAD") {
      label = t("meeting.deliveryFailed");
    } else {
      label = t(isFinal ? "meeting.deliveryFinalQueued" : "meeting.deliveryDraftQueued");
    }
    return { label, status };
  })();

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
    onSuccess: (updated) => {
      qc.setQueryData<MeetingDetailResponse>(queryKeys.meetingDetail(meetingId), (prev) => {
        if (!prev) return prev;
        return {
          ...prev,
          notes: prev.notes.map((n) =>
            n.id === updated.id
              ? {
                  ...n,
                  body: updated.body,
                  version: updated.version,
                  updatedAt: updated.updatedAt,
                  approvalStatus: updated.approvalStatus,
                  draft: updated.draft,
                }
              : n,
          ),
        };
      });
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

  const primaryNoteId = editableNotes[0]?.id ?? detail.notes[0]?.id ?? null;
  const rendersQuery = useQuery({
    queryKey: queryKeys.noteRenders(meetingId, primaryNoteId ?? ""),
    queryFn: () => api.getNoteRenders(meetingId, primaryNoteId!),
    enabled: Boolean(primaryNoteId),
  });
  const pdfDocument =
    rendersQuery.data?.documents.find((d) => d.format === "PDF" && d.downloadUrl) ??
    rendersQuery.data?.documents.find((d) => d.format === "PDF") ??
    null;
  const pdfPending = Boolean(
    primaryNoteId &&
      (rendersQuery.data?.jobs.some((j) => j.format === "PDF" && (j.status === "PENDING" || j.status === "RUNNING")) ??
        false),
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

  const tabs: { id: InsightTab; label: string; count: number; icon: typeof FileText }[] = [
    { id: "decisions", label: t("meeting.decisions"), count: detail.decisions.length, icon: Target },
    { id: "actions", label: t("meeting.actions"), count: detail.actions.length, icon: ListChecks },
    { id: "risks", label: t("meeting.risks"), count: detail.risks.length, icon: ShieldAlert },
    { id: "commitments", label: t("meeting.commitments"), count: detail.commitments.length, icon: Zap },
  ];

  return (
    <div className="space-y-5">
      <MeetingReviewPanel detail={detail} />

      {selectedSegmentId ? (
        <p className="rounded-xl border border-violet-200/70 bg-violet-50/50 px-4 py-2.5 text-sm text-violet-900">
          {t("evidence.linkedFromConversation")}
        </p>
      ) : null}

      <section aria-label={t("meeting.notes")}>
        <div className="mb-4 flex flex-wrap items-center justify-between gap-2">
          <h2 className="font-display text-base font-semibold tracking-tight text-slate-900 sm:text-lg">
            {t("meeting.minutesDocumentTitle")}
          </h2>
          <div className="flex flex-wrap items-center gap-2">
            {deliveryBadge ? (
              <StatusBadge label={deliveryBadge.label} status={deliveryBadge.status} />
            ) : null}
            {pdfDocument?.downloadUrl ? (
              <a
                href={pdfDocument.downloadUrl}
                target="_blank"
                rel="noreferrer"
                className="inline-flex items-center rounded-full border border-emerald-200 bg-emerald-50 px-3 py-1 text-[11px] font-semibold text-emerald-800"
              >
                {t("meeting.pdfDownload")}
              </a>
            ) : pdfPending ? (
              <StatusBadge label={t("meeting.pdfPending")} status="QUEUED" />
            ) : null}
            <span className="inline-flex items-center gap-1.5 rounded-full border border-violet-200/80 bg-gradient-to-r from-violet-50 to-sky-50 px-3 py-1 text-[11px] font-semibold text-violet-800">
              <span className="h-1.5 w-1.5 rounded-full bg-emerald-500" aria-hidden />
              {auth.can("meetings:edit") ? t("meeting.editEnabled") : t("meeting.readOnly")}
            </span>
          </div>
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
                participants={detail.participants}
                meetingStatus={detail.meeting.status}
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

      <section className="meeting-insight-shell" aria-label={t("meeting.intelligence")}>
        <div className="mb-4 flex flex-wrap items-center justify-between gap-2">
          <h2 className="font-display text-base font-semibold tracking-tight text-slate-900 sm:text-lg">
            {t("meeting.insightsTitle")}
          </h2>
          <span className="meeting-detail-live-pill">
            <span className="module-hero-live-dot" aria-hidden />
            {t("meeting.detailLivePulse")}
          </span>
        </div>

        <div className="mb-4 flex flex-wrap gap-1.5 border-b border-violet-100/80 pb-3">
          {tabs.map((tab) => {
            const Icon = tab.icon;
            const active = activeTab === tab.id;
            return (
              <button
                key={tab.id}
                type="button"
                onClick={() => setActiveTab(tab.id)}
                className={[
                  "meeting-insight-tab",
                  active ? "meeting-insight-tab--active" : "meeting-insight-tab--idle",
                ].join(" ")}
              >
                <Icon className="h-3.5 w-3.5" aria-hidden />
                {tab.label}
                <span
                  className={[
                    "meeting-insight-count",
                    active ? "bg-white/20 text-white" : "bg-violet-100 text-violet-700",
                    tab.count > 0 ? "meeting-insight-count--pulse" : "",
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

function artifactRowClass(linked: boolean, tone: "decision" | "action" | "risk" | "commitment" = "decision"): string {
  const tones = {
    decision: "border-emerald-200/80 bg-gradient-to-br from-emerald-50/80 via-white to-teal-50/40",
    action: "border-amber-200/80 bg-gradient-to-br from-amber-50/80 via-white to-orange-50/40",
    risk: "border-rose-200/80 bg-gradient-to-br from-rose-50/80 via-white to-red-50/40",
    commitment: "border-cyan-200/80 bg-gradient-to-br from-cyan-50/80 via-white to-sky-50/40",
  };
  return [
    "rounded-2xl border p-3.5 shadow-sm transition",
    linked
      ? "border-violet-400 bg-violet-100/70 ring-2 ring-violet-300/70 meeting-detail-pulse-border"
      : tones[tone],
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
  const seekable = evidence.filter(isSeekableEvidence);
  if (!seekable.length) return null;
  return (
    <div className="evidence-actions mt-2 space-y-2">
      {seekable.map((e) => (
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
    <div id={`artifact-decision-${item.id}`} className={artifactRowClass(linked, "decision")}>
      <strong className="block break-words text-slate-900 [overflow-wrap:anywhere]">{item.title}</strong>
      <div className="mt-1"><StatusBadge label={statusLabel} status={item.status} /></div>
      {item.rationale || item.decisionStatus ? (
        <p className="mt-1 break-words text-sm leading-relaxed text-slate-600 [overflow-wrap:anywhere]">
          {[item.decisionStatus, item.rationale].filter(Boolean).join(" · ")}
        </p>
      ) : null}
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
    <div id={`artifact-action-${item.id}`} className={artifactRowClass(linked, "action")}>
      <strong className="block break-words text-slate-900 [overflow-wrap:anywhere]">{item.title}</strong>
      <div className="mt-1 flex flex-wrap items-center gap-2 text-sm leading-relaxed text-slate-600">
        <StatusBadge label={statusLabel} status={item.status} />
        <span>{item.ownerDisplayName}</span>
        <DueDateBadge dueAt={item.dueAt} />
        {item.ownerType ? <span>{item.ownerType}</span> : null}
        {item.priority ? <span>{item.priority}</span> : null}
        {item.relativeDate ? <span>{item.relativeDate}</span> : null}
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
    <div id={`artifact-risk-${item.id}`} className={artifactRowClass(linked, "risk")}>
      <strong className="block break-words text-slate-900 [overflow-wrap:anywhere]">{item.title}</strong>
      <div className="mt-1"><StatusBadge label={severityLabel} status={item.severity} /></div>
      {item.likelihood || item.mitigation ? (
        <div className="mt-1 space-y-0.5 text-sm leading-relaxed text-slate-600">
          {item.likelihood ? (
            <p className="break-words [overflow-wrap:anywhere]">Olasılık: {item.likelihood}</p>
          ) : null}
          {item.mitigation ? (
            <p className="break-words [overflow-wrap:anywhere]">Azaltma: {item.mitigation}</p>
          ) : null}
        </div>
      ) : null}
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
    <div id={`artifact-commitment-${item.id}`} className={artifactRowClass(linked, "commitment")}>
      <strong className="block break-words text-slate-900 [overflow-wrap:anywhere]">{item.statement}</strong>
      <div className="mt-1 flex flex-wrap items-center gap-2 text-sm leading-relaxed text-slate-600">
        <StatusBadge label={statusLabel} status={item.status} />
        <span>{item.ownerDisplayName}</span>
        <DueDateBadge dueAt={item.dueAt} />
      </div>
      <EvidenceButtons evidence={item.evidence} onEvidence={onEvidence} jumpLabel={jumpLabel} />
    </div>
  );
}
