import type { ApprovalRecord } from "@/api/types";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { Link } from "react-router-dom";
import { useApi, useApiMode } from "@/api/ApiProvider";
import { portalMutationsEnabled, queryKeys, resolvePortalAuthMode } from "@/api/client";
import { StatusBadge } from "@/components/qa/StatusBadge";
import { useI18n } from "@/i18n";

type PendingApprovalsPanelProps = {
  meetingId: string;
  items: ApprovalRecord[];
  canDecide: boolean;
  showMeetingLink?: boolean;
};

export function PendingApprovalsPanel({
  meetingId,
  items,
  canDecide,
  showMeetingLink = false,
}: PendingApprovalsPanelProps) {
  const api = useApi();
  const apiMode = useApiMode();
  const { t, tb } = useI18n();
  const qc = useQueryClient();
  const [comments, setComments] = useState<Record<string, string>>({});
  const mutationsEnabled = portalMutationsEnabled(apiMode, resolvePortalAuthMode());

  const decideMutation = useMutation({
    mutationFn: ({
      approvalId,
      decision,
      comment,
    }: {
      approvalId: string;
      decision: "APPROVE" | "REJECT";
      comment?: string;
    }) => api.decideApproval(approvalId, decision, comment),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: queryKeys.meetingDetail(meetingId) });
      void qc.invalidateQueries({ queryKey: ["decisions"] });
      void qc.invalidateQueries({ queryKey: queryKeys.dashboard });
    },
  });

  const pending = items.filter((a) => a.status === "PENDING");
  if (!pending.length) return null;

  return (
    <div className="space-y-3">
      {!showMeetingLink ? (
        <h3 className="text-xs font-bold uppercase tracking-wide text-violet-700">
          {t("approvals.inboxTitle")}
        </h3>
      ) : null}
      {pending.map((item) => (
        <article
          key={item.id}
          className="rounded-xl border border-violet-200/70 bg-violet-50/40 p-4"
          aria-label={t("meeting.pendingApproval", { type: tb("artifactType", item.artifactType) })}
        >
          <div className="flex flex-wrap items-start justify-between gap-2">
            <div>
              <p className="text-sm font-semibold text-slate-900">
                {t("meeting.pendingApproval", { type: tb("artifactType", item.artifactType) })}
              </p>
              <StatusBadge label={tb("approvalStatus", item.status)} status={item.status} />
            </div>
            {showMeetingLink ? (
              <Link to={`/meetings/${meetingId}`} className="btn-secondary px-3 py-1.5 text-xs">
                {t("common.openMeeting")}
              </Link>
            ) : null}
          </div>

          {canDecide && mutationsEnabled ? (
            <>
              <label className="mt-3 block">
                <span className="label-text">{t("approvals.commentLabel")}</span>
                <textarea
                  className="input-field min-h-[4rem]"
                  value={comments[item.id] ?? ""}
                  onChange={(e) => setComments((c) => ({ ...c, [item.id]: e.target.value }))}
                  placeholder={t("approvals.commentPlaceholder")}
                />
              </label>
              <div className="mt-2 flex flex-wrap gap-2">
                <button
                  type="button"
                  className="btn-primary"
                  disabled={decideMutation.isPending}
                  onClick={() =>
                    decideMutation.mutate({
                      approvalId: item.id,
                      decision: "APPROVE",
                      comment: comments[item.id] || undefined,
                    })
                  }
                >
                  {t("meeting.approve")}
                </button>
                <button
                  type="button"
                  className="btn-secondary"
                  disabled={decideMutation.isPending}
                  onClick={() =>
                    decideMutation.mutate({
                      approvalId: item.id,
                      decision: "REJECT",
                      comment: comments[item.id] || undefined,
                    })
                  }
                >
                  {t("meeting.reject")}
                </button>
              </div>
            </>
          ) : null}

          {decideMutation.isError ? (
            <p className="mt-2 text-sm text-red-600" role="alert">
              {(decideMutation.error as Error).message}
            </p>
          ) : null}
        </article>
      ))}
    </div>
  );
}
