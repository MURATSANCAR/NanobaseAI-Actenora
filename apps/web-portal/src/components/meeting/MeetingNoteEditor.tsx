import { useQuery } from "@tanstack/react-query";
import { useMemo, useState } from "react";
import { TemplateNoteSectionEditor } from "@/components/template/TemplateNoteSectionEditor";
import { TemplateBrandFooter, TemplateBrandHeader } from "@/components/template/TemplateBrandBanner";
import { StatusBadge } from "@/components/qa/StatusBadge";
import { queryKeys } from "@/api/client";
import { useApi } from "@/api/ApiProvider";
import type { DesignSchemaView, MeetingNote, TemplateSummary } from "@/api/types";
import type { TemplateComponentType } from "@/types/template";
import { editableSectionsFromDesign } from "@/lib/templateStandards";
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
  meetingTitle,
  variant = "document",
}: {
  meetingId: string;
  note: MeetingNote;
  draft: string;
  canEdit: boolean;
  publishedTemplates: TemplateSummary[];
  saving: boolean;
  onChange: (body: string) => void;
  onSave: () => void;
  meetingTitle?: string;
  variant?: "form" | "document";
}) {
  const api = useApi();
  const { t, tb } = useI18n();
  const [showValidation, setShowValidation] = useState(false);
  const [pinning, setPinning] = useState(false);

  // Returns the pinned version when the note is already bound, otherwise the tenant
  // default's latest published version as a not-yet-pinned suggestion.
  const bindingQuery = useQuery({
    queryKey: queryKeys.noteTemplateLock(meetingId, note.id),
    queryFn: () => api.getNoteTemplateLock(meetingId, note.id),
  });

  const parsed = useMemo(() => parseTemplateNoteBody(draft), [draft]);
  const binding = bindingQuery.data ?? null;

  // A saved note keeps the version recorded in its own body, even if the tenant default
  // has moved on since. Only then do we need to look the historical design up separately.
  const historicalVersionId =
    parsed && parsed.templateVersionId && parsed.templateVersionId !== binding?.templateVersionId
      ? parsed.templateVersionId
      : null;

  const historicalQuery = useQuery({
    queryKey: queryKeys.templateDetail(parsed?.templateId ?? ""),
    queryFn: () => api.getTemplate(parsed!.templateId),
    enabled: Boolean(historicalVersionId && parsed?.templateId),
  });

  const historicalDesign: DesignSchemaView | null = useMemo(() => {
    if (!historicalVersionId) return null;
    const version = historicalQuery.data?.versions.find((v) => v.id === historicalVersionId);
    return version?.designSchema ?? null;
  }, [historicalQuery.data, historicalVersionId]);

  const effective = historicalVersionId
    ? {
        templateId: parsed!.templateId,
        templateName: parsed!.templateName,
        templateVersionId: parsed!.templateVersionId,
        templateVersionNumber: parsed!.templateVersionNumber,
        locked: true,
        designSchema: historicalDesign,
      }
    : binding;

  const usesTemplate = Boolean(effective);
  const sectionValues = parsed?.sections ?? {};
  const sections = useMemo(
    () => editableSectionsFromDesign(effective?.designSchema),
    [effective?.designSchema],
  );

  // The first section of the bound design is the note's mandatory summary field.
  const requiredSection: TemplateComponentType | null = sections[0] ?? null;
  const missingRequired = Boolean(
    usesTemplate && requiredSection && !sectionValues[requiredSection]?.trim(),
  );
  const validationError =
    showValidation && missingRequired
      ? t("templates.note.validationRequired", {
          section: requiredSection ? tb("templateComponentType", requiredSection) : "",
        })
      : null;

  function currentBody() {
    return (
      parsed ??
      createTemplateNoteBody({
        templateId: effective?.templateId ?? "",
        templateVersionId: effective?.templateVersionId ?? "",
        templateName: effective?.templateName ?? "",
        templateVersionNumber: effective?.templateVersionNumber ?? 0,
        sections: {},
      })
    );
  }

  function updateSections(next: Partial<Record<TemplateComponentType, string>>) {
    const base = currentBody();
    onChange(
      serializeTemplateNoteBody({
        ...base,
        sections: { ...base.sections, ...next },
      }),
    );
  }

  async function handleSave() {
    if (missingRequired) {
      setShowValidation(true);
      return;
    }
    setShowValidation(false);
    // Pin before persisting so the note keeps this design even after the template evolves.
    if (effective && !effective.locked) {
      setPinning(true);
      try {
        await api.lockNoteTemplate(meetingId, note.id, effective.templateVersionId);
        await bindingQuery.refetch();
      } finally {
        setPinning(false);
      }
    }
    onSave();
  }

  async function applyTemplate(templateId: string) {
    const detail = await api.getTemplate(templateId);
    const publishedId = detail.publishedVersionId;
    const published =
      detail.versions.find((v) => v.id === publishedId) ??
      detail.versions
        .filter((v) => v.status === "PUBLISHED")
        .sort((a, b) => b.versionNumber - a.versionNumber)[0];
    if (!published) return;
    await api.lockNoteTemplate(meetingId, note.id, published.id);
    await bindingQuery.refetch();
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

  const draftBadge = Boolean(note.draft || note.approvalStatus === "DRAFT");

  if (usesTemplate && effective) {
    return (
      <TemplateNoteSectionEditor
        templateName={effective.templateName}
        templateVersion={effective.templateVersionNumber}
        locked={effective.locked}
        canEdit={canEdit}
        sectionValues={sectionValues}
        sections={sections}
        onSectionChange={(type, value) => updateSections({ [type]: value })}
        onSave={handleSave}
        saving={saving || pinning}
        validationError={validationError}
        variant={variant}
        meetingTitle={meetingTitle}
        draftBadge={draftBadge}
      />
    );
  }

  if (variant === "document") {
    return (
      <article className="meeting-note-document overflow-hidden rounded-2xl border border-slate-200/80 bg-white shadow-xl shadow-violet-100/30">
        <TemplateBrandHeader compact />
        <div className="border-b border-slate-100 px-5 py-4">
          <div className="flex flex-wrap items-center gap-2">
            {meetingTitle ? <h2 className="text-lg font-bold text-slate-900">{meetingTitle}</h2> : null}
            <StatusBadge label={tb("noteVisibility", note.visibility)} status={note.visibility} />
            {draftBadge ? (
              <span className="rounded-full bg-amber-100 px-2.5 py-1 text-[10px] font-bold uppercase tracking-wide text-amber-800">
                {t("meeting.noteDraftBadge")}
              </span>
            ) : null}
          </div>
        </div>
        <div className="px-5 py-4">
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
            className="min-h-[12rem] w-full resize-y rounded-xl border-0 bg-violet-50/30 p-4 text-sm leading-relaxed text-slate-800 focus:outline-none focus:ring-2 focus:ring-violet-300/50"
            value={draft}
            onChange={(e) => onChange(e.target.value)}
            disabled={!canEdit}
            aria-label={tb("noteVisibility", note.visibility)}
            rows={8}
          />
        </div>
        <div className="border-t border-slate-100 px-5 py-3">
          <TemplateBrandFooter />
        </div>
        {canEdit ? (
          <div className="flex justify-end border-t border-slate-100 bg-slate-50/80 px-5 py-3">
            <button type="button" className="btn-primary" onClick={onSave} disabled={saving}>
              {t("meeting.saveNote")}
            </button>
          </div>
        ) : null}
      </article>
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
