import { Loader2 } from "lucide-react";
import { useI18n } from "@/i18n";

export function MeetingProcessingStrip({ lastUpdated }: { lastUpdated?: Date }) {
  const { t } = useI18n();

  return (
    <div
      className="card-static flex flex-wrap items-center gap-3 border-amber-200/70 bg-amber-50/60 px-4 py-3 text-sm text-amber-950"
      role="status"
      aria-live="polite"
    >
      <Loader2 className="h-4 w-4 shrink-0 animate-spin text-amber-700" />
      <div className="min-w-0 flex-1">
        <p className="font-semibold">{t("meeting.processingTitle")}</p>
        <p className="text-xs text-amber-900/80">{t("meeting.processingHint")}</p>
      </div>
      {lastUpdated ? (
        <span className="text-xs text-amber-900/70">
          {t("meeting.lastUpdated")} {lastUpdated.toLocaleTimeString()}
        </span>
      ) : null}
    </div>
  );
}
