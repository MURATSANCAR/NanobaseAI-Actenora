import type { ReactNode } from "react";
import { Loader2 } from "lucide-react";
import { EmptyState } from "@/components/qa/EmptyState";
import { ScrollTable } from "@/components/qa/ScrollTable";
import { useI18n } from "@/i18n";

export type AsyncStatus = "loading" | "error" | "empty" | "partial" | "ready";

export function AsyncState({
  status,
  error,
  emptyTitle,
  emptyDescription,
  partialMessage,
  children,
}: {
  status: AsyncStatus;
  error?: Error | null;
  emptyTitle?: string;
  emptyDescription?: string;
  partialMessage?: string;
  children: ReactNode;
}) {
  const { t } = useI18n();

  if (status === "loading") {
    return (
      <div className="card-static flex items-center justify-center gap-3 p-8" role="status" aria-live="polite">
        <Loader2 className="h-5 w-5 animate-spin text-violet-600" />
        <span className="text-sm font-medium text-slate-600">{t("async.loading")}</span>
      </div>
    );
  }

  if (status === "error") {
    return (
      <div className="card-static border-red-200/80 bg-red-50/40 p-6 text-red-700" role="alert">
        <p className="text-sm font-semibold">{error?.message ?? t("async.error")}</p>
      </div>
    );
  }

  if (status === "empty") {
    return (
      <EmptyState
        title={emptyTitle ?? t("async.empty")}
        description={emptyDescription ?? t("async.empty")}
      />
    );
  }

  return (
    <>
      {status === "partial" ? (
        <div className="card-static border-amber-200/70 bg-amber-50/50 p-4 text-sm text-amber-900" role="status">
          {partialMessage ?? t("async.partial")}
        </div>
      ) : null}
      {children}
    </>
  );
}

export function PaginationBar({
  nextCursor,
  onNext,
  onReset,
  disabled,
}: {
  nextCursor: string | null | undefined;
  onNext: () => void;
  onReset: () => void;
  disabled?: boolean;
}) {
  const { t } = useI18n();

  return (
    <nav className="mobile-action-row pt-2" aria-label="Pagination">
      <button type="button" className="btn-secondary" onClick={onReset} disabled={disabled}>
        {t("pagination.first")}
      </button>
      <button type="button" className="btn-primary" onClick={onNext} disabled={disabled || !nextCursor}>
        {t("pagination.next")}
      </button>
    </nav>
  );
}

export function FilterCard({ children }: { children: ReactNode }) {
  return <div className="card-static p-4 sm:p-5">{children}</div>;
}

export function DataTableCard({
  children,
  ariaLabel,
}: {
  children: ReactNode;
  ariaLabel?: string;
}) {
  return (
    <div className="card-static overflow-hidden p-0">
      <ScrollTable aria-label={ariaLabel}>{children}</ScrollTable>
    </div>
  );
}

export function DataTable({
  headers,
  rows,
  ariaLabel,
}: {
  headers: string[];
  rows: ReactNode[][];
  ariaLabel?: string;
}) {
  return (
    <DataTableCard ariaLabel={ariaLabel}>
      <table className="w-full border-collapse text-left">
        <thead className="data-table-head">
          <tr>
            {headers.map((h) => (
              <th key={h} className="data-table-cell">
                {h}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.map((cells, idx) => (
            <tr key={idx} className="data-table-row">
              {cells.map((cell, cellIdx) => (
                <td key={cellIdx} className="data-table-cell">
                  {cell}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </DataTableCard>
  );
}
