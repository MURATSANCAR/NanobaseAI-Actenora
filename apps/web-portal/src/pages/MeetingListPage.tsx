import { useQuery } from "@tanstack/react-query";
import { useMemo, useState } from "react";
import { Link } from "react-router-dom";
import { useApi } from "@/api/ApiProvider";
import { queryKeys } from "@/api/client";
import type { MeetingOccurrenceStatus } from "@/api/types";
import { PageShell } from "@/components/qa/PageShell";
import { StatusBadge } from "@/components/qa/StatusBadge";
import { AsyncState, DataTable, FilterCard, PaginationBar } from "@/components/ui/AsyncState";
import { useI18n } from "@/i18n";

const MEETING_STATUSES: MeetingOccurrenceStatus[] = [
  "READY",
  "PROCESSING",
  "ENDED",
  "SCHEDULED",
  "FAILED",
];

export function MeetingListPage() {
  const api = useApi();
  const { t, tb } = useI18n();
  const [q, setQ] = useState("");
  const [status, setStatus] = useState<MeetingOccurrenceStatus | "">("");
  const [cursor, setCursor] = useState<string | undefined>();

  const params = useMemo(
    () => ({
      q: q || undefined,
      status: status || undefined,
      cursor,
      limit: 25,
    }),
    [q, status, cursor],
  );

  const query = useQuery({
    queryKey: queryKeys.meetings(params),
    queryFn: () => api.listMeetings(params),
  });

  const asyncStatus =
    query.isLoading
      ? "loading"
      : query.isError
        ? "error"
        : !query.data?.items.length
          ? "empty"
          : "ready";

  return (
    <PageShell titleKey="meetings.title" subtitleKey="meetings.description" maxWidth="max-w-7xl">
      <FilterCard>
        <form
          className="mobile-toolbar"
          role="search"
          onSubmit={(e) => {
            e.preventDefault();
            setCursor(undefined);
          }}
        >
          <label className="min-w-[12rem] flex-1">
            <span className="label-text">{t("filter.search")}</span>
            <input
              className="input-field"
              value={q}
              onChange={(e) => {
                setQ(e.target.value);
                setCursor(undefined);
              }}
              placeholder={t("filter.titlePlaceholder")}
            />
          </label>
          <label className="min-w-[10rem]">
            <span className="label-text">{t("filter.status")}</span>
            <select
              className="input-field"
              value={status}
              onChange={(e) => {
                setStatus(e.target.value as MeetingOccurrenceStatus | "");
                setCursor(undefined);
              }}
            >
              <option value="">{t("filter.all")}</option>
              {MEETING_STATUSES.map((s) => (
                <option key={s} value={s}>
                  {tb("meetingStatus", s)}
                </option>
              ))}
            </select>
          </label>
        </form>
      </FilterCard>

      <AsyncState
        status={asyncStatus}
        error={query.error}
        emptyTitle={t("meetings.empty")}
        emptyDescription={t("meetings.description")}
      >
        <DataTable
          ariaLabel={t("meetings.title")}
          headers={[t("filter.titlePlaceholder"), t("filter.status"), t("meeting.scheduled"), ""]}
          rows={
            query.data?.items.map((m) => [
              <Link key={`${m.id}-title`} to={`/meetings/${m.id}`} className="font-medium text-violet-800 hover:underline">
                {m.title}
              </Link>,
              <StatusBadge key={`${m.id}-status`} label={tb("meetingStatus", m.status)} status={m.status} />,
              <span key={`${m.id}-date`} className="text-slate-500">
                {new Date(m.scheduledStartAt).toLocaleString()}
              </span>,
              <span key={`${m.id}-people`} className="text-slate-500">
                {m.participantCount} {t("common.people")}
              </span>,
            ]) ?? []
          }
        />
        <PaginationBar
          nextCursor={query.data?.nextCursor}
          onNext={() => setCursor(query.data?.nextCursor ?? undefined)}
          onReset={() => setCursor(undefined)}
          disabled={query.isFetching}
        />
      </AsyncState>
    </PageShell>
  );
}
