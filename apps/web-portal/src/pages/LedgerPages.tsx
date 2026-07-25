import { useQuery } from "@tanstack/react-query";
import { useMemo, useState } from "react";
import { Link } from "react-router-dom";
import { useApi } from "../api/ApiProvider";
import { queryKeys } from "../api/client";
import type { ArtifactStatus } from "../api/types";
import { AsyncState, PageHeader, PaginationBar } from "../components/ui/AsyncState";

export function DecisionLedgerPage() {
  const api = useApi();
  const [status, setStatus] = useState<ArtifactStatus | "">("");
  const [cursor, setCursor] = useState<string | undefined>();
  const params = useMemo(
    () => ({ status: status || undefined, cursor, limit: 25 }),
    [status, cursor],
  );
  const q = useQuery({
    queryKey: queryKeys.decisions(params),
    queryFn: () => api.listDecisions(params),
  });
  const asyncStatus =
    q.isLoading ? "loading" : q.isError ? "error" : !q.data?.items.length ? "empty" : "ready";

  return (
    <section className="page">
      <PageHeader title="Decision Ledger" description="Approved and pending decisions across meetings." />
      <label className="filter-bar">
        Status
        <select
          value={status}
          onChange={(e) => {
            setStatus(e.target.value as ArtifactStatus | "");
            setCursor(undefined);
          }}
        >
          <option value="">All</option>
          <option value="PENDING_APPROVAL">PENDING_APPROVAL</option>
          <option value="APPROVED">APPROVED</option>
          <option value="REJECTED">REJECTED</option>
        </select>
      </label>
      <AsyncState status={asyncStatus} error={q.error}>
        <ul className="data-table">
          {q.data?.items.map((d) => (
            <li key={d.id} className="data-row">
              <span>{d.title}</span>
              <span>{d.status}</span>
              <Link to={`/meetings/${d.meetingId}`}>Open meeting</Link>
            </li>
          ))}
        </ul>
        <PaginationBar
          nextCursor={q.data?.nextCursor}
          onNext={() => setCursor(q.data?.nextCursor ?? undefined)}
          onReset={() => setCursor(undefined)}
        />
      </AsyncState>
    </section>
  );
}

export function ActionCenterPage() {
  const api = useApi();
  const [status, setStatus] = useState<ArtifactStatus | "">("");
  const [cursor, setCursor] = useState<string | undefined>();
  const params = useMemo(
    () => ({ status: status || undefined, cursor, limit: 25 }),
    [status, cursor],
  );
  const q = useQuery({
    queryKey: queryKeys.actions(params),
    queryFn: () => api.listActions(params),
  });
  const asyncStatus =
    q.isLoading ? "loading" : q.isError ? "error" : !q.data?.items.length ? "empty" : "ready";

  return (
    <section className="page">
      <PageHeader title="Action Center" description="Track open and completed actions." />
      <label className="filter-bar">
        Status
        <select
          value={status}
          onChange={(e) => {
            setStatus(e.target.value as ArtifactStatus | "");
            setCursor(undefined);
          }}
        >
          <option value="">All</option>
          <option value="OPEN">OPEN</option>
          <option value="COMPLETED">COMPLETED</option>
          <option value="PENDING_APPROVAL">PENDING_APPROVAL</option>
        </select>
      </label>
      <AsyncState status={asyncStatus} error={q.error}>
        <ul className="data-table">
          {q.data?.items.map((a) => (
            <li key={a.id} className="data-row">
              <span>{a.title}</span>
              <span>{a.status}</span>
              <span className="muted">{a.ownerDisplayName}</span>
              <Link to={`/meetings/${a.meetingId}`}>Open meeting</Link>
            </li>
          ))}
        </ul>
        <PaginationBar
          nextCursor={q.data?.nextCursor}
          onNext={() => setCursor(q.data?.nextCursor ?? undefined)}
          onReset={() => setCursor(undefined)}
        />
      </AsyncState>
    </section>
  );
}

export function CommitmentTrackerPage() {
  const api = useApi();
  const [cursor, setCursor] = useState<string | undefined>();
  const params = useMemo(() => ({ cursor, limit: 25 }), [cursor]);
  const q = useQuery({
    queryKey: queryKeys.commitments(params),
    queryFn: () => api.listCommitments(params),
  });
  const asyncStatus =
    q.isLoading ? "loading" : q.isError ? "error" : !q.data?.items.length ? "empty" : "ready";

  return (
    <section className="page">
      <PageHeader title="Commitment Tracker" description="Promises made in meetings." />
      <AsyncState status={asyncStatus} error={q.error}>
        <ul className="data-table">
          {q.data?.items.map((c) => (
            <li key={c.id} className="data-row">
              <span>{c.statement}</span>
              <span>{c.status}</span>
              <span className="muted">{c.ownerDisplayName}</span>
              <Link to={`/meetings/${c.meetingId}`}>Open meeting</Link>
            </li>
          ))}
        </ul>
        <PaginationBar
          nextCursor={q.data?.nextCursor}
          onNext={() => setCursor(q.data?.nextCursor ?? undefined)}
          onReset={() => setCursor(undefined)}
        />
      </AsyncState>
    </section>
  );
}
