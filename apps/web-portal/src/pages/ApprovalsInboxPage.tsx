import { useQueries, useQuery } from "@tanstack/react-query";
import { useMemo } from "react";
import { useApi } from "@/api/ApiProvider";
import { queryKeys } from "@/api/client";
import { PendingApprovalsPanel } from "@/components/meeting/PendingApprovalsPanel";
import { PageShell } from "@/components/qa/PageShell";
import { AsyncState } from "@/components/ui/AsyncState";
import { useAuth } from "@/auth/AuthProvider";
import { useI18n } from "@/i18n";

const INBOX_MEETING_LIMIT = 12;

export function ApprovalsInboxPage() {
  const api = useApi();
  const auth = useAuth();
  const { t } = useI18n();

  const dashboardQ = useQuery({
    queryKey: queryKeys.dashboard,
    queryFn: () => api.getDashboard(),
  });

  const meetingIds = useMemo(
    () => (dashboardQ.data?.recentMeetings ?? []).slice(0, INBOX_MEETING_LIMIT).map((m) => m.id),
    [dashboardQ.data?.recentMeetings],
  );

  const detailQueries = useQueries({
    queries: meetingIds.map((id) => ({
      queryKey: queryKeys.meetingDetail(id),
      queryFn: () => api.getMeetingDetail(id),
      enabled: Boolean(id) && Boolean(dashboardQ.data),
    })),
  });

  const groups = useMemo(() => {
    return detailQueries
      .map((q, index) => {
        const meetingId = meetingIds[index];
        if (!meetingId || !q.data) return null;
        const pending = q.data.approvalHistory.filter((a) => a.status === "PENDING");
        if (!pending.length) return null;
        return {
          meetingId,
          title: q.data.meeting.title,
          pending,
        };
      })
      .filter((g): g is NonNullable<typeof g> => g !== null);
  }, [detailQueries, meetingIds]);

  const isLoadingDetails = detailQueries.some((q) => q.isLoading);
  const status =
    dashboardQ.isLoading || isLoadingDetails
      ? "loading"
      : dashboardQ.isError
        ? "error"
        : !groups.length
          ? "empty"
          : "ready";

  return (
    <PageShell titleKey="approvals.title" subtitleKey="approvals.description" maxWidth="max-w-3xl">
      <AsyncState
        status={status}
        error={dashboardQ.error}
        emptyTitle={t("approvals.emptyTitle")}
        emptyDescription={t("approvals.emptyDescription")}
      >
        <div className="space-y-6">
          {groups.map((group) => (
            <section key={group.meetingId} className="card-static space-y-3 p-4 sm:p-5">
              <h2 className="text-base font-bold text-slate-900">{group.title}</h2>
              <PendingApprovalsPanel
                meetingId={group.meetingId}
                items={group.pending}
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
