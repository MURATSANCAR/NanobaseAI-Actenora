import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  AlertTriangle,
  CalendarClock,
  Check,
  CheckSquare,
  ClipboardCheck,
  Handshake,
  MessageSquareWarning,
  X,
} from "lucide-react";
import { useState, type FormEvent, type ReactNode } from "react";
import { Link } from "react-router-dom";
import { useApi } from "@/api/ApiProvider";
import { queryKeys } from "@/api/client";
import type { WorkAction } from "@/api/types";
import { useAuth } from "@/auth/AuthProvider";
import { PageShell } from "@/components/qa/PageShell";
import { StatusBadge } from "@/components/qa/StatusBadge";
import { AsyncState } from "@/components/ui/AsyncState";
import { DueDateBadge } from "@/components/ui/DueDateBadge";
import { useI18n } from "@/i18n";

type RequestDialog =
  | { mode: "dispute"; action: WorkAction }
  | { mode: "dueDate"; action: WorkAction }
  | null;

export function MyWorkPage() {
  const api = useApi();
  const auth = useAuth();
  const queryClient = useQueryClient();
  const { t } = useI18n();
  const [dialog, setDialog] = useState<RequestDialog>(null);
  const [feedback, setFeedback] = useState<string | null>(null);

  const query = useQuery({
    queryKey: queryKeys.myWork,
    queryFn: () => api.getMyWork(),
  });

  const refresh = async () => {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: queryKeys.myWork }),
      queryClient.invalidateQueries({ queryKey: queryKeys.dashboard }),
      queryClient.invalidateQueries({ queryKey: ["actions"] }),
    ]);
  };

  const complete = useMutation({
    mutationFn: (actionId: string) => api.completeAction(actionId),
    onSuccess: async () => {
      setFeedback(t("myWork.completeSuccess"));
      await refresh();
    },
  });

  const dispute = useMutation({
    mutationFn: (input: { actionId: string; reason: string; proposedTitle?: string }) =>
      api.disputeAction(input.actionId, {
        reason: input.reason,
        proposedTitle: input.proposedTitle,
      }),
    onSuccess: async () => {
      setDialog(null);
      setFeedback(t("myWork.requestSuccess"));
      await refresh();
    },
  });

  const dueDate = useMutation({
    mutationFn: (input: { actionId: string; requestedDueDate: string; reason: string }) =>
      api.requestActionDueDateChange(input.actionId, {
        requestedDueDate: input.requestedDueDate,
        reason: input.reason,
      }),
    onSuccess: async () => {
      setDialog(null);
      setFeedback(t("myWork.requestSuccess"));
      await refresh();
    },
  });

  const status =
    query.isLoading ? "loading" : query.isError ? "error" : !query.data ? "empty" : "ready";
  const mutationError = complete.error ?? dispute.error ?? dueDate.error;
  const canEdit = auth.can("meetings:edit");

  return (
    <PageShell titleKey="myWork.title" subtitleKey="myWork.description" maxWidth="max-w-7xl">
      <AsyncState status={status} error={query.error}>
        {query.data ? (
          <div className="space-y-4">
            {feedback ? (
              <div className="flex items-center justify-between gap-3 rounded-xl border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm text-emerald-800">
                <span>{feedback}</span>
                <button type="button" onClick={() => setFeedback(null)} aria-label={t("common.close")}>
                  <X className="h-4 w-4" />
                </button>
              </div>
            ) : null}
            {mutationError ? (
              <div className="rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700" role="alert">
                {mutationError.message}
              </div>
            ) : null}

            <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
              <Metric
                icon={<CheckSquare className="h-4 w-4" />}
                label={t("myWork.assigned")}
                value={query.data.assignedActions.length}
              />
              <Metric
                icon={<CalendarClock className="h-4 w-4" />}
                label={t("myWork.dueSoon")}
                value={query.data.dueSoonActions.length}
              />
              <Metric
                icon={<AlertTriangle className="h-4 w-4" />}
                label={t("myWork.overdue")}
                value={query.data.overdueActions.length}
              />
              <Metric
                icon={<ClipboardCheck className="h-4 w-4" />}
                label={t("myWork.pendingApprovals")}
                value={query.data.pendingApprovals.length}
              />
            </div>

            <div className="grid items-start gap-4 xl:grid-cols-2">
              <ActionSection
                title={t("myWork.overdue")}
                empty={t("myWork.noOverdue")}
                actions={query.data.overdueActions}
                canEdit={canEdit}
                busyId={complete.isPending ? complete.variables : null}
                onComplete={(action) => complete.mutate(action.id)}
                onDispute={(action) => setDialog({ mode: "dispute", action })}
                onDueDate={(action) => setDialog({ mode: "dueDate", action })}
              />
              <ActionSection
                title={t("myWork.dueSoon")}
                empty={t("myWork.noDueSoon")}
                actions={query.data.dueSoonActions}
                canEdit={canEdit}
                busyId={complete.isPending ? complete.variables : null}
                onComplete={(action) => complete.mutate(action.id)}
                onDispute={(action) => setDialog({ mode: "dispute", action })}
                onDueDate={(action) => setDialog({ mode: "dueDate", action })}
              />
              <ActionSection
                title={t("myWork.assigned")}
                empty={t("myWork.noAssigned")}
                actions={query.data.assignedActions}
                canEdit={canEdit}
                busyId={complete.isPending ? complete.variables : null}
                onComplete={(action) => complete.mutate(action.id)}
                onDispute={(action) => setDialog({ mode: "dispute", action })}
                onDueDate={(action) => setDialog({ mode: "dueDate", action })}
              />

              <section className="card-static space-y-3 p-4 sm:p-5">
                <div className="flex items-center justify-between gap-3">
                  <h2 className="flex items-center gap-2 font-semibold text-slate-900">
                    <ClipboardCheck className="h-4 w-4 text-violet-600" />
                    {t("myWork.pendingApprovals")}
                  </h2>
                  <Link to="/approvals" className="text-xs font-semibold text-violet-700 hover:text-violet-900">
                    {t("myWork.openApprovals")}
                  </Link>
                </div>
                {query.data.pendingApprovals.length ? (
                  <ul className="space-y-2">
                    {query.data.pendingApprovals.map((approval) => (
                      <li key={approval.id} className="rounded-xl border border-slate-200 bg-white/70 p-3">
                        <div className="flex items-center justify-between gap-3">
                          <span className="text-sm font-medium text-slate-800">
                            {approval.subjectType}
                          </span>
                          <span className="text-xs text-slate-500">
                            {new Date(approval.updatedAt).toLocaleString()}
                          </span>
                        </div>
                      </li>
                    ))}
                  </ul>
                ) : (
                  <p className="text-sm text-slate-500">{t("myWork.noApprovals")}</p>
                )}
              </section>

              <section className="card-static space-y-3 p-4 sm:p-5 xl:col-span-2">
                <h2 className="flex items-center gap-2 font-semibold text-slate-900">
                  <Handshake className="h-4 w-4 text-teal-600" />
                  {t("myWork.recentCommitments")}
                </h2>
                {query.data.recentCommitments.length ? (
                  <div className="grid gap-2 md:grid-cols-2">
                    {query.data.recentCommitments.map((commitment) => (
                      <Link
                        key={commitment.id}
                        to={`/meetings/${commitment.meetingId}`}
                        className="rounded-xl border border-slate-200 bg-white/70 p-3 transition hover:border-teal-300 hover:bg-teal-50/40"
                      >
                        <p className="text-sm font-medium text-slate-900">{commitment.text}</p>
                        <p className="mt-1 text-xs text-slate-500">
                          {commitment.owner ?? t("common.unassigned")}
                          {commitment.dueDate ? ` · ${commitment.dueDate}` : ""}
                        </p>
                      </Link>
                    ))}
                  </div>
                ) : (
                  <p className="text-sm text-slate-500">{t("myWork.noCommitments")}</p>
                )}
              </section>
            </div>
          </div>
        ) : null}
      </AsyncState>

      {dialog ? (
        <ActionRequestDialog
          dialog={dialog}
          pending={dispute.isPending || dueDate.isPending}
          onClose={() => setDialog(null)}
          onSubmit={(payload) => {
            if (dialog.mode === "dispute") {
              dispute.mutate({
                actionId: dialog.action.id,
                reason: payload.reason,
                proposedTitle: payload.proposedTitle,
              });
            } else {
              dueDate.mutate({
                actionId: dialog.action.id,
                requestedDueDate: payload.requestedDueDate,
                reason: payload.reason,
              });
            }
          }}
        />
      ) : null}
    </PageShell>
  );
}

