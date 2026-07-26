import clsx from "clsx";
import { useI18n } from "@/i18n";
import { dueDateTone, formatDueDate, isOverdue } from "@/lib/dueDates";

export function DueDateBadge({ dueAt }: { dueAt: string | null | undefined }) {
  const { t, locale } = useI18n();
  const tone = dueDateTone(dueAt);
  const overdue = isOverdue(dueAt);

  if (!dueAt) {
    return <span className="text-xs text-slate-400">{t("due.noDate")}</span>;
  }

  return (
    <span
      className={clsx(
        "inline-flex items-center rounded-full px-2 py-0.5 text-[11px] font-semibold",
        tone === "warn"
          ? "bg-amber-100 text-amber-900"
          : tone === "muted"
            ? "bg-slate-100 text-slate-500"
            : "bg-teal-50 text-teal-800",
      )}
    >
      {overdue ? t("due.overdue") : t("due.due")} {formatDueDate(dueAt, locale === "tr" ? "tr-TR" : "en-US")}
    </span>
  );
}
