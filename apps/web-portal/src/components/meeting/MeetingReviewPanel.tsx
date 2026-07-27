import { useMutation, useQueryClient } from "@tanstack/react-query";
import { ClipboardCheck, Send } from "lucide-react";
import { Link } from "react-router-dom";
import { useApi, useApiMode } from "@/api/ApiProvider";
import { portalMutationsEnabled, queryKeys, resolvePortalAuthMode } from "@/api/client";
import type { MeetingDetailResponse, MeetingNote } from "@/api/types";
import { PendingApprovalsPanel } from "@/components/meeting/PendingApprovalsPanel";
import { useAuth } from "@/auth/AuthProvider";
import { useI18n } from "@/i18n";
import { draftNotesNeedingSubmit, hasPendingNoteApprovals } from "@/lib/meetingPipeline";

export function MeetingReviewPanel({ detail }: { detail: MeetingDetailResponse }) {
  const api = useApi();
  const apiMode = useApiMode();
  const auth = useAuth();
  const { t } = useI18n();
  const qc = useQueryClient();
  const meetingId = detail.meeting.id;
  const mutationsEnabled = portalMutationsEnabled(apiMode, resolvePortalAuthMode());
  const canEdit = auth.can("meetings:edit") && mutationsEnabled;
  // Organizers who submit are the required approver; they may self-approve with edit rights.
  const canDecide = (auth.canApprove || auth.can("meetings:edit")) && mutationsEnabled;
  const drafts = draftNotesNeedingSubmit(detail);
  const pending = hasPendingNoteApprovals(detail);

  const submitMutation = useMutation({
    mutationFn: async (note: MeetingNote) => {
      // Always use the server's current optimistic-lock version — drafts may have
      // advanced via AI regen / saves after the detail payload was cached.
      const fresh = await api.getMeetingDetail(meetingId);
      const latest = fresh.notes.find((n) => n.id === note.id);
      return api.submitNoteForApproval(meetingId, note.id, latest?.version);
    },
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: queryKeys.meetingDetail(meetingId) });
      void qc.invalidateQueries({ queryKey: queryKeys.approvalsPending });
      void qc.invalidateQueries({ queryKey: queryKeys.dashboard });
    },
  });

  if (!drafts.length && !pending) {
    return null;
  }

  return (
    <section
      id="meeting-review"
      className="scroll-mt-20 rounded-2xl border border-amber-200/80 bg-gradient-to-br from-amber-50/90 via-white/70 to-violet-50/60 p-4 shadow-md shadow-amber-100/40 sm:p-5"
      aria-label={t("meeting.review.title")}
    >
      <div className="mb-3 flex flex-wrap items-start gap-3">
        <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-amber-500 text-white shadow-md shadow-amber-200">
          <ClipboardCheck className="h-5 w-5" aria-hidden />
        </span>
        <div className="min-w-0 flex-1">
          <h2 className="text-sm font-bold text-slate-900">{t("meeting.review.title")}</h2>
          <p className="mt-1 text-sm text-slate-600">
            {pending ? t("meeting.review.pendingHint") : t("meeting.review.draftHint")}
          </p>
          {!pending && drafts.length ? (
            <p className="mt-1 text-xs font-medium text-amber-900">{t("meeting.review.twoStepHint")}</p>
          ) : null}
        </div>
        {auth.canApprove ? (
          <Link to="/approvals" className="btn-secondary px-3 py-1.5 text-xs">
            {t("nav.approvals")}
          </Link>
        ) : null}
      </div>

      {drafts.length && !pending ? (
        <div className="space-y-3">
          {drafts.map((note) => (
            <div
              key={note.id}
              className="flex flex-wrap items-center justify-between gap-3 rounded-xl border border-white/80 bg-white/70 px-4 py-3"
            >
              <div>
                <p className="text-sm font-semibold text-slate-800">{t("meeting.review.draftNote")}</p>
                <p className="text-xs text-slate-500">{t("meeting.review.draftNoteHint")}</p>
              </div>
              {canEdit ? (
                <button
                  type="button"
                  className="btn-primary inline-flex items-center gap-2"
                  disabled={submitMutation.isPending}
                  onClick={() => submitMutation.mutate(note)}
                >
                  <Send className="h-4 w-4" aria-hidden />
                  {t("meeting.review.submit")}
                </button>
              ) : (
                <p className="text-xs text-amber-800">{t("meeting.review.needEditPermission")}</p>
              )}
            </div>
          ))}
          {submitMutation.isError ? (
            <p className="text-sm text-red-600" role="alert">
              {(submitMutation.error as Error).message}
            </p>
          ) : null}
        </div>
      ) : null}

      {pending ? (
        <div className="space-y-3">
          {!canDecide ? (
            <p className="rounded-xl border border-amber-200 bg-amber-50/80 px-4 py-3 text-sm text-amber-950">
              {t("meeting.review.needApproverPermission")}
            </p>
          ) : null}
          <PendingApprovalsPanel
            meetingId={meetingId}
            items={detail.approvalHistory}
            canDecide={canDecide}
          />
        </div>
      ) : null}
    </section>
  );
}