function Metric({ icon, label, value }: { icon: ReactNode; label: string; value: number }) {
  return (
    <div className="card-static flex items-center gap-3 p-4">
      <span className="flex h-9 w-9 items-center justify-center rounded-xl bg-violet-100 text-violet-700">
        {icon}
      </span>
      <div>
        <p className="text-2xl font-bold text-slate-900">{value}</p>
        <p className="text-xs font-medium text-slate-500">{label}</p>
      </div>
    </div>
  );
}

function ActionSection({
  title,
  empty,
  actions,
  canEdit,
  busyId,
  onComplete,
  onDispute,
  onDueDate,
}: {
  title: string;
  empty: string;
  actions: WorkAction[];
  canEdit: boolean;
  busyId: string | null;
  onComplete: (action: WorkAction) => void;
  onDispute: (action: WorkAction) => void;
  onDueDate: (action: WorkAction) => void;
}) {
  const { t, tb } = useI18n();
  return (
    <section className="card-static space-y-3 p-4 sm:p-5">
      <h2 className="font-semibold text-slate-900">{title}</h2>
      {actions.length ? (
        <ul className="space-y-2">
          {actions.map((action) => (
            <li key={action.id} className="rounded-xl border border-slate-200 bg-white/75 p-3">
              <div className="flex flex-wrap items-start justify-between gap-2">
                <Link
                  to={`/meetings/${action.meetingId}`}
                  className="min-w-0 flex-1 text-sm font-semibold text-slate-900 hover:text-violet-800"
                >
                  {action.title}
                </Link>
                <StatusBadge label={tb("artifactStatus", action.status)} status={action.status} />
              </div>
              <div className="mt-2 flex flex-wrap items-center gap-2 text-xs text-slate-500">
                <span>{action.owner ?? t("common.unassigned")}</span>
                <DueDateBadge dueAt={action.dueAt} />
              </div>
              {canEdit ? (
                <div className="mt-3 flex flex-wrap gap-2">
                  <button
                    type="button"
                    className="btn-primary px-2.5 py-1.5 text-xs"
                    disabled={busyId === action.id}
                    onClick={() => onComplete(action)}
                  >
                    <Check className="h-3.5 w-3.5" />
                    {t("myWork.complete")}
                  </button>
                  <button
                    type="button"
                    className="btn-secondary px-2.5 py-1.5 text-xs"
                    onClick={() => onDispute(action)}
                  >
                    <MessageSquareWarning className="h-3.5 w-3.5" />
                    {t("myWork.dispute")}
                  </button>
                  <button
                    type="button"
                    className="btn-secondary px-2.5 py-1.5 text-xs"
                    onClick={() => onDueDate(action)}
                  >
                    <CalendarClock className="h-3.5 w-3.5" />
                    {t("myWork.requestDate")}
                  </button>
                </div>
              ) : null}
            </li>
          ))}
        </ul>
      ) : (
        <p className="text-sm text-slate-500">{empty}</p>
      )}
    </section>
  );
}

