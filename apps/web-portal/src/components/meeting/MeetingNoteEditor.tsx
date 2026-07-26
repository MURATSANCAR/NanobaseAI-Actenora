import { useQuery } from "@tanstack/react-query";
import { useMemo, useState } from "react";
import { TemplateNoteSectionEditor } from "@/components/template/TemplateNoteSectionEditor";
import { StatusBadge } from "@/components/qa/StatusBadge";
import { queryKeys } from "@/api/client";
import { useApi } from "@/api/ApiProvider";
import type { MeetingNote, TemplateSummary } from "@/api/types";
import type { TemplateComponentType } from "@/types/template";
import {
  createTemplateNoteBody,
  parseTemplateNoteBody,
  serializeTemplateNoteBody,
} from "@/lib/templateNoteBody";
import { useI18n } from "@/i18n";

export function MeetingNoteEditor({
  meetingId,
  note,
  draft,
  canEdit,
  publishedTemplates,
  saving,
  onChange,
  onSave,
}: {
  meetingId: string;
  note: MeetingNote;
  draft: string;
  canEdit: boolean;
  publishedTemplates: TemplateSummary[];
  saving: boolean;
  onChange: (body: string) => void;
  onSave: () => void;
}) {
  const api = useApi();
  const { t, tb } = useI18n();
  const [showValidation, setShowValidation] = useState(false);

  const lockQuery = useQuery({
    queryKey: queryKeys.noteTemplateLock(meetingId, note.id),
    queryFn: () => api.getNoteTemplateLock(meetingId, note.id),
  });

  const parsed = useMemo(() => parseTemplateNoteBody(draft), [draft]);
  const lock = lockQuery.data ?? null;
  const usesTemplate = Boolean(parsed || lock);

  const templateName = lock?.templateName ?? parsed?.templateName ?? "";
  const templateVersion = lock?.templateVersionNumber ?? parsed?.templateVersionNumber ?? 0;
  const sectionValues = parsed?.sections ?? {};

  const validationError =
    showValidation && usesTemplate && !sectionValues.EXECUTIVE_SUMMARY?.trim()
      ? t("templates.note.validationRequired")
      : null;

  function handleSave() {
    if (usesTemplate && !sectionValues.EXECUTIVE_SUMMARY?.trim()) {
      setShowValidation(true);
      return;
    }
    setShowValidation(false);
    onSave();
  }

  function updateSections(next: Partial<Record<TemplateComponentType, string>>) {
    const base =
      parsed ??
      createTemplateNoteBody({
        templateId: lock?.templateId ?? "",
        templateVersionId: lock?.templateVersionId ?? "",
        templateName,
        templateVersionNumber: templateVersion,
        sections: {},
      });
    onChange(
      serializeTemplateNoteBody({
        ...base,
        sections: { ...base.sections, ...next },
      }),
    );
  }

  async function applyTemplate(templateId: string) {
    const detail = await api.getTemplate(templateId);
    const published = detail.versions.find((v) => v.status === "PUBLISHED");
    if (!published) return;
    await api.lockNoteTemplate(meetingId, note.id, published.id);
    await lockQuery.refetch();
    onChange(
      serializeTemplateNoteBody(
        createTemplateNoteBody({
          templateId: detail.id,
          templateVersionId: published.id,
          templateName: detail.name,
          templateVersionNumber: published.versionNumber,
        }),
      ),
    );
  }

  if (usesTemplate) {
    return (
      <TemplateNoteSectionEditor
        templateName={templateName}
        templateVersion={templateVersion}
        locked={Boolean(lock)}
        canEdit={canEdit}
        sectionValues={sectionValues}
        onSectionChange={(type, value) => updateSections({ [type]: value })}
        onSave={handleSave}
        saving={saving}
        validationError={validationError}
      />
    );
  }

  return (
    <div className="rounded-xl border border-white/70 bg-white/50 p-3">
      <div className="mb-2 flex flex-wrap items-center gap-2">
        <StatusBadge label={tb("noteVisibility", note.visibility)} status={note.visibility} />
      </div>
      {canEdit && publishedTemplates.length ? (
        <label className="mb-3 block">
          <span className="label-text">{t("templates.note.applyTemplate")}</span>
          <select
            className="input-field"
            defaultValue=""
            onChange={(e) => {
              const value = e.target.value;
              if (value) void applyTemplate(value);
              e.currentTarget.value = "";
            }}
          >
            <option value="">{t("templates.note.chooseTemplate")}</option>
            {publishedTemplates.map((template) => (
              <option key={template.id} value={template.id}>
                {template.name}
              </option>
            ))}
          </select>
        </label>
      ) : null}
      <textarea
        className="input-field min-h-[5rem]"
        value={draft}
        onChange={(e) => onChange(e.target.value)}
        disabled={!canEdit}
        aria-label={tb("noteVisibility", note.visibility)}
        rows={3}
      />
      {canEdit ? (
        <button type="button" className="btn-primary mt-2" onClick={onSave} disabled={saving}>
          {t("meeting.saveNote")}
        </button>
      ) : null}
    </div>
  );
}
