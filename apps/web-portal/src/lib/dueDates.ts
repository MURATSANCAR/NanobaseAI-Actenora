export function isOverdue(dueAt: string | null | undefined, now = Date.now()): boolean {
  if (!dueAt) return false;
  const due = Date.parse(dueAt);
  if (Number.isNaN(due)) return false;
  return due < now;
}

export function formatDueDate(dueAt: string | null | undefined, locale?: string): string {
  if (!dueAt) return "—";
  const parsed = Date.parse(dueAt);
  if (Number.isNaN(parsed)) return dueAt;
  return new Date(parsed).toLocaleDateString(locale);
}

export function dueDateTone(dueAt: string | null | undefined, now = Date.now()): "default" | "warn" | "muted" {
  if (!dueAt) return "muted";
  if (isOverdue(dueAt, now)) return "warn";
  return "default";
}
