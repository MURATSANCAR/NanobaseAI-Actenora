import { Link } from "react-router-dom";
import { Calendar, Clock, Users } from "lucide-react";
import type { MeetingSummary } from "@/api/types";
import { StatusBadge } from "@/components/qa/StatusBadge";
import { useI18n, type Locale } from "@/i18n";

function formatMeetingWhen(iso: string, locale: Locale) {
  const parsed = Date.parse(iso);
  if (Number.isNaN(parsed)) {
    return { dateLabel: "—", timeLabel: "—" };
  }
  const start = new Date(parsed);
  return {
    dateLabel: start.toLocaleDateString(locale, {
      day: "numeric",
      month: "short",
      year: "numeric",
    }),
    timeLabel: start.toLocaleTimeString(locale, {
      hour: "2-digit",
      minute: "2-digit",
    }),
  };
}

/** Newer calendar days first; within a day, earlier clock time first (AM before PM). */
export function compareMeetingsByScheduledStartDesc(a: MeetingSummary, b: MeetingSummary): number {
  const aMs = Date.parse(a.scheduledStartAt);
  const bMs = Date.parse(b.scheduledStartAt);
  const aValid = !Number.isNaN(aMs);
  const bValid = !Number.isNaN(bMs);
  if (!aValid && !bValid) return b.id.localeCompare(a.id);
  if (!aValid) return 1;
  if (!bValid) return -1;

  const aDate = new Date(aMs);
  const bDate = new Date(bMs);
  const aDay = Date.UTC(aDate.getFullYear(), aDate.getMonth(), aDate.getDate());
  const bDay = Date.UTC(bDate.getFullYear(), bDate.getMonth(), bDate.getDate());
  if (aDay !== bDay) return bDay - aDay;
  return aMs - bMs;
}

/** Numbered card list used on dashboard "Recent meetings" and Meetings page. */
export function MeetingSummaryList({
  meetings,
  ariaLabel,
  empty,
  indexOffset = 0,
}: {
  meetings: MeetingSummary[];
  ariaLabel: string;
  empty?: string;
  /** 0-based offset when paginating so indices continue across pages. */
  indexOffset?: number;
}) {
  const { t, tb, locale } = useI18n();
  const ordered = [...meetings].sort(compareMeetingsByScheduledStartDesc);

  if (ordered.length === 0) {
    return empty ? (
      <p className="rounded-2xl border border-dashed border-teal-200/80 bg-teal-50/40 px-4 py-8 text-center text-sm text-slate-600">
        {empty}
      </p>
    ) : null;
  }

  return (
    <ul className="dashboard-meeting-list" aria-label={ariaLabel}>
      {ordered.map((m, index) => {
        const { dateLabel, timeLabel } = formatMeetingWhen(m.scheduledStartAt, locale);
        const displayIndex = indexOffset + index + 1;
        return (
          <li key={m.id} style={{ animationDelay: `${120 + index * 50}ms` }}>
            <Link to={`/meetings/${m.id}`} className="dashboard-meeting-row">
              <span className="dashboard-meeting-index" aria-hidden>
                {String(displayIndex).padStart(2, "0")}
              </span>
              <div className="min-w-0 flex-1">
                <div className="flex flex-wrap items-start justify-between gap-2">
                  <p className="min-w-0 flex-1 truncate font-semibold text-slate-900">{m.title}</p>
                  <StatusBadge label={tb("meetingStatus", m.status)} status={m.status} />
                </div>
                <div className="mt-2.5 flex flex-wrap items-center gap-2">
                  <span className="dashboard-meeting-meta dashboard-meeting-meta--date">
                    <Calendar className="h-3.5 w-3.5" aria-hidden />
                    <span>{dateLabel}</span>
                  </span>
                  <span className="dashboard-meeting-meta dashboard-meeting-meta--time">
                    <Clock className="h-3.5 w-3.5" aria-hidden />
                    <span>{timeLabel}</span>
                  </span>
                  <span className="dashboard-meeting-meta dashboard-meeting-meta--people">
                    <Users className="h-3.5 w-3.5" aria-hidden />
                    <span>
                      {m.participantCount} {t("common.people")}
                    </span>
                  </span>
                </div>
              </div>
            </Link>
          </li>
        );
      })}
    </ul>
  );
}
