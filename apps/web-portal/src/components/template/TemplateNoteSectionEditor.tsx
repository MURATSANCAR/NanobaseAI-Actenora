import { StatusBadge } from "@/components/qa/StatusBadge";
import { MeetingMinutesDocument } from "@/components/meeting/MeetingMinutesDocument";
import type { TemplateComponentType } from "@/types/template";
import { MEETING_NOTE_EDITABLE_SECTIONS } from "@/lib/templateStandards";
import { sectionsFromTemplateValues } from "@/lib/minutesDocument";
import { useI18n } from "@/i18n";

/** Structured meeting note — document layout with inline editable sections. */
export function TemplateNoteSectionEditor({
  templateName,
  templateVersion,
  locked,
  canEdit,
  sectionValues,
  sections,
  onSectionChange,
  onSave,
  saving,
  validationError,
  variant = "form",
  meetingTitle,
  draftBadge,
}: {
  templateName: string;
  templateVersion: number;
  locked: boolean;
  canEdit: boolean;
  sectionValues: Partial<Record<TemplateComponentType, string>>;
  sections?: TemplateComponentType[];
  onSectionChange?: (type: TemplateComponentType, value: string) => void;
  onSave?: () => void;
  saving?: boolean;
  validationError?: string | null;
  variant?: "form" | "document";
  meetingTitle?: string;
  draftBadge?: boolean;
}) {
  const { t, tb } = useI18n();
  const resolvedSections = sections?.length ? sections : MEETING_NOTE_EDITABLE_SECTIONS;

  if (variant === "document") {
    const document = {
      title: meetingTitle?.trim() || templateName,
      statusLabel: "",
      sections: sectionsFromTemplateValues(sectionValues, resolvedSections),
    };

    return (
      <div className="space-y-3">
        {validationError ? (
          <div className="rounded-xl border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700" role="alert">
            {validationError}
          </div>
        ) : null}
        <MeetingMinutesDocument
          document={document}
          canEdit={canEdit}
          draftBadge={draftBadge}
          saving={saving}
          onSave={onSave}
          onSectionChange={onSectionChange}
          footerExtra={
            <div className="border-t border-violet-100/80 bg-gradient-to-r from-violet-50/70 via-white to-sky-50/50 px-5 py-3 text-xs text-violet-900">
              {t("templates.note.boundTo", { name: templateName, version: templateVersion })}
              {draftBadge ? (
                <span className="ml-2 inline-flex rounded-full bg-amber-100 px-2 py-0.5 text-[10px] font-bold uppercase tracking-wide text-amber-800">
                  {t("meeting.noteDraftBadge")}
                </span>
              ) : null}
            </div>
          }
        />
      </div>
    );
  }

  return (
    <div className="artifact-block space-y-3">
      <div className="flex flex-wrap items-center gap-2">
        <h3 className="text-xs font-bold uppercase tracking-wide text-violet-700">{t("meeting.notes")}</h3>
        <StatusBadge label={tb("noteVisibility", "SHARED")} status="SHARED" />
        <StatusBadge
          label={locked ? t("templates.note.locked") : t("templates.note.pinPending")}
          status={locked ? "APPROVED" : "PENDING"}
        />
      </div>

      <div className="rounded-xl border border-violet-200/70 bg-violet-50/40 px-3 py-2 text-xs text-violet-900">
        {t("templates.note.boundTo", { name: templateName, version: templateVersion })}
        {locked ? null : (
          <span className="mt-1 block text-violet-700/80">{t("templates.note.pinOnSaveHint")}</span>
        )}
      </div>

      {validationError ? (
        <div className="card-static border-red-200/80 bg-red-50/40 p-3 text-sm text-red-700" role="alert">
          {validationError}
        </div>
      ) : null}

      <div className="space-y-3">
        {resolvedSections.map((type) => (
          <div key={type} className="rounded-xl border border-white/70 bg-white/50 p-3">
            <label className="mb-2 block text-xs font-bold uppercase tracking-wide text-violet-700">
              {tb("templateComponentType", type)}
            </label>
            <textarea
              className="input-field min-h-[4rem]"
              value={sectionValues[type] ?? ""}
              onChange={(e) => onSectionChange?.(type, e.target.value)}
              disabled={!canEdit}
              placeholder={t("templates.note.sectionPlaceholder")}
              rows={2}
            />
          </div>
        ))}
      </div>

      {canEdit ? (
        <button type="button" className="btn-primary" onClick={onSave} disabled={saving}>
          {t("meeting.saveNote")}
        </button>
      ) : null}
    </div>
  );
}
