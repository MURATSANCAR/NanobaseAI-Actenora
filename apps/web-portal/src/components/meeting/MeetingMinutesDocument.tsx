import { FileText, Sparkles } from "lucide-react";
import { useEffect, useMemo, useState, type ReactNode } from "react";
import type { Participant } from "@/api/types";
import { HighlightPersonNames } from "@/components/meeting/HighlightPersonNames";
import { MinutesAttendanceBanner } from "@/components/meeting/MinutesAttendanceBanner";
import { classifyAttendance } from "@/components/meeting/ParticipantAttendanceRow";
import { TemplateBrandFooter } from "@/components/template/TemplateBrandBanner";
import { PRODUCT_BRAND } from "@/config/brand";
import { useI18n } from "@/i18n";
import type { MessageKey } from "@/i18n";
import { sanitizeProductCopy } from "@/lib/brandSanitize";
import {
  enhanceParagraphReadability,
  hasSubsumedProposalDrop,
  parseActionMeta,
  parseCorporateItemId,
  parseSectionContent,
  displayMinutesDue,
  reviewBannerReasons,
  shouldOmitEmptyMinutesSection,
  type MinutesDocument,
  type MinutesSection,
  type MinutesSectionType,
} from "@/lib/minutesDocument";
import { minutesSectionTheme } from "@/lib/minutesSectionTheme";
import { MINUTES_SECTION_TITLE_KEYS } from "@/lib/minutesSectionTitle";
import { collectPersonNames } from "@/lib/personNames";
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
  qualityFlags = [],
  showAdminQualityInfo = false,
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
  qualityFlags?: string[];
  showAdminQualityInfo?: boolean;
}) {
  const { t } = useI18n();
  const filledCount = document.sections.filter((section) => {
    const parsed = parseSectionContent(section.value, section.kind);
    return !parsed.empty;
  }).length;

  const personNames = useMemo(() => {
    const sources: Array<string | null | undefined> = participants.map((p) => p.displayName);
    for (const section of document.sections) {
      if (section.kind !== "list") continue;
      for (const item of parseSectionContent(section.value, "list").items) {
        sources.push(parseActionMeta(item).owner);
      }
    }
    return collectPersonNames(sources);
  }, [participants, document.sections]);

  const attendanceCounts = useMemo(() => {
    let attended = 0;
    let absent = 0;
    for (const p of participants) {
      const bucket = classifyAttendance(p.attendanceStatus, meetingStatus, p.participantType);
      if (bucket === "attended") attended += 1;
      else if (bucket === "absent") absent += 1;
    }
    return { attended, absent };
  }, [participants, meetingStatus]);

  const reviewReasons = useMemo(() => reviewBannerReasons(qualityFlags), [qualityFlags]);
  const showDropInfo = showAdminQualityInfo && hasSubsumedProposalDrop(qualityFlags);

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
              </p>
              <p className="mt-3 inline-flex items-center gap-2 text-[11px] font-bold uppercase tracking-[0.22em] text-white/75">
                <FileText className="h-3.5 w-3.5" aria-hidden />
                {t("meeting.minutesDocumentTitle")}
              </p>
              <h2 className="meeting-note-title">
                {document.title.trim() || t("meeting.minutesUntitled")}
              </h2>
            </div>

            <div className="flex flex-col items-end gap-2">
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

      {reviewReasons.length ? (
        <div
          className="relative z-10 border-b border-amber-200/80 bg-amber-50/95 px-4 py-3 sm:px-6"
          role="status"
        >
          <p className="text-sm font-semibold text-amber-950">{t("meeting.minutesReviewBannerTitle")}</p>
          <ul className="mt-1.5 list-disc space-y-1 pl-5 text-sm text-amber-900">
            {reviewReasons.map((code) => (
              <li key={code}>{t(`backend.qualityFlag.${code}` as MessageKey)}</li>
            ))}
          </ul>
        </div>
      ) : null}

      {showDropInfo ? (
        <div className="relative z-10 border-b border-slate-200/80 bg-slate-50/90 px-4 py-2 sm:px-6">
          <p className="text-xs text-slate-600">{t("meeting.minutesAdminDropInfo")}</p>
        </div>
      ) : null}

      {participants.length ? (
        <div className="relative z-10 border-b border-violet-100/70 bg-gradient-to-b from-violet-50/50 via-white to-white px-4 py-4 sm:px-6">
          <MinutesAttendanceBanner participants={participants} meetingStatus={meetingStatus} />
        </div>
      ) : null}

      <div className="meeting-note-body">
        {document.sections
          .filter((section) => {
            const parsed = parseSectionContent(section.value, section.kind);
            return !(parsed.empty && shouldOmitEmptyMinutesSection(section.type));
          })
          .map((section, index) => (
          <MinutesSectionCard
            key={section.type}
            section={section}
            index={index + 1}
            canEdit={Boolean(canEdit) && isEditableTemplateSection(section.type)}
            personNames={personNames}
            onChange={
              onSectionChange && isEditableTemplateSection(section.type)
                ? (value) => onSectionChange(section.type as TemplateComponentType, value)
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

function isEditableTemplateSection(type: MinutesSectionType): type is TemplateComponentType {
  return (
    type !== "MEETING_KUNYE" &&
    type !== "PROPOSALS" &&
    type !== "ISSUES" &&
    type !== "NEXT_CHECKPOINT"
  );
}

function MinutesSectionCard({
  section,
  index,
  canEdit,
  personNames,
  onChange,
}: {
  section: MinutesSection;
  index: number;
  canEdit: boolean;
  personNames: string[];
  onChange?: (value: string) => void;
}) {
  const { t } = useI18n();
  const theme = minutesSectionTheme(section.type);
  const Icon = theme.icon;
  const parsed = parseSectionContent(section.value, section.kind);
  const remoteValue = editableValue(section);
  const [focused, setFocused] = useState(false);
  const [draft, setDraft] = useState(remoteValue);
  const titleKey = MINUTES_SECTION_TITLE_KEYS[section.type];

  useEffect(() => {
    if (!focused) setDraft(remoteValue);
  }, [remoteValue, focused]);

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
              {titleKey ? t(titleKey) : section.type}
            </h3>
          </div>
        </div>
      </div>

      <div className="pl-2">
        {canEdit && onChange ? (
          <textarea
            className="w-full resize-y rounded-xl border border-white/90 bg-white/80 px-3.5 py-3 text-sm leading-relaxed text-slate-800 shadow-inner placeholder:text-slate-400 focus:border-violet-300 focus:outline-none focus:ring-2 focus:ring-violet-300/40 whitespace-pre-wrap break-words"
            value={draft}
            onFocus={() => setFocused(true)}
            onBlur={() => {
              setFocused(false);
              onChange(draft);
            }}
            onChange={(e) => {
              const next = e.target.value;
              setDraft(next);
              onChange(next);
            }}
            onKeyDown={(e) => {
              if (e.key === "Enter") e.stopPropagation();
            }}
            placeholder={
              section.kind === "list"
                ? t("meeting.minutesListPlaceholder")
                : t("templates.note.sectionPlaceholder")
            }
            rows={
              section.kind === "paragraph"
                ? Math.max(3, Math.min(12, draft.split("\n").length + 1))
                : Math.max(3, Math.min(12, draft.split("\n").length + 2))
            }
          />
        ) : parsed.empty ? (
          <p className="rounded-xl border border-dashed border-slate-200/90 bg-white/55 px-3 py-5 text-center text-sm italic text-slate-400">
            {t("meeting.noteSectionEmpty")}
          </p>
        ) : section.kind === "paragraph" ? (
          <p className="whitespace-pre-wrap break-words rounded-xl bg-white/80 px-4 py-3.5 text-sm leading-relaxed text-slate-800 shadow-sm ring-1 ring-white/90 sm:text-[15px] sm:leading-7">
            <HighlightPersonNames text={parsed.paragraph} names={personNames} />
          </p>
        ) : section.type === "ACTIONS" ? (
          <ActionTable items={parsed.items} personNames={personNames} themeItem={theme.item} />
        ) : (
          <ol className="space-y-2.5">
            {parsed.items.map((item, i) => {
              const corp = parseCorporateItemId(item);
              const mitigationSplit = splitMitigation(corp.text);
              return (
                <li
                  key={`${section.type}-${i}`}
                  className={[
                    "flex items-start gap-3 rounded-xl border px-3.5 py-3 text-sm leading-relaxed text-slate-800 shadow-sm",
                    theme.item,
                  ].join(" ")}
                >
                  <span
                    className={[
                      "mt-0.5 flex h-6 min-w-6 shrink-0 items-center justify-center rounded-full text-[11px] font-bold tabular-nums shadow-sm",
                      theme.itemIndex,
                    ].join(" ")}
                    aria-hidden
                  >
                    {corp.id ?? i + 1}
                  </span>
                  <div className="min-w-0 flex-1 space-y-1">
                    <p className="break-words [overflow-wrap:anywhere] sm:text-[15px] sm:leading-6">
                      <HighlightPersonNames text={mitigationSplit.body} names={personNames} />
                    </p>
                    {mitigationSplit.mitigation ? (
                      <p className="text-xs text-slate-600">
                        <span className="font-semibold">{t("meeting.minutesMitigation")}: </span>
                        <HighlightPersonNames text={mitigationSplit.mitigation} names={personNames} />
                      </p>
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

function ActionTable({
  items,
  personNames,
  themeItem,
}: {
  items: string[];
  personNames: string[];
  themeItem: string;
}) {
  const { t } = useI18n();
  return (
    <div className="overflow-x-auto rounded-xl border border-amber-100/90 bg-white/85 shadow-sm">
      <table className="min-w-full text-left text-sm">
        <thead className="bg-amber-50/90 text-[11px] font-bold uppercase tracking-wide text-amber-950">
          <tr>
            <th className="px-3 py-2">{t("meeting.minutesActionColId")}</th>
            <th className="px-3 py-2">{t("meeting.minutesActionColOwner")}</th>
            <th className="px-3 py-2">{t("meeting.minutesActionColWork")}</th>
            <th className="px-3 py-2">{t("meeting.minutesActionColDue")}</th>
          </tr>
        </thead>
        <tbody>
          {items.map((item, i) => {
            const meta = parseActionMeta(item);
            return (
              <tr key={`action-${i}`} className={["border-t border-amber-100/80", themeItem].join(" ")}>
                <td className="px-3 py-2.5 font-semibold tabular-nums text-amber-900">
                  {meta.id ?? `A-${String(i + 1).padStart(2, "0")}`}
                </td>
                <td className="px-3 py-2.5 text-slate-800">
                  {meta.owner ? (
                    <HighlightPersonNames text={meta.owner} names={personNames} />
                  ) : (
                    "—"
                  )}
                </td>
                <td className="px-3 py-2.5 text-slate-800">
                  <HighlightPersonNames text={meta.text} names={personNames} />
                </td>
                <td className="px-3 py-2.5 text-slate-700">
                  {displayMinutesDue(meta.due, t("meeting.minutesDueUnspecified"))}
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}

function splitMitigation(text: string): { body: string; mitigation?: string } {
  const m = /\s+Azaltma:\s*(.+)$/iu.exec(text);
  if (!m) return { body: text };
  return {
    body: text.slice(0, m.index).trim(),
    mitigation: (m[1] ?? "").trim(),
  };
}

function editableValue(section: MinutesSection): string {
  if (section.kind !== "list") return enhanceParagraphReadability(section.value);
  const parsed = parseSectionContent(section.value, "list");
  if (parsed.empty) return "";
  return parsed.items.map((item, i) => `${i + 1}. ${item}`).join("\n");
}
