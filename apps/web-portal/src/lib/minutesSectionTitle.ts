import type { MessageKey } from "@/i18n";
import type { MinutesSectionType } from "@/lib/minutesDocument";

/**
 * Canonical heading keys for every minutes section, shared by the on-screen
 * minutes document and the A4 PDF preview so both render identical titles.
 *
 * Covers presentation-only sections (MEETING_KUNYE, PROPOSALS, ISSUES,
 * NEXT_CHECKPOINT) that have no `backend.templateComponentType.*` entry — those
 * previously fell through to the raw section key (e.g. "NEXT_CHECKPOINT").
 */
export const MINUTES_SECTION_TITLE_KEYS: Partial<Record<MinutesSectionType, MessageKey>> = {
  MEETING_KUNYE: "meeting.minutesSection.MEETING_KUNYE",
  EXECUTIVE_SUMMARY: "backend.templateComponentType.EXECUTIVE_SUMMARY",
  AGENDA: "meeting.minutesSection.AGENDA",
  DECISIONS: "backend.templateComponentType.DECISIONS",
  ACTIONS: "meeting.minutesSection.ACTIONS",
  RISKS: "meeting.minutesSection.RISKS",
  COMMITMENTS: "backend.templateComponentType.COMMITMENTS",
  OPEN_QUESTIONS: "backend.templateComponentType.OPEN_QUESTIONS",
  ISSUES: "meeting.minutesSection.ISSUES",
  PROPOSALS: "meeting.minutesSection.PROPOSALS",
  NEXT_CHECKPOINT: "meeting.minutesSection.NEXT_CHECKPOINT",
};

/** Localized section heading; falls back to the raw type only for unknown sections. */
export function minutesSectionTitle(
  t: (key: MessageKey) => string,
  type: MinutesSectionType,
): string {
  const key = MINUTES_SECTION_TITLE_KEYS[type];
  return key ? t(key) : type;
}
