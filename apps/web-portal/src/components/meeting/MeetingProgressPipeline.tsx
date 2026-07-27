import { Check, Circle, Loader2, Sparkles, X } from "lucide-react";
import type { MeetingDetailResponse } from "@/api/types";
import { StatusBadge } from "@/components/qa/StatusBadge";
import { useI18n } from "@/i18n";
import {
  deriveMeetingPipelineStages,
  draftNotesNeedingSubmit,
  hasPendingNoteApprovals,
  type MeetingPipelineStage,
  type MeetingPipelineStageId,
} from "@/lib/meetingPipeline";
import { meetingNeedsProcessingPoll } from "@/lib/meetingProcessing";

const STAGE_ICONS: Record<MeetingPipelineStageId, typeof Sparkles> = {
  CONVERSATION: Circle,
  AI_ANALYSIS: Sparkles,
  NOTES: Sparkles,
  REVIEW: Check,
};

export function MeetingProgressPipeline({
  detail,
  hasConversation,
  lastUpdated,
}: {
  detail: MeetingDetailResponse;
  hasConversation: boolean;
  lastUpdated?: Date;
}) {
  const { t, tb } = useI18n();
  const stages = deriveMeetingPipelineStages(detail, hasConversation);
  const polling = meetingNeedsProcessingPoll(detail);
  const allDone = stages.every((s) => s.state === "done");
  const failed = detail.meeting.status === "FAILED";
  const reviewActive = stages.find((s) => s.id === "REVIEW")?.state === "active";
  const needsSubmit = draftNotesNeedingSubmit(detail).length > 0;
  const needsDecide = hasPendingNoteApprovals(detail);

  if (allDone && !failed) {
    return (
      <div
        className="flex flex-wrap items-center gap-3 rounded-2xl border border-emerald-200/80 bg-gradient-to-r from-emerald-50/90 via-teal-50/70 to-violet-50/60 px-4 py-3"
        role="status"
      >
        <span className="flex h-9 w-9 items-center justify-center rounded-xl bg-emerald-500 text-white shadow-md shadow-emerald-200">
          <Check className="h-5 w-5" aria-hidden />
        </span>
        <div className="min-w-0 flex-1">
          <p className="font-semibold text-emerald-900">{t("meeting.pipeline.completeTitle")}</p>
          <p className="text-xs text-emerald-800/80">{t("meeting.pipeline.completeHint")}</p>
        </div>
        <StatusBadge label={tb("meetingStatus", detail.meeting.status)} status={detail.meeting.status} />
      </div>
    );
  }

  return (
    <div
      className={[
        "rounded-2xl border px-4 py-4 shadow-lg backdrop-blur-xl",
        failed
          ? "border-red-200/80 bg-gradient-to-br from-red-50/80 to-rose-50/60"
          : "border-violet-200/70 bg-gradient-to-br from-violet-50/80 via-white/60 to-sky-50/50 shadow-violet-100/40",
      ].join(" ")}
      role="status"
      aria-live="polite"
    >
      <div className="mb-4 flex flex-wrap items-center justify-between gap-2">
        <div>
          <p className="text-sm font-bold text-slate-900">
            {failed ? t("meeting.pipeline.failedTitle") : t("meeting.pipeline.title")}
          </p>
          <p className="text-xs text-slate-600">
            {failed ? t("meeting.pipeline.failedHint") : t("meeting.pipeline.hint")}
          </p>
        </div>
        {polling && lastUpdated ? (
          <span className="text-xs text-violet-700/80">
            {t("meeting.lastUpdated")} {lastUpdated.toLocaleTimeString()}
          </span>
        ) : null}
      </div>

      <ol className="grid gap-2 sm:grid-cols-4">
        {stages.map((stage, index) => (
          <StageStep key={stage.id} stage={stage} index={index} isLast={index === stages.length - 1} />
        ))}
      </ol>

      {reviewActive ? (
        <div className="mt-4 flex flex-wrap items-center justify-between gap-3 rounded-xl border border-amber-200/80 bg-amber-50/70 px-4 py-3">
          <p className="text-sm text-amber-950">
            {needsDecide ? t("meeting.review.pendingHint") : t("meeting.review.draftHint")}
          </p>
          <a href="#meeting-review" className="btn-primary px-3 py-1.5 text-xs">
            {needsSubmit ? t("meeting.review.goSubmit") : t("meeting.review.goDecide")}
          </a>
        </div>
      ) : null}
    </div>
  );
}

function StageStep({
  stage,
  index,
  isLast,
}: {
  stage: MeetingPipelineStage;
  index: number;
  isLast: boolean;
}) {
  const { t } = useI18n();
  const Icon = STAGE_ICONS[stage.id];

  const tone =
    stage.state === "done"
      ? "border-emerald-300/80 bg-emerald-50/70 text-emerald-800"
      : stage.state === "active"
        ? "border-violet-400/80 bg-violet-100/70 text-violet-900 ring-2 ring-violet-300/50"
        : stage.state === "failed"
          ? "border-red-300/80 bg-red-50/70 text-red-800"
          : "border-white/80 bg-white/50 text-slate-500";

  return (
    <li className="relative flex min-w-0 flex-col items-stretch">
      <div
        className={[
          "flex flex-1 flex-col items-center rounded-xl border px-2 py-3 text-center transition",
          tone,
          stage.state === "active" ? "meeting-detail-pulse-border" : "",
        ].join(" ")}
      >
        <span
          className={[
            "mb-2 flex h-8 w-8 items-center justify-center rounded-full",
            stage.state === "done"
              ? "bg-emerald-500 text-white"
              : stage.state === "active"
                ? "bg-violet-600 text-white meeting-detail-pulse"
                : stage.state === "failed"
                  ? "bg-red-500 text-white"
                  : "bg-slate-200/80 text-slate-500",
          ].join(" ")}
        >
          {stage.state === "active" && stage.id !== "REVIEW" ? (
            <Loader2 className="h-4 w-4 animate-spin" aria-hidden />
          ) : stage.state === "done" ? (
            <Check className="h-4 w-4" aria-hidden />
          ) : stage.state === "failed" ? (
            <X className="h-4 w-4" aria-hidden />
          ) : (
            <Icon className="h-3.5 w-3.5" aria-hidden />
          )}
        </span>
        <span className="text-[10px] font-bold uppercase tracking-wide leading-tight">
          {t(`meeting.pipeline.stage.${stage.id}`)}
        </span>
        <span className="mt-1 text-[10px] opacity-80">
          {stage.id === "REVIEW" && stage.state === "active"
            ? t("meeting.pipeline.state.review")
            : t(`meeting.pipeline.state.${stage.state}`)}
        </span>
      </div>
      {!isLast ? (
        <span
          className="pointer-events-none absolute -right-1 top-1/2 hidden h-0.5 w-2 -translate-y-1/2 bg-violet-200 sm:block"
          aria-hidden
        />
      ) : null}
      <span className="sr-only">
        {t("meeting.pipeline.stepLabel", { step: index + 1, stage: t(`meeting.pipeline.stage.${stage.id}`) })}
      </span>
    </li>
  );
}
