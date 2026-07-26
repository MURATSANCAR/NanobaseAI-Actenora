import { AlertTriangle } from "lucide-react";
import type { TemplateValidationIssue } from "@/types/template";
import { useI18n } from "@/i18n";

export function TemplateValidationBanner({ issues }: { issues: TemplateValidationIssue[] }) {
  const { t } = useI18n();

  if (!issues.length) return null;

  return (
    <div
      className="card-static border-red-200/80 bg-red-50/40 p-4 text-red-800"
      role="alert"
      aria-live="polite"
    >
      <div className="mb-2 flex items-center gap-2">
        <AlertTriangle className="h-4 w-4 shrink-0" aria-hidden />
        <p className="text-sm font-semibold">{t("templates.validation.title")}</p>
      </div>
      <ul className="list-inside list-disc space-y-1 text-sm">
        {issues.map((issue, index) => (
          <li key={`${issue.code}-${index}`}>{t(issue.messageKey)}</li>
        ))}
      </ul>
    </div>
  );
}
