import { FileText } from "lucide-react";
import type { ReactNode } from "react";
import { TemplateBrandFooter, TemplateBrandHeader } from "@/components/template/TemplateBrandBanner";
import { useI18n } from "@/i18n";
import { sanitizeProductCopy } from "@/lib/brandSanitize";
import {
  parseActionMeta,
  parseSectionContent,
  type MinutesDocument,
  type MinutesSection,
} from "@/lib/minutesDocument";
import { minutesSectionTheme } from "@/lib/minutesSectionTheme";
import type { TemplateComponentType } from "@/types/template";

export function MeetingMinutesDocument({
  document,
  canEdit,
  onSectionChange,
  onSave,
  saving,
  draftBadge,
  footerExtra,
}: {
  document: MinutesDocument;
  canEdit?: boolean;
  onSectionChange?: (type: TemplateComponentType, value: string) => void;
  onSave?: () => void;
  saving?: boolean;
  draftBadge?: boolean;
  footerExtra?: ReactNode;
}) {
  const { t } = useI18n();

  return (
    <article className="meeting-note-document overflow-hidden rounded-3xl border border-slate-200/70 bg-white shadow-2xl shadow-violet-200/40 ring-1 ring-violet-100/60">
      <TemplateBrandHeader />

      <header className="relative overflow-hidden border-b border-violet-100/80 bg-gradient-to-br from-violet-600 via-indigo-500 to-sky-500 px-5 py-6 text-white sm:px-7 sm:py-8">
        <div
          className="pointer-events-none absolute -right-10 -top-16 h-40 w-40 rounded-full bg-white/15 blur-2xl"
          aria-hidden
        />
        <div
          className="pointer-events-none absolute -bottom-20 left-1/3 h-36 w-36 rounded-full bg-sky-300/25 blur-2xl"
          aria-hidden
        />
        <div className="relative flex flex-wrap items-start justify-between gap-4">
          <div className="min-w-0 flex-1">
            <p className="inline-flex items-center gap-2 text-[11px] font-bold uppercase tracking-[0.2em] text-white/80">
              <FileText className="h-3.5 w-3.5" aria-hidden />
              {t("meeting.minutesDocumentTitle")}
            </p>
            <h2 className="mt-2 text-2xl font-bold tracking-tight sm:text-3xl">
              {document.title.trim() || t("meeting.minutesUntitled")}
            </h2>
            <div className="mt-3 flex flex-wrap items-center gap-2">
              {document.statusLabel ? (
                <span className="rounded-full bg-white/20 px-3 py-1 text-xs font-semibold ring-1 ring-white/35 backdrop-blur">
                  {t("meeting.minutesStatus")}: {sanitizeProductCopy(document.statusLabel)}
                </span>
              ) : null}
              {draftBadge ? (
                <span className="rounded-full bg-amber-300/95 px-3 py-1 text-[10px] font-bold uppercase tracking-wide text-amber-950 shadow-sm">
                  {t("meeting.noteDraftBadge")}
                </span>
              ) : null}
            </div>
          </div>
        </div>
      </header>

      <div className="space-y-4 bg-gradient-to-b from-slate-50/80 via-white to-violet-50/30 px-4 py-5 sm:px-6 sm:py-6">
        {document.sections.map((section, index) => (
          <MinutesSectionCard
            key={section.type}
            section={section}
            index={index + 1}
            canEdit={Boolean(canEdit)}
            onChange={
              onSectionChange
                ? (value) => onSectionChange(section.type, value)
                : undefined
            }
          />
        ))}
      </div>

      <div className="border-t border-slate-100 px-4 py-3 sm:px-6">
        <TemplateBrandFooter />
      </div>

      {footerExtra}

      {canEdit && onSave ? (
        <div className="flex justify-end border-t border-slate-100 bg-slate-50/90 px-5 py-3">
          <button type="button" className="btn-primary" onClick={onSave} disabled={saving}>
            {t("meeting.saveNote")}
          </button>
        </div>
      ) : null}
    </article>
  );
}

