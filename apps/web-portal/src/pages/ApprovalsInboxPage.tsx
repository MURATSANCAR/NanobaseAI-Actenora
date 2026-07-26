import { Navigate } from "react-router-dom";
import { useQuery } from "@tanstack/react-query";
import { useApi } from "@/api/ApiProvider";
import { queryKeys } from "@/api/client";
import { PendingApprovalsPanel } from "@/components/meeting/PendingApprovalsPanel";
import { PageShell } from "@/components/qa/PageShell";
import { AsyncState } from "@/components/ui/AsyncState";
import { useAuth } from "@/auth/AuthProvider";
import { useI18n } from "@/i18n";

export function ApprovalsInboxPage() {
  const api = useApi();
  const auth = useAuth();
  const { t } = useI18n();

  const q = useQuery({
    queryKey: queryKeys.approvalsPending,
    queryFn: () => api.listPendingApprovals(),
    enabled: auth.canApprove,
  });

  if (!auth.isLoading && !auth.canApprove) {
    return <Navigate to="/" replace />;
  }

  const status =
    q.isLoading ? "loading" : q.isError ? "error" : !q.data?.groups.length ? "empty" : "ready";

  return (
    <PageShell titleKey="approvals.title" subtitleKey="approvals.description" maxWidth="max-w-3xl">
      <AsyncState
        status={status}
        error={q.error}
        emptyTitle={t("approvals.emptyTitle")}
        emptyDescription={t("approvals.emptyDescription")}
      >
        <div className="space-y-6">
          {q.data?.groups.map((group) => (
            <section key={group.meetingId} className="card-static space-y-3 p-4 sm:p-5">
              <h2 className="text-base font-bold text-slate-900">{group.meetingTitle}</h2>
              <PendingApprovalsPanel
                meetingId={group.meetingId}
                items={group.items}
                canDecide={auth.canApprove}
                showMeetingLink
              />
            </section>
          ))}
        </div>
      </AsyncState>
    </PageShell>
  );
}
