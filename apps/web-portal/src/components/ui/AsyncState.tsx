import type { ReactNode } from "react";

export type AsyncStatus = "loading" | "error" | "empty" | "partial" | "ready";

export function AsyncState({
  status,
  error,
  emptyMessage = "Nothing to show yet.",
  partialMessage = "Some data could not be loaded.",
  children,
}: {
  status: AsyncStatus;
  error?: Error | null;
  emptyMessage?: string;
  partialMessage?: string;
  children: ReactNode;
}) {
  if (status === "loading") {
    return (
      <div className="async-state" role="status" aria-live="polite">
        Loading…
      </div>
    );
  }
  if (status === "error") {
    return (
      <div className="async-state async-error" role="alert">
        {error?.message ?? "Something went wrong."}
      </div>
    );
  }
  if (status === "empty") {
    return (
      <div className="async-state" role="status">
        {emptyMessage}
      </div>
    );
  }
  return (
    <>
      {status === "partial" ? (
        <div className="async-banner" role="status">
          {partialMessage}
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
  return (
    <nav className="pagination" aria-label="Pagination">
      <button type="button" className="btn ghost" onClick={onReset} disabled={disabled}>
        First page
      </button>
      <button
        type="button"
        className="btn"
        onClick={onNext}
        disabled={disabled || !nextCursor}
      >
        Next
      </button>
    </nav>
  );
}
