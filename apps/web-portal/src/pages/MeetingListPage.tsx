import { useQuery } from "@tanstack/react-query";
import { useMemo, useState } from "react";
import { Link } from "react-router-dom";
import { useApi } from "../api/ApiProvider";
import { queryKeys } from "../api/client";
import type { MeetingOccurrenceStatus } from "../api/types";
import { AsyncState, PageHeader, PaginationBar } from "../components/ui/AsyncState";

export function MeetingListPage() {
  const api = useApi();
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
      <PageHeader
        title="Meetings"
        description="Filter and open meeting intelligence workspaces."
      />
      <form
        className="filter-bar"
        role="search"
        onSubmit={(e) => {
          e.preventDefault();
          setCursor(undefined);
        }}
      >
        <label>
          Search
          <input
            value={q}
            onChange={(e) => {
              setQ(e.target.value);
              setCursor(undefined);
            }}
            placeholder="Title"
          />
        </label>
        <label>
          Status
          <select
            value={status}
            onChange={(e) => {
              setStatus(e.target.value as MeetingOccurrenceStatus | "");
              setCursor(undefined);
            }}
          >
            <option value="">All</option>
            <option value="READY">READY</option>
            <option value="PROCESSING">PROCESSING</option>
            <option value="ENDED">ENDED</option>
            <option value="SCHEDULED">SCHEDULED</option>
            <option value="FAILED">FAILED</option>
          </select>
        </label>
      </form>
      <AsyncState status={asyncStatus} error={query.error} emptyMessage="No meetings match.">
        <ul className="data-table" aria-label="Meeting list">
          {query.data?.items.map((m) => (
            <li key={m.id} className="data-row">
              <Link to={`/meetings/${m.id}`}>{m.title}</Link>
              <span>{m.status}</span>
              <span className="muted">{new Date(m.scheduledStartAt).toLocaleString()}</span>
              <span className="muted">{m.participantCount} people</span>
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