function ActionRequestDialog({
  dialog,
  pending,
  onClose,
  onSubmit,
}: {
  dialog: Exclude<RequestDialog, null>;
  pending: boolean;
  onClose: () => void;
  onSubmit: (payload: {
    reason: string;
    proposedTitle?: string;
    requestedDueDate: string;
  }) => void;
}) {
  const { t } = useI18n();
  const [reason, setReason] = useState("");
  const [proposedTitle, setProposedTitle] = useState(dialog.action.title);
  const [requestedDueDate, setRequestedDueDate] = useState(
    dialog.action.dueAt?.slice(0, 10) ?? "",
  );

  const submit = (event: FormEvent) => {
    event.preventDefault();
    onSubmit({ reason, proposedTitle, requestedDueDate });
  };

  return (
    <div className="fixed inset-0 z-[120] flex items-center justify-center bg-slate-950/45 p-4" role="presentation">
      <form
        className="w-full max-w-lg space-y-4 rounded-2xl border border-slate-200 bg-white p-5 shadow-2xl"
        role="dialog"
        aria-modal="true"
        aria-labelledby="action-request-title"
        onSubmit={submit}
      >
        <div className="flex items-start justify-between gap-3">
          <div>
            <h2 id="action-request-title" className="font-semibold text-slate-900">
              {dialog.mode === "dispute" ? t("myWork.disputeTitle") : t("myWork.dateRequestTitle")}
            </h2>
            <p className="mt-1 text-sm text-slate-500">{dialog.action.title}</p>
          </div>
          <button type="button" onClick={onClose} aria-label={t("common.close")}>
            <X className="h-4 w-4" />
          </button>
        </div>
        {dialog.mode === "dispute" ? (
          <label className="block">
            <span className="label-text">{t("myWork.proposedTitle")}</span>
            <input
              className="input-field"
              value={proposedTitle}
              onChange={(event) => setProposedTitle(event.target.value)}
              required
            />
          </label>
        ) : (
          <label className="block">
            <span className="label-text">{t("myWork.requestedDate")}</span>
            <input
              className="input-field"
              type="date"
              value={requestedDueDate}
              onChange={(event) => setRequestedDueDate(event.target.value)}
              required
            />
          </label>
        )}
        <label className="block">
          <span className="label-text">{t("myWork.reason")}</span>
          <textarea
            className="input-field min-h-24"
            value={reason}
            onChange={(event) => setReason(event.target.value)}
            required
          />
        </label>
        <div className="flex justify-end gap-2">
          <button type="button" className="btn-secondary" onClick={onClose}>
            {t("common.cancel")}
          </button>
          <button type="submit" className="btn-primary" disabled={pending}>
            {pending ? t("myWork.submitting") : t("myWork.submitRequest")}
          </button>
        </div>
      </form>
    </div>
  );
}
