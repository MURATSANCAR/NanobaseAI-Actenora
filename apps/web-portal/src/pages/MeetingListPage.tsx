import { useQuery } from "@tanstack/react-query";
import { useMemo, useState } from "react";
import { Link } from "react-router-dom";
import { useApi } from "../api/ApiProvider";
import { queryKeys } from "../api/client";
import type { MeetingOccurrenceStatus } from "../api/types";
import { AsyncState, PageHeader, PaginationBar } from "../components/ui/AsyncState";
import { useI18n } from "../i18n";

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
    <section className="page">
      <PageHeader title={t("meetings.title")} description={t("meetings.description")} />
      <form
        className="filter-bar"
        role="search"
        onSubmit={(e) => {
          e.preventDefault();
          setCursor(undefined);
        }}
      >
        <label>
          {t("filter.search")}
          <input
            value={q}
            onChange={(e) => {
              setQ(e.target.value);
              setCursor(undefined);
            }}
            placeholder={t("filter.titlePlaceholder")}
          />
        </label>
        <label>
          {t("filter.status")}
          <select
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
      <AsyncState status={asyncStatus} error={query.error} emptyMessage={t("meetings.empty")}>
        <ul className="data-table" aria-label="Meeting list">
          {query.data?.items.map((m) => (
            <li key={m.id} className="data-row">
              <Link to={`/meetings/${m.id}`}>{m.title}</Link>
              <span>{tb("meetingStatus", m.status)}</span>
              <span className="muted">{new Date(m.scheduledStartAt).toLocaleString()}</span>
              <span className="muted">
                {m.participantCount} {t("common.people")}
              </span>
            </li>
          ))}
        </ul>
        <PaginationBar
          nextCursor={query.data?.nextCursor}
          onNext={() => setCursor(query.data?.nextCursor ?? undefined)}
          onReset={() => setCursor(undefined)}
          disabled={query.isFetching}
        />
      </AsyncState>
    </section>
  );
}
