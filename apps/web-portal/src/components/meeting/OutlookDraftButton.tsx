import { useMutation } from "@tanstack/react-query";
import { ExternalLink, MailPlus } from "lucide-react";
import { useApi } from "@/api/ApiProvider";
import { useI18n } from "@/i18n";

export function OutlookDraftButton({
  meetingId,
  noteId,
}: {
  meetingId: string;
  noteId: string;
}) {
  const api = useApi();
  const { t } = useI18n();
  const mutation = useMutation({
    mutationFn: () => api.createOutlookDraft(meetingId, noteId),
  });

  if (mutation.data?.webLink) {
    return (
      <a
        className="btn-primary px-3 py-1.5 text-xs"
        href={mutation.data.webLink}
        target="_blank"
        rel="noreferrer"
      >
        <ExternalLink className="h-4 w-4" />
        {t("outlookDraft.open")}
      </a>
    );
  }

  return (
    <div className="flex flex-col items-end gap-1">
      <button
        type="button"
        className="btn-primary px-3 py-1.5 text-xs"
        disabled={mutation.isPending}
        onClick={() => mutation.mutate()}
      >
        <MailPlus className="h-4 w-4" />
        {mutation.isPending ? t("outlookDraft.creating") : t("outlookDraft.create")}
      </button>
      {mutation.data ? (
        <span className="text-[10px] text-emerald-700">{t("outlookDraft.created")}</span>
      ) : null}
      {mutation.isError ? (
        <span className="max-w-56 text-right text-[10px] text-red-700">{mutation.error.message}</span>
      ) : null}
    </div>
  );
}
