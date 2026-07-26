import type { ReactNode } from "react";
import { useI18n } from "../../i18n";

export type AsyncStatus = "loading" | "error" | "empty" | "partial" | "ready";

export function AsyncState({
  status,
  error,
  emptyMessage,
  partialMessage,
  children,
}: {
  status: AsyncStatus;
  error?: Error | null;
  emptyMessage?: string;
  partialMessage?: string;
  children: ReactNode;
}) {
  const { t } = useI18n();
  const empty = emptyMessage ?? t("async.empty");
  const partial = partialMessage ?? t("async.partial");

  if (status === "loading") {
    return (
      <div className="async-state" role="status" aria-live="polite">
        {t("async.loading")}
      </div>
    );
  }
  if (status === "error") {
    return (
      <div className="async-state async-error" role="alert">
        {error?.message ?? t("async.error")}
      </div>
    );
  }
  if (status === "empty") {
    return (
      <div className="async-state" role="status">
        {empty}
      </div>
    );
  }
  return (
    <>
      {status === "partial" ? (
        <div className="async-banner" role="status">
          {partial}
        </div>
      ) : null}
      {children}
    </>
  );
}

export function PageHeader({
  title,
  description,
  actions,
}: {
  title: string;
  description?: string;
  actions?: ReactNode;
}) {
  return (
    <header className="page-header">
      <div>
        <h1>{title}</h1>
        {description ? <p className="page-lede">{description}</p> : null}
      </div>
      {actions ? <div className="page-actions">{actions}</div> : null}
    </header>
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
    <nav className="pagination" aria-label="Pagination">
      <button type="button" className="btn ghost" onClick={onReset} disabled={disabled}>
        {t("pagination.first")}
      </button>
      <button
        type="button"
        className="btn"
        onClick={onNext}
        disabled={disabled || !nextCursor}
      >
        {t("pagination.next")}
      </button>
    </nav>
  );
}
