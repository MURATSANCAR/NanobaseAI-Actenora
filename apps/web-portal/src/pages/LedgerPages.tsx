import { useQuery } from "@tanstack/react-query";
import { useMemo, useState } from "react";
import { Link } from "react-router-dom";
import { useApi } from "../api/ApiProvider";
import { queryKeys } from "../api/client";
import type { ArtifactStatus } from "../api/types";
import { AsyncState, PageHeader, PaginationBar } from "../components/ui/AsyncState";
import { useI18n } from "../i18n";

const DECISION_STATUSES: ArtifactStatus[] = ["PENDING_APPROVAL", "APPROVED", "REJECTED"];
const ACTION_STATUSES: ArtifactStatus[] = ["OPEN", "COMPLETED", "PENDING_APPROVAL"];

export function DecisionLedgerPage() {
  const api = useApi();
  const { t, tb } = useI18n();
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
      <PageHeader title={t("decisions.title")} description={t("decisions.description")} />
      <label className="filter-bar">
        {t("filter.status")}
        <select
          value={status}
          onChange={(e) => {
            setStatus(e.target.value as ArtifactStatus | "");
            setCursor(undefined);
          }}
        >
          <option value="">{t("filter.all")}</option>
          {DECISION_STATUSES.map((s) => (
            <option key={s} value={s}>
              {tb("artifactStatus", s)}
            </option>
          ))}
        </select>
      </label>
      <AsyncState status={asyncStatus} error={q.error}>
        <ul className="data-table">
          {q.data?.items.map((d) => (
            <li key={d.id} className="data-row">
              <span>{d.title}</span>
              <span>{tb("artifactStatus", d.status)}</span>
              <Link to={`/meetings/${d.meetingId}`}>{t("common.openMeeting")}</Link>
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
  const { t, tb } = useI18n();
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
      <PageHeader title={t("actions.title")} description={t("actions.description")} />
      <label className="filter-bar">
        {t("filter.status")}
        <select
          value={status}
          onChange={(e) => {
            setStatus(e.target.value as ArtifactStatus | "");
            setCursor(undefined);
          }}
        >
          <option value="">{t("filter.all")}</option>
          {ACTION_STATUSES.map((s) => (
            <option key={s} value={s}>
              {tb("artifactStatus", s)}
            </option>
          ))}
        </select>
      </label>
      <AsyncState status={asyncStatus} error={q.error}>
        <ul className="data-table">
          {q.data?.items.map((a) => (
            <li key={a.id} className="data-row">
              <span>{a.title}</span>
              <span>{tb("artifactStatus", a.status)}</span>
              <span className="muted">{a.ownerDisplayName}</span>
              <Link to={`/meetings/${a.meetingId}`}>{t("common.openMeeting")}</Link>
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
  const { t, tb } = useI18n();
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
      <PageHeader title={t("commitments.title")} description={t("commitments.description")} />
      <AsyncState status={asyncStatus} error={q.error}>
        <ul className="data-table">
          {q.data?.items.map((c) => (
            <li key={c.id} className="data-row">
              <span>{c.statement}</span>
              <span>{tb("artifactStatus", c.status)}</span>
              <span className="muted">{c.ownerDisplayName}</span>
              <Link to={`/meetings/${c.meetingId}`}>{t("common.openMeeting")}</Link>
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
