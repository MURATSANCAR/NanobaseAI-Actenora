import { useState, type ReactNode } from "react";
import { StatusBadge } from "@/components/qa/StatusBadge";
import { TemplateBrandFooter, TemplateBrandHeader } from "@/components/template/TemplateBrandBanner";
import type { TemplateComponentType } from "@/types/template";
import { MEETING_NOTE_EDITABLE_SECTIONS } from "@/lib/templateStandards";
import { sectionHeadingClass, sectionShellClass } from "@/lib/templateSectionStyles";
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
    return (
      <article className="meeting-note-document overflow-hidden rounded-2xl border border-slate-200/80 bg-white shadow-xl shadow-violet-100/30">
        <TemplateBrandHeader compact />

        <div className="border-b border-slate-100 px-5 py-4">
          <div className="flex flex-wrap items-start justify-between gap-3">
            <div className="min-w-0 flex-1">
              {meetingTitle ? (
                <h2 className="text-lg font-bold tracking-tight text-slate-900">{meetingTitle}</h2>
              ) : null}
              <p className="mt-1 text-xs text-slate-500">
                {t("templates.note.boundTo", { name: templateName, version: templateVersion })}
              </p>
            </div>
            <div className="flex flex-wrap items-center gap-2">
              {draftBadge ? (
                <span className="rounded-full bg-amber-100 px-2.5 py-1 text-[10px] font-bold uppercase tracking-wide text-amber-800 ring-1 ring-amber-200">
                  {t("meeting.noteDraftBadge")}
                </span>
              ) : null}
              <StatusBadge
                label={locked ? t("templates.note.locked") : t("templates.note.pinPending")}
                status={locked ? "APPROVED" : "PENDING"}
              />
            </div>
          </div>
        </div>

        {validationError ? (
          <div className="mx-5 mt-4 rounded-xl border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700" role="alert">
            {validationError}
          </div>
        ) : null}

        <div className="space-y-3 px-5 py-4">
          {resolvedSections.map((type) => {
            const value = sectionValues[type] ?? "";
            const empty = !value.trim();
            return (
              <section key={type} className={sectionShellClass(type)}>
                <h3 className={sectionHeadingClass(type)}>{tb("templateComponentType", type)}</h3>
                {canEdit ? (
                  <textarea
                    className="w-full resize-y border-0 bg-transparent p-0 text-sm leading-relaxed text-slate-800 placeholder:text-slate-400 focus:outline-none focus:ring-0"
                    value={value}
                    onChange={(e) => onSectionChange?.(type, e.target.value)}
                    placeholder={t("templates.note.sectionPlaceholder")}
                    rows={Math.max(3, Math.min(8, value.split("\n").length + 1))}
                  />
                ) : empty ? (
                  <p className="text-sm italic text-slate-400">{t("meeting.noteSectionEmpty")}</p>
                ) : (
                  <p className="whitespace-pre-wrap text-sm leading-relaxed text-slate-800">{value}</p>
                )}
              </section>
            );
          })}
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
