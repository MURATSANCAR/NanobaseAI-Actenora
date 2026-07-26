import { useQuery } from "@tanstack/react-query";
import { useMemo, useState } from "react";
import { Link } from "react-router-dom";
import { useApi } from "@/api/ApiProvider";
import { queryKeys } from "@/api/client";
import type { ArtifactStatus } from "@/api/types";
import { PageShell } from "@/components/qa/PageShell";
import { StatusBadge } from "@/components/qa/StatusBadge";
import { AsyncState, DataTable, FilterCard, PaginationBar } from "@/components/ui/AsyncState";
import { useI18n } from "@/i18n";

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
    <PageShell titleKey="decisions.title" subtitleKey="decisions.description" maxWidth="max-w-7xl">
      <FilterCard>
        <label className="block max-w-xs">
          <span className="label-text">{t("filter.status")}</span>
          <select
            className="input-field"
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
      </FilterCard>
      <AsyncState status={asyncStatus} error={q.error} emptyTitle={t("async.empty")} emptyDescription={t("decisions.description")}>
        <DataTable
          headers={["Title", t("filter.status"), ""]}
          rows={
            q.data?.items.map((d) => [
              d.title,
              <StatusBadge key={`${d.id}-s`} label={tb("artifactStatus", d.status)} status={d.status} />,
              <Link key={`${d.id}-m`} to={`/meetings/${d.meetingId}`} className="btn-secondary px-3 py-1.5 text-xs">
                {t("common.openMeeting")}
              </Link>,
            ]) ?? []
          }
        />
        <PaginationBar
          nextCursor={q.data?.nextCursor}
          onNext={() => setCursor(q.data?.nextCursor ?? undefined)}
          onReset={() => setCursor(undefined)}
        />
      </AsyncState>
    </PageShell>
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
    <PageShell titleKey="actions.title" subtitleKey="actions.description" maxWidth="max-w-7xl">
      <FilterCard>
        <label className="block max-w-xs">
          <span className="label-text">{t("filter.status")}</span>
          <select
            className="input-field"
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
      </FilterCard>
      <AsyncState status={asyncStatus} error={q.error} emptyTitle={t("async.empty")} emptyDescription={t("actions.description")}>
        <DataTable
          headers={["Title", t("filter.status"), "Owner", ""]}
          rows={
            q.data?.items.map((a) => [
              a.title,
              <StatusBadge key={`${a.id}-s`} label={tb("artifactStatus", a.status)} status={a.status} />,
              <span key={`${a.id}-o`} className="text-slate-500">{a.ownerDisplayName}</span>,
              <Link key={`${a.id}-m`} to={`/meetings/${a.meetingId}`} className="btn-secondary px-3 py-1.5 text-xs">
                {t("common.openMeeting")}
              </Link>,
            ]) ?? []
          }
        />
        <PaginationBar
          nextCursor={q.data?.nextCursor}
          onNext={() => setCursor(q.data?.nextCursor ?? undefined)}
          onReset={() => setCursor(undefined)}
        />
      </AsyncState>
    </PageShell>
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
    <PageShell titleKey="commitments.title" subtitleKey="commitments.description" maxWidth="max-w-7xl">
      <AsyncState status={asyncStatus} error={q.error} emptyTitle={t("async.empty")} emptyDescription={t("commitments.description")}>
        <DataTable
          headers={["Statement", t("filter.status"), "Owner", ""]}
          rows={
            q.data?.items.map((c) => [
              c.statement,
              <StatusBadge key={`${c.id}-s`} label={tb("artifactStatus", c.status)} status={c.status} />,
              <span key={`${c.id}-o`} className="text-slate-500">{c.ownerDisplayName}</span>,
              <Link key={`${c.id}-m`} to={`/meetings/${c.meetingId}`} className="btn-secondary px-3 py-1.5 text-xs">
                {t("common.openMeeting")}
              </Link>,
            ]) ?? []
          }
        />
        <PaginationBar
          nextCursor={q.data?.nextCursor}
          onNext={() => setCursor(q.data?.nextCursor ?? undefined)}
          onReset={() => setCursor(undefined)}
        />
      </AsyncState>
    </PageShell>
  );
}