function MinutesSectionCard({
  section,
  index,
  canEdit,
  onChange,
}: {
  section: MinutesSection;
  index: number;
  canEdit: boolean;
  onChange?: (value: string) => void;
}) {
  const { t, tb } = useI18n();
  const theme = minutesSectionTheme(section.type);
  const Icon = theme.icon;
  const parsed = parseSectionContent(section.value, section.kind);

  return (
    <section
      className={[
        "rounded-2xl border p-4 shadow-sm transition sm:p-5",
        theme.shell,
      ].join(" ")}
    >
      <div className="mb-3 flex items-center gap-3">
        <span
          className={[
            "flex h-10 w-10 shrink-0 items-center justify-center rounded-xl shadow-md",
            theme.iconTile,
          ].join(" ")}
          aria-hidden
        >
          <Icon className="h-5 w-5" />
        </span>
        <div className="min-w-0 flex-1">
          <div className="flex flex-wrap items-center gap-2">
            <span
              className={[
                "inline-flex h-6 min-w-6 items-center justify-center rounded-full px-2 text-[11px] font-bold",
                theme.indexChip,
              ].join(" ")}
            >
              {index}
            </span>
            <h3
              className={[
                "text-sm font-bold uppercase tracking-wide",
                theme.heading,
              ].join(" ")}
            >
              {tb("templateComponentType", section.type)}
            </h3>
          </div>
        </div>
      </div>

      {canEdit && onChange ? (
        <textarea
          className="w-full resize-y rounded-xl border border-white/80 bg-white/70 px-3 py-2.5 text-sm leading-relaxed text-slate-800 shadow-inner placeholder:text-slate-400 focus:border-violet-300 focus:outline-none focus:ring-2 focus:ring-violet-300/40"
          value={editableValue(section)}
          onChange={(e) => onChange(e.target.value)}
          placeholder={
            section.kind === "list"
              ? t("meeting.minutesListPlaceholder")
              : t("templates.note.sectionPlaceholder")
          }
          rows={
            section.kind === "paragraph"
              ? Math.max(3, Math.min(8, section.value.split("\n").length + 1))
              : Math.max(3, Math.min(10, parseSectionContent(section.value, "list").items.length + 2))
          }
        />
      ) : parsed.empty ? (
        <p className="rounded-xl border border-dashed border-slate-200/90 bg-white/50 px-3 py-4 text-center text-sm italic text-slate-400">
          {t("meeting.noteSectionEmpty")}
        </p>
      ) : section.kind === "paragraph" ? (
        <p className="whitespace-pre-wrap rounded-xl bg-white/70 px-3.5 py-3 text-sm leading-relaxed text-slate-800 shadow-sm ring-1 ring-white/80">
          {parsed.paragraph}
        </p>
      ) : (
        <ol className="space-y-2">
          {parsed.items.map((item, i) => {
            const meta =
              section.type === "ACTIONS" ? parseActionMeta(item) : { text: item };
            return (
              <li
                key={`${section.type}-${i}`}
                className={[
                  "flex gap-3 rounded-xl border px-3 py-2.5 text-sm leading-relaxed text-slate-800 shadow-sm",
                  theme.item,
                ].join(" ")}
              >
                <span
                  className={[
                    "mt-0.5 flex h-6 w-6 shrink-0 items-center justify-center rounded-full text-[11px] font-bold",
                    theme.itemIndex,
                  ].join(" ")}
                >
                  {i + 1}
                </span>
                <div className="min-w-0 flex-1 space-y-1.5">
                  <p>{meta.text}</p>
                  {"owner" in meta && (meta.owner || meta.due) ? (
                    <div className="flex flex-wrap gap-1.5">
                      {meta.owner ? (
                        <span className="rounded-full bg-amber-100/90 px-2.5 py-0.5 text-[11px] font-semibold text-amber-900">
                          {t("meeting.minutesOwner")}: {meta.owner}
                        </span>
                      ) : null}
                      {meta.due ? (
                        <span className="rounded-full bg-slate-100 px-2.5 py-0.5 text-[11px] font-semibold text-slate-700">
                          {t("meeting.minutesDue")}: {meta.due}
                        </span>
                      ) : null}
                    </div>
                  ) : null}
                </div>
              </li>
            );
          })}
        </ol>
      )}
    </section>
  );
}

/** For list sections, show one item per line without numbering for easier editing. */
function editableValue(section: MinutesSection): string {
  if (section.kind !== "list") return section.value;
  const parsed = parseSectionContent(section.value, "list");
  if (parsed.empty) return "";
  return parsed.items.join("\n");
}
