import { useQuery } from "@tanstack/react-query";
import { useMemo, useState } from "react";
import { Link } from "react-router-dom";
import { Download } from "lucide-react";
import { useApi } from "@/api/ApiProvider";
import { queryKeys } from "@/api/client";
import type { ArtifactStatus } from "@/api/types";
import { PageShell } from "@/components/qa/PageShell";
import { StatusBadge } from "@/components/qa/StatusBadge";
import { DueDateBadge } from "@/components/ui/DueDateBadge";
import { AsyncState, DataTable, FilterCard, PaginationBar } from "@/components/ui/AsyncState";
import { useAuth } from "@/auth/AuthProvider";
import { useI18n } from "@/i18n";
import { isOverdue } from "@/lib/dueDates";
import { exportTableCsv } from "@/lib/export";
import { formatOwner, ownerMatchesUser } from "@/lib/owner";

const DECISION_STATUSES: ArtifactStatus[] = ["PENDING_APPROVAL", "APPROVED", "REJECTED"];
const ACTION_STATUSES: ArtifactStatus[] = ["OPEN", "IN_PROGRESS", "COMPLETED", "CANCELLED", "PENDING_APPROVAL"];

function ClientFilterHint({ active, text }: { active: boolean; text: string }) {
  if (!active) return null;
  return <p className="mt-3 w-full text-xs text-slate-500">{text}</p>;
}

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

  const exportCsv = () => {
    if (!q.data?.items.length) return;
    exportTableCsv(
      "decisions.csv",
      [t("table.title"), t("filter.status")],
      q.data.items.map((d) => [d.title, tb("artifactStatus", d.status)]),
    );
  };

  return (
    <PageShell
      titleKey="decisions.title"
      subtitleKey="decisions.description"
      maxWidth="max-w-7xl"
      heroTrailing={
        q.data?.items.length ? (
          <button type="button" className="btn-secondary px-3 py-1.5 text-xs" onClick={exportCsv}>
            <Download className="h-4 w-4" />
            {t("export.csv")}
          </button>
        ) : null
      }
    >
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
          headers={[t("table.title"), t("filter.status"), ""]}
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
  const auth = useAuth();
  const { t, tb } = useI18n();
  const [status, setStatus] = useState<ArtifactStatus | "">("");
  const [overdueOnly, setOverdueOnly] = useState(false);
  const [mineOnly, setMineOnly] = useState(false);
  const [cursor, setCursor] = useState<string | undefined>();
  const params = useMemo(
    () => ({ status: status || undefined, cursor, limit: 25 }),
    [status, cursor],
  );
  const q = useQuery({
    queryKey: queryKeys.actions(params),
    queryFn: () => api.listActions(params),
  });

  const items = useMemo(() => {
    let rows = q.data?.items ?? [];
    if (overdueOnly) rows = rows.filter((a) => isOverdue(a.dueAt));
    if (mineOnly && auth.user) {
      rows = rows.filter((a) => ownerMatchesUser(a.ownerDisplayName, auth.user!.displayName));
    }
    return rows;
  }, [q.data?.items, overdueOnly, mineOnly, auth.user]);

  const clientFiltered = overdueOnly || mineOnly;
  const filteredEmpty = clientFiltered && (q.data?.items.length ?? 0) > 0 && items.length === 0;
  const asyncStatus =
    q.isLoading ? "loading" : q.isError ? "error" : !items.length ? "empty" : "ready";

  const exportCsv = () => {
    if (!items.length) return;
    exportTableCsv(
      "actions.csv",
      [t("table.title"), t("filter.status"), t("table.owner"), t("table.dueDate")],
      items.map((a) => [
        a.title,
        tb("artifactStatus", a.status),
        formatOwner(a.ownerDisplayName, t("common.unassigned")),
        a.dueAt ?? "",
      ]),
    );
  };

  return (
    <PageShell
      titleKey="actions.title"
      subtitleKey="actions.description"
      maxWidth="max-w-7xl"
      heroTrailing={
        items.length ? (
          <button type="button" className="btn-secondary px-3 py-1.5 text-xs" onClick={exportCsv}>
            <Download className="h-4 w-4" />
            {t("export.csv")}
          </button>
        ) : null
      }
    >
      <FilterCard>
        <div className="flex flex-wrap items-end gap-4">
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
          <label className="flex items-center gap-2 text-sm text-slate-700">
            <input
              type="checkbox"
              checked={overdueOnly}
              onChange={(e) => setOverdueOnly(e.target.checked)}
            />
            {t("filter.overdueOnly")}
          </label>
          <label className="flex items-center gap-2 text-sm text-slate-700">
            <input
              type="checkbox"
              checked={mineOnly}
              onChange={(e) => setMineOnly(e.target.checked)}
            />
            {t("filter.mineOnly")}
          </label>
          <ClientFilterHint active={clientFiltered} text={t("filter.appliesToLoaded")} />
        </div>
      </FilterCard>
      <AsyncState
        status={asyncStatus}
        error={q.error}
        emptyTitle={filteredEmpty ? t("filter.noMatchesTitle") : t("async.empty")}
        emptyDescription={filteredEmpty ? t("filter.noMatchesHint") : t("actions.description")}
      >
        <DataTable
          headers={[t("table.title"), t("filter.status"), t("table.owner"), t("table.dueDate"), ""]}
          rows={
            items.map((a) => [
              a.title,
              <StatusBadge key={`${a.id}-s`} label={tb("artifactStatus", a.status)} status={a.status} />,
              <span key={`${a.id}-o`} className="text-slate-500">
                {formatOwner(a.ownerDisplayName, t("common.unassigned"))}
              </span>,
              <DueDateBadge key={`${a.id}-d`} dueAt={a.dueAt} />,
              <Link key={`${a.id}-m`} to={`/meetings/${a.meetingId}`} className="btn-secondary px-3 py-1.5 text-xs">
                {t("common.openMeeting")}
              </Link>,
            ])
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
  const auth = useAuth();
  const { t, tb } = useI18n();
  const [overdueOnly, setOverdueOnly] = useState(false);
  const [mineOnly, setMineOnly] = useState(false);
  const [cursor, setCursor] = useState<string | undefined>();
  const params = useMemo(() => ({ cursor, limit: 25 }), [cursor]);
  const q = useQuery({
    queryKey: queryKeys.commitments(params),
    queryFn: () => api.listCommitments(params),
  });

  const items = useMemo(() => {
    let rows = q.data?.items ?? [];
    if (overdueOnly) rows = rows.filter((c) => isOverdue(c.dueAt) || c.status === "AT_RISK");
    if (mineOnly && auth.user) {
      rows = rows.filter((c) => ownerMatchesUser(c.ownerDisplayName, auth.user!.displayName));
    }
    return rows;
  }, [q.data?.items, overdueOnly, mineOnly, auth.user]);

  const clientFiltered = overdueOnly || mineOnly;
  const filteredEmpty = clientFiltered && (q.data?.items.length ?? 0) > 0 && items.length === 0;
  const asyncStatus =
    q.isLoading ? "loading" : q.isError ? "error" : !items.length ? "empty" : "ready";

  const exportCsv = () => {
    if (!items.length) return;
    exportTableCsv(
      "commitments.csv",
      [t("table.statement"), t("filter.status"), t("table.owner"), t("table.dueDate")],
      items.map((c) => [
        c.statement,
        tb("artifactStatus", c.status),
        formatOwner(c.ownerDisplayName, t("common.unassigned")),
        c.dueAt ?? "",
      ]),
    );
  };

  return (
    <PageShell
      titleKey="commitments.title"
      subtitleKey="commitments.description"
      maxWidth="max-w-7xl"
      heroTrailing={
        items.length ? (
          <button type="button" className="btn-secondary px-3 py-1.5 text-xs" onClick={exportCsv}>
            <Download className="h-4 w-4" />
            {t("export.csv")}
          </button>
        ) : null
      }
    >
      <FilterCard>
        <div className="flex flex-wrap items-center gap-4">
          <label className="flex items-center gap-2 text-sm text-slate-700">
            <input
              type="checkbox"
              checked={overdueOnly}
              onChange={(e) => setOverdueOnly(e.target.checked)}
            />
            {t("filter.overdueOnly")}
          </label>
          <label className="flex items-center gap-2 text-sm text-slate-700">
            <input
              type="checkbox"
              checked={mineOnly}
              onChange={(e) => setMineOnly(e.target.checked)}
            />
            {t("filter.mineOnly")}
          </label>
          <ClientFilterHint active={clientFiltered} text={t("filter.appliesToLoaded")} />
        </div>
      </FilterCard>
      <AsyncState
        status={asyncStatus}
        error={q.error}
        emptyTitle={filteredEmpty ? t("filter.noMatchesTitle") : t("async.empty")}
        emptyDescription={filteredEmpty ? t("filter.noMatchesHint") : t("commitments.description")}
      >
        <DataTable
          headers={[t("table.statement"), t("filter.status"), t("table.owner"), t("table.dueDate"), ""]}
          rows={
            items.map((c) => [
              c.statement,
              <StatusBadge key={`${c.id}-s`} label={tb("artifactStatus", c.status)} status={c.status} />,
              <span key={`${c.id}-o`} className="text-slate-500">
                {formatOwner(c.ownerDisplayName, t("common.unassigned"))}
              </span>,
              <DueDateBadge key={`${c.id}-d`} dueAt={c.dueAt} />,
              <Link key={`${c.id}-m`} to={`/meetings/${c.meetingId}`} className="btn-secondary px-3 py-1.5 text-xs">
                {t("common.openMeeting")}
              </Link>,
            ])
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
