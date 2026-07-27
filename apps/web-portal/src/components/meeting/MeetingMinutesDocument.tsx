import { FileText, Globe2, Sparkles } from "lucide-react";
import { useMemo, type ReactNode } from "react";
import type { Participant } from "@/api/types";
import { MinutesAttendanceBanner } from "@/components/meeting/MinutesAttendanceBanner";
import { classifyAttendance } from "@/components/meeting/ParticipantAttendanceRow";
import { TemplateBrandFooter } from "@/components/template/TemplateBrandBanner";
import { PRODUCT_BRAND } from "@/config/brand";
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
  participants = [],
  meetingStatus = "",
}: {
  document: MinutesDocument;
  canEdit?: boolean;
  onSectionChange?: (type: TemplateComponentType, value: string) => void;
  onSave?: () => void;
  saving?: boolean;
  draftBadge?: boolean;
  footerExtra?: ReactNode;
  participants?: Participant[];
  meetingStatus?: string;
}) {
  const { t } = useI18n();
  const filledCount = document.sections.filter((section) => {
    const parsed = parseSectionContent(section.value, section.kind);
    return !parsed.empty;
  }).length;

  const attendanceCounts = useMemo(() => {
    let attended = 0;
    let absent = 0;
    for (const p of participants) {
      const bucket = classifyAttendance(p.attendanceStatus, meetingStatus);
      if (bucket === "attended") attended += 1;
      else if (bucket === "absent") absent += 1;
    }
    return { attended, absent };
  }, [participants, meetingStatus]);

  return (
    <article className="meeting-note-document">
      <div className="meeting-note-aurora" aria-hidden />
      <div className="meeting-note-grid" aria-hidden />
      <div className="meeting-note-orb meeting-note-orb--a" aria-hidden />
      <div className="meeting-note-orb meeting-note-orb--b" aria-hidden />
      <div className="meeting-note-orb meeting-note-orb--c" aria-hidden />

      <header className="meeting-note-hero">
        <div className="relative z-10 space-y-5">
          <div className="flex flex-wrap items-start justify-between gap-4">
            <div className="min-w-0 flex-1">
              <p className="meeting-note-brand">
                <span className="meeting-note-brand-mark" aria-hidden>
                  <Sparkles className="h-3.5 w-3.5" />
                </span>
                {PRODUCT_BRAND}
                <span className="meeting-note-ai-pill">{t("templates.brand.aiBadge")}</span>
              </p>
              <p className="mt-3 inline-flex items-center gap-2 text-[11px] font-bold uppercase tracking-[0.22em] text-white/75">
                <FileText className="h-3.5 w-3.5" aria-hidden />
                {t("meeting.minutesDocumentTitle")}
              </p>
              <h2 className="meeting-note-title">
                {document.title.trim() || t("meeting.minutesUntitled")}
              </h2>
              <p className="mt-2 max-w-2xl text-sm text-white/80 sm:text-[15px]">
                {t("templates.brand.slogan")}
              </p>
            </div>

            <div className="flex flex-col items-end gap-2">
              <span className="meeting-note-live-pill">
                <Globe2 className="h-3.5 w-3.5" aria-hidden />
                {t("meeting.minutesGlobalBadge")}
              </span>
              <span className="rounded-full bg-white/15 px-3 py-1 text-[11px] font-semibold text-white/90 ring-1 ring-white/25 backdrop-blur">
                {t("meeting.minutesSectionsFilled", {
                  filled: filledCount,
                  total: document.sections.length,
                })}
              </span>
              {participants.length ? (
                <span className="rounded-full bg-emerald-300/95 px-3 py-1 text-[11px] font-bold text-emerald-950 shadow-sm">
                  {t("meeting.attendanceSummary", {
                    attended: attendanceCounts.attended,
                    absent: attendanceCounts.absent,
                  })}
                </span>
              ) : null}
            </div>
          </div>

          <div className="meeting-note-hero-rule" aria-hidden />

          <div className="flex flex-wrap items-center gap-2">
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
            <div className="ml-auto flex flex-wrap items-center gap-1.5" aria-hidden>
              {document.sections.map((section) => {
                const theme = minutesSectionTheme(section.type);
                return (
                  <span
                    key={section.type}
                    className={[
                      "h-2.5 w-2.5 rounded-full shadow-sm ring-1 ring-white/40",
                      theme.swatch,
                    ].join(" ")}
                    title={section.type}
                  />
                );
              })}
            </div>
          </div>
        </div>
      </header>

      {participants.length ? (
        <div className="relative z-10 border-b border-violet-100/70 bg-gradient-to-b from-violet-50/50 via-white to-white px-4 py-4 sm:px-6">
          <MinutesAttendanceBanner participants={participants} meetingStatus={meetingStatus} />
        </div>
      ) : null}

      <div className="meeting-note-body">
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

      <div className="relative z-10 px-4 py-3 sm:px-6">
        <TemplateBrandFooter />
      </div>

      {footerExtra ? <div className="relative z-10">{footerExtra}</div> : null}

      {canEdit && onSave ? (
        <div className="relative z-10 flex justify-end border-t border-violet-100/80 bg-gradient-to-r from-violet-50/80 via-white to-sky-50/60 px-5 py-3.5">
          <button type="button" className="btn-primary shadow-glow" onClick={onSave} disabled={saving}>
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
        "meeting-note-section relative overflow-hidden rounded-2xl border p-4 shadow-[0_14px_40px_-24px_rgba(15,23,42,0.35)] backdrop-blur-sm transition duration-300 sm:p-5",
        "hover:-translate-y-0.5 hover:shadow-[0_20px_48px_-22px_rgba(15,23,42,0.4)]",
        theme.shell,
      ].join(" ")}
      style={{ animationDelay: `${Math.min(index, 8) * 55}ms` }}
    >
      <span
        className={["absolute inset-y-0 left-0 w-1.5 bg-gradient-to-b", theme.rail].join(" ")}
        aria-hidden
      />

      <div className="mb-3.5 flex items-center gap-3 pl-2">
        <span
          className={[
            "flex h-11 w-11 shrink-0 items-center justify-center rounded-2xl shadow-lg ring-2 ring-white/70",
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
                "font-display text-sm font-semibold uppercase tracking-[0.08em]",
                theme.heading,
              ].join(" ")}
            >
              {tb("templateComponentType", section.type)}
            </h3>
          </div>
        </div>
      </div>

      <div className="pl-2">
        {canEdit && onChange ? (
          <textarea
            className="w-full resize-y rounded-xl border border-white/90 bg-white/80 px-3.5 py-3 text-sm leading-relaxed text-slate-800 shadow-inner placeholder:text-slate-400 focus:border-violet-300 focus:outline-none focus:ring-2 focus:ring-violet-300/40"
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
          <p className="rounded-xl border border-dashed border-slate-200/90 bg-white/55 px-3 py-5 text-center text-sm italic text-slate-400">
            {t("meeting.noteSectionEmpty")}
          </p>
        ) : section.kind === "paragraph" ? (
          <p className="whitespace-pre-wrap rounded-xl bg-white/80 px-4 py-3.5 text-sm leading-relaxed text-slate-800 shadow-sm ring-1 ring-white/90 sm:text-[15px] sm:leading-7">
            {parsed.paragraph}
          </p>
        ) : (
          <ol className="space-y-2.5">
            {parsed.items.map((item, i) => {
              const meta =
                section.type === "ACTIONS" ? parseActionMeta(item) : { text: item };
              return (
                <li
                  key={`${section.type}-${i}`}
                  className={[
                    "flex gap-3 rounded-xl border px-3.5 py-3 text-sm leading-relaxed text-slate-800 shadow-sm",
                    theme.item,
                  ].join(" ")}
                >
                  <span
                    className={[
                      "mt-0.5 flex h-6 w-6 shrink-0 items-center justify-center rounded-full text-[11px] font-bold shadow-sm",
                      theme.itemIndex,
                    ].join(" ")}
                  >
                    {i + 1}
                  </span>
                  <div className="min-w-0 flex-1 space-y-1.5">
                    <p className="sm:text-[15px] sm:leading-6">{meta.text}</p>
                    {"owner" in meta && (meta.owner || meta.due) ? (
                      <div className="flex flex-wrap gap-1.5">
                        {meta.owner ? (
                          <span className="rounded-full bg-amber-100/95 px-2.5 py-0.5 text-[11px] font-semibold text-amber-950">
                            {t("meeting.minutesOwner")}: {meta.owner}
                          </span>
                        ) : null}
                        {meta.due ? (
                          <span className="rounded-full bg-slate-100/95 px-2.5 py-0.5 text-[11px] font-semibold text-slate-700">
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
      </div>
    </section>
  );
}

function editableValue(section: MinutesSection): string {
  if (section.kind !== "list") return section.value;
  const parsed = parseSectionContent(section.value, "list");
  if (parsed.empty) return "";
  return parsed.items.join("\n");
}
